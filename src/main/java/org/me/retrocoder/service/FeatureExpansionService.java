package org.me.retrocoder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.agent.ClaudeClient;
import org.me.retrocoder.agent.ClaudeClientFactory;
import org.me.retrocoder.agent.LangChain4jClientFactory;
import org.me.retrocoder.model.AgentRole;
import org.me.retrocoder.model.AiAgentConfig;
import org.me.retrocoder.model.dto.FeatureExpansionRequestDTO;
import org.me.retrocoder.model.dto.FeatureExpansionResponseDTO;
import org.me.retrocoder.websocket.WebSocketSessionManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service for expanding feature descriptions using Claude.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureExpansionService {

    private final RegistryService registryService;
    private final ClaudeClientFactory clientFactory;
    private final LangChain4jClientFactory langChain4jClientFactory;
    private final AiAgentConfigService aiAgentConfigService;
    private final WebSocketSessionManager webSocketSessionManager;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String TEMPLATE_PATH = "templates/feature_expansion_prompt.template.md";

    /**
     * Expand a feature description into a complete feature specification.
     */
    public FeatureExpansionResponseDTO expand(String projectName, FeatureExpansionRequestDTO request) {
        String projectPath = registryService.getProjectPath(projectName)
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectName));

        log.info("Starting feature expansion for project: {} - {}", projectName,
                truncate(request.getDescription(), 100));

        webSocketSessionManager.broadcastLog(projectName,
                "\n*** FEATURE EXPANSION STARTED ***");
        webSocketSessionManager.broadcastLog(projectName,
                "Expanding: " + truncate(request.getDescription(), 200));

        ClaudeClient client = null;
        try {
            // Get or create Claude client
            AiAgentConfig agentConfig = aiAgentConfigService.getAgentForRole(AgentRole.CODING, projectName);
            if (agentConfig != null) {
                client = langChain4jClientFactory.createClaudeClient(agentConfig);
            } else {
                client = clientFactory.createDefaultClient();
            }

            // Load and prepare prompt
            String prompt = loadPromptTemplate();
            prompt = prompt.replace("{{FEATURE_DESCRIPTION}}", request.getDescription());

            // Send to Claude and collect response
            StringBuilder fullResponse = new StringBuilder();
            String response = client.sendPrompt(prompt, projectPath, chunk -> {
                fullResponse.append(chunk);
                webSocketSessionManager.broadcastLog(projectName, chunk);
            });

            log.info("Feature expansion completed for project: {}", projectName);
            webSocketSessionManager.broadcastLog(projectName,
                    "\n*** FEATURE EXPANSION COMPLETE ***");

            // Parse the JSON response
            return parseResponse(response);

        } catch (Exception e) {
            log.error("Feature expansion failed for project: {}", projectName, e);
            webSocketSessionManager.broadcastLog(projectName,
                    "\n*** FEATURE EXPANSION FAILED: " + e.getMessage() + " ***");
            return FeatureExpansionResponseDTO.failure(e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    /**
     * Load the feature expansion prompt template.
     */
    private String loadPromptTemplate() throws IOException {
        ClassPathResource resource = new ClassPathResource(TEMPLATE_PATH);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Parse the Claude response to extract structured data.
     */
    private FeatureExpansionResponseDTO parseResponse(String response) {
        try {
            // Try to extract JSON from the response
            String json = extractJson(response);
            if (json == null) {
                log.warn("No JSON found in response, using fallback parsing");
                return createFallbackResponse(response);
            }

            JsonNode root = objectMapper.readTree(json);

            String name = getTextOrDefault(root, "name", "New Feature");
            String category = getTextOrDefault(root, "category", "Feature");
            String description = getTextOrDefault(root, "description", "");
            List<String> steps = getStringList(root, "steps");

            return FeatureExpansionResponseDTO.success(name, category, description, steps);

        } catch (Exception e) {
            log.warn("Failed to parse JSON response, using fallback", e);
            return createFallbackResponse(response);
        }
    }

    /**
     * Extract JSON object from response text.
     */
    private String extractJson(String response) {
        // Look for JSON block in markdown code fence
        Pattern codeBlockPattern = Pattern.compile("```(?:json)?\\s*\\n?(\\{[\\s\\S]*?\\})\\s*\\n?```", Pattern.MULTILINE);
        Matcher matcher = codeBlockPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Try to find raw JSON object
        Pattern jsonPattern = Pattern.compile("(\\{[\\s\\S]*\"steps\"[\\s\\S]*\\})", Pattern.MULTILINE);
        matcher = jsonPattern.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return null;
    }

    /**
     * Create a fallback response when JSON parsing fails.
     */
    private FeatureExpansionResponseDTO createFallbackResponse(String response) {
        List<String> steps = new ArrayList<>();
        steps.add("Review the AI output above for implementation details");
        steps.add("Implement the feature based on the analysis");
        steps.add("Test the implementation");

        return FeatureExpansionResponseDTO.success(
                "New Feature",
                "Feature",
                response.length() > 500 ? response.substring(0, 500) + "..." : response,
                steps
        );
    }

    /**
     * Get text value from JSON node with default.
     */
    private String getTextOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        return node != null && !node.isNull() ? node.asText() : defaultValue;
    }

    /**
     * Get string list from JSON node.
     */
    private List<String> getStringList(JsonNode root, String field) {
        List<String> result = new ArrayList<>();
        JsonNode node = root.get(field);
        if (node != null && node.isArray()) {
            for (JsonNode item : node) {
                result.add(item.asText());
            }
        }
        return result;
    }

    /**
     * Truncate string for logging.
     */
    private String truncate(String str, int maxLen) {
        if (str == null) return "";
        return str.length() > maxLen ? str.substring(0, maxLen) + "..." : str;
    }
}
