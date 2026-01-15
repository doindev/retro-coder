package org.me.retrocoder.websocket;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.agent.ClaudeClient;
import org.me.retrocoder.agent.ClaudeClientFactory;
import org.me.retrocoder.model.dto.FeatureCreateDTO;
import org.me.retrocoder.service.FeatureService;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages a project expansion conversation.
 *
 * Unlike SpecChatSession which writes spec files, this session:
 * 1. Reads existing app_spec.txt for context
 * 2. Parses feature definitions from Claude's output
 * 3. Creates features via FeatureService
 * 4. Tracks which features were created during the session
 */
@Slf4j
public class ExpandChatSession {

    private static final Pattern FEATURES_PATTERN = Pattern.compile(
            "<features_to_create>\\s*(\\[[\\s\\S]*?\\])\\s*</features_to_create>",
            Pattern.MULTILINE
    );

    @Getter
    private final String projectName;

    @Getter
    private final String projectPath;

    private final ClaudeClientFactory clientFactory;
    private final FeatureService featureService;
    private final ObjectMapper objectMapper;

    private ClaudeClient client;

    @Getter
    private final List<Map<String, Object>> messages = new CopyOnWriteArrayList<>();

    @Getter
    private boolean complete = false;

    @Getter
    private final Instant createdAt = Instant.now();

    @Getter
    private int featuresCreated = 0;

    @Getter
    private final List<Long> createdFeatureIds = new ArrayList<>();

    @SuppressWarnings("unused")
	private WebSocketSession currentSession;

    public ExpandChatSession(String projectName, String projectPath,
                             ClaudeClientFactory clientFactory, FeatureService featureService) {
        this.projectName = projectName;
        this.projectPath = projectPath;
        this.clientFactory = clientFactory;
        this.featureService = featureService;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Start the expand project session.
     */
    public void start(WebSocketSession session) throws IOException {
        this.currentSession = session;

        // Load the expand-project skill
        String skillContent = loadExpandProjectSkill();
        if (skillContent == null) {
            sendMessage(session, "error", "Expand project skill not found");
            return;
        }

        // Verify project has existing spec
        Path specPath = Paths.get(projectPath, "prompts", "app_spec.txt");
        if (!Files.exists(specPath)) {
            sendMessage(session, "error", "Project has no app_spec.txt. Please create it first using spec creation.");
            return;
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

        // Start the conversation
        sendMessage(session, "text", "Analyzing your project for expansion...");

        // Send the initial prompt to Claude
        queryClaudeAndStream(session, "Begin the project expansion process.", systemPrompt);
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
     * Mark expansion as done.
     */
    public void markComplete(WebSocketSession session) throws IOException {
        complete = true;
        sendRawMessage(session, Map.of(
                "type", "expansion_complete",
                "total_added", featuresCreated
        ));
    }

    /**
     * Query Claude and stream the response to the WebSocket.
     */
    private void queryClaudeAndStream(WebSocketSession session, String message, String systemPrompt) {
        try {
            // Build the full prompt with system context if provided
            String fullPrompt = systemPrompt != null
                    ? systemPrompt + "\n\n---\n\nUser: " + message
                    : message;

            // Send to Claude and stream response
            StringBuilder responseBuffer = new StringBuilder();

            String response = client.sendPrompt(fullPrompt, projectPath, chunk -> {
                // Stream each chunk to the WebSocket
                try {
                    responseBuffer.append(chunk);
                    sendMessage(session, "text", chunk);
                } catch (IOException e) {
                    log.error("Failed to send chunk to WebSocket", e);
                }
            });

            // Store the assistant message
            Map<String, Object> assistantMessage = new HashMap<>();
            assistantMessage.put("role", "assistant");
            assistantMessage.put("content", response);
            assistantMessage.put("timestamp", Instant.now().toString());
            messages.add(assistantMessage);

            // Check for feature creation blocks in the response
            checkForFeatureCreation(session, response);

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
     * Check for feature creation blocks in Claude's response.
     */
    private void checkForFeatureCreation(WebSocketSession session, String response) throws IOException {
        Matcher matcher = FEATURES_PATTERN.matcher(response);

        // Collect all features from all blocks, deduplicating by name
        List<Map<String, Object>> allFeatures = new ArrayList<>();
        Set<String> seenNames = new HashSet<>();

        while (matcher.find()) {
            String featuresJson = matcher.group(1);
            try {
                List<Map<String, Object>> featuresData = objectMapper.readValue(
                        featuresJson,
                        new TypeReference<List<Map<String, Object>>>() {}
                );

                if (featuresData != null) {
                    for (Map<String, Object> feature : featuresData) {
                        String name = (String) feature.get("name");
                        if (name != null && !seenNames.contains(name)) {
                            seenNames.add(name);
                            allFeatures.add(feature);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to parse features JSON block: {}", e.getMessage());
                // Continue processing other blocks
            }
        }

        if (!allFeatures.isEmpty()) {
            try {
                List<Map<String, Object>> created = createFeaturesBulk(allFeatures);

                if (!created.isEmpty()) {
                    featuresCreated += created.size();
                    for (Map<String, Object> f : created) {
                        createdFeatureIds.add((Long) f.get("id"));
                    }

                    sendRawMessage(session, Map.of(
                            "type", "features_created",
                            "count", created.size(),
                            "features", created
                    ));

                    log.info("Created {} features for {}", created.size(), projectName);
                }
            } catch (Exception e) {
                log.error("Failed to create features", e);
                sendMessage(session, "error", "Failed to create features: " + e.getMessage());
            }
        }
    }

    /**
     * Create features in bulk using the FeatureService.
     */
    private List<Map<String, Object>> createFeaturesBulk(List<Map<String, Object>> features) {
        List<Map<String, Object>> createdFeatures = new ArrayList<>();

        for (Map<String, Object> f : features) {
            try {
                FeatureCreateDTO dto = new FeatureCreateDTO();
                dto.setCategory((String) f.getOrDefault("category", "functional"));
                dto.setName((String) f.getOrDefault("name", "Unnamed feature"));
                dto.setDescription((String) f.getOrDefault("description", ""));

                // Handle steps (could be List<String> or needs conversion)
                Object stepsObj = f.get("steps");
                if (stepsObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<String> steps = (List<String>) stepsObj;
                    dto.setSteps(steps);
                }

                var created = featureService.createFeature(projectName, dto);

                Map<String, Object> result = new HashMap<>();
                result.put("id", created.getId());
                result.put("name", created.getName());
                result.put("category", created.getCategory());
                createdFeatures.add(result);

            } catch (Exception e) {
                log.warn("Failed to create feature: {}", f.get("name"), e);
            }
        }

        return createdFeatures;
    }

    /**
     * Load the expand-project skill from resources.
     */
    private String loadExpandProjectSkill() {
        // Try to load from the retrocoder-master project's .claude/commands directory
        Path skillPath = Paths.get(System.getProperty("user.dir"), "..", "retrocoder-master",
                ".claude", "commands", "expand-project.md");

        if (Files.exists(skillPath)) {
            try {
                return Files.readString(skillPath, StandardCharsets.UTF_8);
            } catch (IOException e) {
                log.warn("Failed to read expand-project.md from file system", e);
            }
        }

        // Try classpath
        try {
            var resource = getClass().getClassLoader().getResourceAsStream("commands/expand-project.md");
            if (resource != null) {
                return new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            log.warn("Failed to read expand-project.md from classpath", e);
        }

        // Return a default skill prompt
        return getDefaultExpandProjectPrompt();
    }

    /**
     * Get default expand project prompt if the skill file is not found.
     */
    private String getDefaultExpandProjectPrompt() {
        return """
            # GOAL

            Help the user add new features to an existing project.

            # YOUR ROLE

            You are the Project Expansion Assistant - an expert at understanding existing projects and adding new capabilities.

            # STEPS

            1. Read the existing project specification at $ARGUMENTS/prompts/app_spec.txt
            2. Summarize what exists for the user
            3. Ask what NEW features they want to add
            4. Create features in this JSON format wrapped in <features_to_create> tags:

            ```json
            <features_to_create>
            [
              {
                "category": "functional",
                "name": "Feature name",
                "description": "What this feature does",
                "steps": ["Step 1", "Step 2", "Step 3"]
              }
            ]
            </features_to_create>
            ```

            # BEGIN

            Start by reading the app specification and greeting the user.
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
