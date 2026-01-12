package org.me.retrocoder.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.agent.ClaudeClient;
import org.me.retrocoder.agent.ClaudeClientFactory;
import org.me.retrocoder.model.Conversation;
import org.me.retrocoder.service.ConversationService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.Map;

/**
 * Manages a read-only assistant conversation for a project.
 *
 * The assistant can:
 * - Read and analyze source code files
 * - Search for patterns in the codebase
 * - Check feature progress and status
 * - Create new features in the backlog
 * - Skip features to deprioritize them
 *
 * The assistant CANNOT:
 * - Modify, create, or delete source code files
 * - Mark features as passing
 * - Run bash commands
 */
@Slf4j
public class AssistantChatSession {

    @Getter
    private final String projectName;

    @Getter
    private final String projectPath;

    @Getter
    private Long conversationId;

    private final ClaudeClientFactory clientFactory;
    private final ConversationService conversationService;
    private final ObjectMapper objectMapper;

    private ClaudeClient client;

    @Getter
    private final Instant createdAt = Instant.now();

    public AssistantChatSession(String projectName, String projectPath,
                                 Long conversationId,
                                 ClaudeClientFactory clientFactory,
                                 ConversationService conversationService) {
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.conversationId = conversationId;
        this.clientFactory = clientFactory;
        this.conversationService = conversationService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Start the assistant session.
     */
    public void start(WebSocketSession session) throws IOException {
        // Create new conversation if we don't have one
        if (conversationId == null) {
            Conversation conv = conversationService.createConversation(projectName);
            conversationId = conv.getId();
            sendRawMessage(session, Map.of(
                    "type", "conversation_created",
                    "conversation_id", conversationId
            ));
        }

        // Create Claude client
        try {
            client = clientFactory.createDefaultClient();
        } catch (Exception e) {
            log.error("Failed to create Claude client", e);
            sendMessage(session, "error", "Failed to initialize assistant: " + e.getMessage());
            return;
        }

        // Generate system prompt with project context
        @SuppressWarnings("unused")
		String systemPrompt = generateSystemPrompt();

        // Send initial greeting
        String greeting = String.format(
                "Hello! I'm your project assistant for **%s**. I can help you understand the codebase, " +
                "explain features, and answer questions about the project. What would you like to know?",
                projectName
        );

        // Store greeting in database
        conversationService.addMessage(projectName, conversationId, "assistant", greeting);

        sendMessage(session, "text", greeting);
        sendRawMessage(session, Map.of("type", "response_done"));
    }

    /**
     * Send a user message and stream Claude's response.
     */
    public void sendUserMessage(WebSocketSession session, String content) throws IOException {
        if (client == null) {
            sendMessage(session, "error", "Session not initialized. Send 'start' first.");
            return;
        }

        if (content == null || content.trim().isEmpty()) {
            sendMessage(session, "error", "Empty message");
            return;
        }

        if (conversationId == null) {
            sendMessage(session, "error", "No conversation ID set.");
            return;
        }

        // Store user message
        conversationService.addMessage(projectName, conversationId, "user", content);

        // Query Claude and stream response
        queryClaudeAndStream(session, content);
    }

    /**
     * Query Claude and stream the response to the WebSocket.
     */
	private void queryClaudeAndStream(WebSocketSession session, String message) {
        try {
            // Build the full prompt with system context
            String systemPrompt = generateSystemPrompt();
            String fullPrompt = systemPrompt + "\n\n---\n\nUser: " + message;

            // Send to Claude and stream response
            StringBuilder responseBuffer = new StringBuilder();

            @SuppressWarnings("unused")
            String response = client.sendPrompt(fullPrompt, projectPath, chunk -> {
                try {
                    responseBuffer.append(chunk);
                    sendMessage(session, "text", chunk);
                } catch (IOException e) {
                    log.error("Failed to send chunk to WebSocket", e);
                }
            });

            // Store assistant response in database
            if (!responseBuffer.isEmpty()) {
                conversationService.addMessage(projectName, conversationId, "assistant", responseBuffer.toString());
            }

            // Signal response is done
            sendRawMessage(session, Map.of("type", "response_done"));

        } catch (Exception e) {
            log.error("Error querying Claude", e);
            try {
                sendMessage(session, "error", "Error: " + e.getMessage());
            } catch (IOException ex) {
                log.error("Failed to send error message", ex);
            }
        }
    }

    /**
     * Generate the system prompt with project context.
     */
    private String generateSystemPrompt() {
        // Try to load app_spec.txt for context
        String appSpecContent = "";
        Path appSpecPath = Paths.get(projectPath, "prompts", "app_spec.txt");
        if (Files.exists(appSpecPath)) {
            try {
                appSpecContent = Files.readString(appSpecPath, StandardCharsets.UTF_8);
                // Truncate if too long
                if (appSpecContent.length() > 5000) {
                    appSpecContent = appSpecContent.substring(0, 5000) + "\n... (truncated)";
                }
            } catch (IOException e) {
                log.warn("Failed to read app_spec.txt", e);
            }
        }

        return String.format("""
                You are a helpful project assistant and backlog manager for the "%s" project.

                Your role is to help users understand the codebase, answer questions about features, and manage the project backlog. You can READ files and CREATE/MANAGE features, but you cannot modify source code.

                ## What You CAN Do

                **Codebase Analysis (Read-Only):**
                - Read and analyze source code files
                - Search for patterns in the codebase
                - Look up documentation online
                - Check feature progress and status

                **Feature Management:**
                - Create new features/test cases in the backlog
                - Skip features to deprioritize them (move to end of queue)
                - View feature statistics and progress

                ## What You CANNOT Do

                - Modify, create, or delete source code files
                - Mark features as passing (that requires actual implementation by the coding agent)
                - Run bash commands or execute code

                If the user asks you to modify code, explain that you're a project assistant and they should use the main coding agent for implementation.

                ## Project Specification

                %s

                ## Guidelines

                1. Be concise and helpful
                2. When explaining code, reference specific file paths and line numbers
                3. Search the codebase to find relevant information before answering
                4. When creating features, confirm what was created
                5. If you're unsure about details, ask for clarification
                """,
                projectName,
                appSpecContent.isEmpty() ? "(No app specification found)" : appSpecContent
        );
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
}
