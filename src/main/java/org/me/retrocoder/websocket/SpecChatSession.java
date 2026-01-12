package org.me.retrocoder.websocket;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import org.me.retrocoder.agent.ClaudeClient;
import org.me.retrocoder.agent.ClaudeClientFactory;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages a spec creation conversation for one project.
 *
 * Uses the create-spec skill to guide users through:
 * - Phase 1: Project Overview (name, description, audience)
 * - Phase 2: Involvement Level (Quick vs Detailed mode)
 * - Phase 3: Technology Preferences
 * - Phase 4: Features (main exploration phase)
 * - Phase 5: Technical Details (derived or discussed)
 * - Phase 6-7: Success Criteria & Approval
 */
@Slf4j
public class SpecChatSession {

    @Getter
    private final String projectName;

    @Getter
    private final String projectPath;

    private final ClaudeClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    private ClaudeClient client;

    @Getter
    private final List<Map<String, Object>> messages = new CopyOnWriteArrayList<>();

    @Getter
    private boolean complete = false;

    @Getter
    private final Instant createdAt = Instant.now();

    @SuppressWarnings("unused")
	private WebSocketSession currentSession;

    // Track which files have been written
    private boolean appSpecWritten = false;
    private boolean initializerPromptWritten = false;
    private String specPath = null;

    // Store the system prompt for conversation continuity
    private String storedSystemPrompt = null;

    public SpecChatSession(String projectName, String projectPath, ClaudeClientFactory clientFactory) {
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.clientFactory = clientFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Start the spec creation session.
     */
    public void start(WebSocketSession session) throws IOException {
        this.currentSession = session;

        // Load the create-spec skill
        String skillContent = loadCreateSpecSkill();
        if (skillContent == null) {
            sendMessage(session, "error", "Spec creation skill not found");
            return;
        }

        // Ensure project directory and prompts directory exist
        Path projectDir = Paths.get(projectPath);
        Path promptsDir = projectDir.resolve("prompts");
        try {
            Files.createDirectories(promptsDir);
        } catch (IOException e) {
            log.error("Failed to create prompts directory", e);
            sendMessage(session, "error", "Failed to create project directory");
            return;
        }

        // Delete existing app_spec.txt so Claude can create it fresh
        Path appSpecPath = promptsDir.resolve("app_spec.txt");
        try {
            Files.deleteIfExists(appSpecPath);
            log.info("Deleted existing app_spec.txt for fresh spec creation");
        } catch (IOException e) {
            log.warn("Failed to delete existing app_spec.txt", e);
        }

        // Create Claude client
        try {
            client = clientFactory.createDefaultClient();
        } catch (Exception e) {
            log.error("Failed to create Claude client", e);
            sendMessage(session, "error", "Failed to initialize Claude: " + e.getMessage());
            return;
        }

        // Replace $ARGUMENTS with absolute project path
        String systemPrompt = skillContent.replace("$ARGUMENTS", projectPath);

        // Store the system prompt for conversation continuity
        this.storedSystemPrompt = systemPrompt;

        // Start the conversation
        sendMessage(session, "text", "Starting spec creation...");

        // Send the initial prompt to Claude
        queryClaudeAndStream(session, "Begin the spec creation process.", systemPrompt);
    }

    /**
     * Send a user message and stream Claude's response.
     */
    public void sendUserMessage(WebSocketSession session, String content) throws IOException {
        sendUserMessage(session, content, null);
    }

    /**
     * Send a user message with optional attachments and stream Claude's response.
     */
    public void sendUserMessage(WebSocketSession session, String content, List<Map<String, Object>> attachments) throws IOException {
        this.currentSession = session;

        if (client == null) {
            sendMessage(session, "error", "Session not initialized. Send 'start' first.");
            return;
        }

        if ((content == null || content.trim().isEmpty()) && (attachments == null || attachments.isEmpty())) {
            sendMessage(session, "error", "Empty message");
            return;
        }

        // Store the user message
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", content);
        userMessage.put("has_attachments", attachments != null && !attachments.isEmpty());
        userMessage.put("timestamp", Instant.now().toString());
        messages.add(userMessage);

        // Query Claude and stream response
        queryClaudeAndStream(session, content, null);
    }

    /**
     * Handle structured answer from user.
     */
    public void handleAnswer(WebSocketSession session, Map<String, Object> answers) throws IOException {
        // Convert structured answers to a message
        StringBuilder responseBuilder = new StringBuilder();
        for (Map.Entry<String, Object> entry : answers.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof List) {
                responseBuilder.append(String.join(", ", ((List<?>) value).stream()
                        .map(Object::toString).toList()));
            } else {
                responseBuilder.append(value.toString());
            }
            responseBuilder.append("; ");
        }

        String userResponse = responseBuilder.length() > 0
                ? responseBuilder.substring(0, responseBuilder.length() - 2)
                : "OK";

        sendUserMessage(session, userResponse);
    }

    /**
     * Query Claude and stream the response to the WebSocket.
     */
    private void queryClaudeAndStream(WebSocketSession session, String message, String systemPrompt) {
        try {
            // Use stored system prompt if none provided
            String effectiveSystemPrompt = systemPrompt != null ? systemPrompt : storedSystemPrompt;

            // Build conversation history for context
            StringBuilder conversationHistory = new StringBuilder();

            // Include system prompt at the start
            if (effectiveSystemPrompt != null) {
                conversationHistory.append(effectiveSystemPrompt);
                conversationHistory.append("\n\n---\n\n");
            }

            // Include all previous messages for conversation continuity
            for (Map<String, Object> msg : messages) {
                String role = (String) msg.get("role");
                String content = (String) msg.get("content");
                if (role != null && content != null) {
                    conversationHistory.append(role.equals("user") ? "User: " : "Assistant: ");
                    conversationHistory.append(content);
                    conversationHistory.append("\n\n");
                }
            }

            // Add the current user message
            conversationHistory.append("User: ").append(message);

            String fullPrompt = conversationHistory.toString();

            // Send to Claude and stream response
            StringBuilder responseBuffer = new StringBuilder();

            // Log prompt size for debugging
            log.info("Sending prompt to Claude ({} chars)", fullPrompt.length());

            // Warn if prompt is very large
            if (fullPrompt.length() > 50000) {
                log.warn("Large prompt detected - response may take longer");
            }

            String response = client.sendPrompt(fullPrompt, projectPath, chunk -> {
                // Stream each chunk to the WebSocket
                try {
                    // Add newline since CLI wrapper strips them from readLine()
                    String chunkWithNewline = chunk + "\n";
                    responseBuffer.append(chunkWithNewline);
                    sendMessage(session, "text", chunkWithNewline);

                    // Check for file writes in the chunk
                    checkForFileWrites(session, chunk);
                } catch (IOException e) {
                    log.error("Failed to send chunk to WebSocket", e);
                }
            });

            log.info("Claude response received ({} chars)", response.length());

            // Store the assistant message
            Map<String, Object> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", response);
            assistantMessage.put("timestamp", Instant.now().toString());
            messages.add(assistantMessage);

            // Check if both required files have been written
            checkSpecCompletion(session);

            // Signal response is done
            sendRawMessage(session, Map.of("type", "response_done"));

        } catch (Exception e) {
            log.error("Error querying Claude", e);
            try {
                sendMessage(session, "error", "Error: " + e.getMessage());
                // Signal response is done even on error so frontend stops showing "thinking"
                sendRawMessage(session, Map.of("type", "response_done"));
            } catch (IOException ex) {
                log.error("Failed to send error message", ex);
            }
        }
    }

    /**
     * Check for file write operations in Claude's response.
     */
    private void checkForFileWrites(WebSocketSession session, String chunk) throws IOException {
        // Check if app_spec.txt was written
        Path appSpecPath = Paths.get(projectPath, "prompts", "app_spec.txt");
        if (!appSpecWritten && Files.exists(appSpecPath)) {
            appSpecWritten = true;
            specPath = appSpecPath.toString();
            log.info("app_spec.txt verified at: {}", appSpecPath);
            sendRawMessage(session, Map.of(
                    "type", "file_written",
                    "path", "prompts/app_spec.txt"
            ));
        }

        // Check if initializer_prompt.md was written/updated
        Path initializerPath = Paths.get(projectPath, "prompts", "initializer_prompt.md");
        if (!initializerPromptWritten && Files.exists(initializerPath)) {
            // Check if the file has been modified (contains actual feature count, not placeholder)
            try {
                String content = Files.readString(initializerPath, StandardCharsets.UTF_8);
                if (!content.contains("[FEATURE_COUNT]")) {
                    initializerPromptWritten = true;
                    log.info("initializer_prompt.md verified at: {}", initializerPath);
                    sendRawMessage(session, Map.of(
                            "type", "file_written",
                            "path", "prompts/initializer_prompt.md"
                    ));
                }
            } catch (IOException e) {
                log.warn("Failed to read initializer_prompt.md", e);
            }
        }
    }

    /**
     * Check if spec creation is complete (both files written).
     */
    private void checkSpecCompletion(WebSocketSession session) throws IOException {
        if (appSpecWritten && initializerPromptWritten && !complete) {
            complete = true;
            log.info("Both app_spec.txt and initializer_prompt.md verified - signaling completion");
            sendRawMessage(session, Map.of(
                    "type", "spec_complete",
                    "path", specPath != null ? specPath : "prompts/app_spec.txt"
            ));
            sendRawMessage(session, Map.of(
                    "type", "complete",
                    "path", specPath != null ? specPath : "prompts/app_spec.txt"
            ));
        }
    }

    /**
     * Load the create-spec skill from resources.
     */
    private String loadCreateSpecSkill() {
        // Try to load from the current project's .claude/commands directory
        Path skillPath = Paths.get(System.getProperty("user.dir"),
                ".claude", "commands", "create-spec.md");

        log.info("Looking for create-spec.md at: {}", skillPath);

        if (Files.exists(skillPath)) {
            try {
                String content = Files.readString(skillPath, StandardCharsets.UTF_8);
                log.info("Successfully loaded create-spec.md from file system ({} chars)", content.length());
                return content;
            } catch (IOException e) {
                log.warn("Failed to read create-spec.md from file system", e);
            }
        } else {
            log.warn("create-spec.md not found at: {}", skillPath);
        }

        // Try classpath
        try {
            var resource = getClass().getClassLoader().getResourceAsStream("commands/create-spec.md");
            if (resource != null) {
                String content = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
                log.info("Successfully loaded create-spec.md from classpath ({} chars)", content.length());
                return content;
            }
        } catch (IOException e) {
            log.warn("Failed to read create-spec.md from classpath", e);
        }

        // Return a default skill prompt
        log.warn("Using default spec creation prompt - create-spec.md not found");
        return getDefaultSpecCreationPrompt();
    }

    /**
     * Get default spec creation prompt if the skill file is not found.
     */
    private String getDefaultSpecCreationPrompt() {
        return """
            # CRITICAL RULE: ONE QUESTION PER MESSAGE

            You MUST ask exactly ONE question per message. Never ask multiple questions.

            - WRONG: "1. What's the name? 2. What does it do? 3. Who uses it?"
            - RIGHT: "What do you want to call this project?"

            # GOAL

            Help the user create a comprehensive project specification for a long-running autonomous coding process.

            # YOUR ROLE

            You are the Spec Creation Assistant - an expert at translating project ideas into detailed technical specifications.

            # CONVERSATION FLOW

            Ask these questions ONE AT A TIME, waiting for a response before asking the next:

            1. First message: "What do you want to call this project?"
            2. After they respond: "In your own words, what are you building?"
            3. After they respond: "Who will use this - just you, or others too?"
            4. Continue asking about features ONE question at a time

            # BEGIN

            Your first message should ONLY be:

            "Hi! I'm here to help you create a detailed specification for your app.

            What do you want to call this project?"

            STOP. Wait for their response before asking anything else.
            """;
    }

    /**
     * Send a typed message to the WebSocket.
     */
    private void sendMessage(WebSocketSession session, String type, String content) throws IOException {
        sendRawMessage(session, Map.of("type", type, "content", content));
    }

    /**
     * Send a raw message object to the WebSocket.
     */
    private void sendRawMessage(WebSocketSession session, Map<String, Object> message) throws IOException {
        if (session.isOpen()) {
            String json = objectMapper.writeValueAsString(message);
            session.sendMessage(new TextMessage(json));
        }
    }

    /**
     * Close the session and cleanup resources.
     */
    public void close() {
        if (client != null) {
            client.close();
            client = null;
        }
    }

    /**
     * Get the number of messages in the conversation.
     */
    public int getMessageCount() {
        return messages.size();
    }
}
