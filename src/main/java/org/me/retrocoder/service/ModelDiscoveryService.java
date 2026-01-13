package org.me.retrocoder.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.model.LlmProviderType;
import org.me.retrocoder.model.dto.ModelInfoDTO;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.*;

/**
 * Service for discovering available models from LLM providers.
 * This validates credentials and returns the list of available models.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelDiscoveryService {

    private final WebClient.Builder webClientBuilder;
    private final ObjectMapper objectMapper;

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    /**
     * Discover available models from a provider.
     * This also validates the credentials.
     */
    public List<ModelInfoDTO> discoverModels(LlmProviderType providerType,
                                              Map<String, String> credentials,
                                              String endpointUrl) {
        return switch (providerType) {
            case OPENAI -> discoverOpenAiModels(credentials);
            case ANTHROPIC -> discoverAnthropicModels(credentials);
            case AZURE_OPENAI -> discoverAzureOpenAiModels(credentials, endpointUrl);
            case GOOGLE_GEMINI -> discoverGoogleGeminiModels(credentials);
            case MISTRAL_AI -> discoverMistralAiModels(credentials);
            case OLLAMA -> discoverOllamaModels(endpointUrl);
            case HUGGING_FACE -> getHuggingFaceModels();
            case LOCAL_AI -> discoverLocalAiModels(endpointUrl);
            case CUSTOM -> discoverCustomModels(credentials, endpointUrl);
            case CLAUDE_CODE -> getClaudeCodeModels();
            case AMAZON_BEDROCK -> getBedrockModels();
        };
    }

    private ModelInfoDTO model(String id, String name) {
        return ModelInfoDTO.builder().id(id).name(name).build();
    }

    // === OpenAI ===

    private List<ModelInfoDTO> discoverOpenAiModels(Map<String, String> credentials) {
        String apiKey = credentials.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OpenAI API key is required");
        }

        try {
            WebClient client = webClientBuilder
                .baseUrl("https://api.openai.com/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

            String response = client.get()
                .uri("/models")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            for (JsonNode modelNode : root.get("data")) {
                String id = modelNode.get("id").asText();
                if (id.contains("gpt") || id.contains("o1") || id.contains("chatgpt")) {
                    models.add(model(id, id));
                }
            }

            models.sort(Comparator.comparing(ModelInfoDTO::getName));
            return models;

        } catch (Exception e) {
            log.error("Failed to discover OpenAI models", e);
            throw new RuntimeException("Failed to validate OpenAI credentials: " + e.getMessage());
        }
    }

    // === Anthropic ===

    private List<ModelInfoDTO> discoverAnthropicModels(Map<String, String> credentials) {
        String apiKey = credentials.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Anthropic API key is required");
        }

        try {
            WebClient client = webClientBuilder
                .baseUrl("https://api.anthropic.com/v1")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .build();

            client.post()
                .uri("/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                    "model", "claude-3-5-sonnet-20241022",
                    "max_tokens", 1,
                    "messages", List.of(Map.of("role", "user", "content", "Hi"))
                ))
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

        } catch (Exception e) {
            String msg = e.getMessage();
            if (msg != null && (msg.contains("401") || msg.contains("403") || msg.contains("invalid"))) {
                throw new RuntimeException("Failed to validate Anthropic credentials: " + msg);
            }
        }

        return List.of(
            model("claude-opus-4-5-20251101", "Claude Opus 4.5"),
            model("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5"),
            model("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
            model("claude-3-5-haiku-20241022", "Claude 3.5 Haiku"),
            model("claude-3-opus-20240229", "Claude 3 Opus"),
            model("claude-3-sonnet-20240229", "Claude 3 Sonnet"),
            model("claude-3-haiku-20240307", "Claude 3 Haiku")
        );
    }

    // === Azure OpenAI ===

    private List<ModelInfoDTO> discoverAzureOpenAiModels(Map<String, String> credentials, String endpoint) {
        String apiKey = credentials.get("apiKey");
        String baseUrl = endpoint != null ? endpoint : credentials.get("endpoint");

        if (apiKey == null || baseUrl == null) {
            throw new IllegalArgumentException("Azure OpenAI requires API key and endpoint");
        }

        try {
            WebClient client = webClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("api-key", apiKey)
                .build();

            String response = client.get()
                .uri("/openai/deployments?api-version=2024-02-01")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            for (JsonNode deployment : root.get("data")) {
                String id = deployment.get("id").asText();
                String modelName = deployment.has("model") ? deployment.get("model").asText() : id;
                models.add(model(id, modelName + " (" + id + ")"));
            }

            return models;

        } catch (Exception e) {
            log.error("Failed to discover Azure OpenAI models", e);
            throw new RuntimeException("Failed to validate Azure OpenAI credentials: " + e.getMessage());
        }
    }

    // === Google Gemini ===

    private List<ModelInfoDTO> discoverGoogleGeminiModels(Map<String, String> credentials) {
        String apiKey = credentials.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Google AI API key is required");
        }

        try {
            WebClient client = webClientBuilder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta")
                .build();

            String response = client.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/models")
                    .queryParam("key", apiKey)
                    .build())
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            for (JsonNode modelNode : root.get("models")) {
                String name = modelNode.get("name").asText();
                String displayName = modelNode.has("displayName") ? modelNode.get("displayName").asText() : name;
                String id = name.replace("models/", "");
                if (id.startsWith("gemini")) {
                    models.add(model(id, displayName));
                }
            }

            return models;

        } catch (Exception e) {
            log.error("Failed to discover Google Gemini models", e);
            throw new RuntimeException("Failed to validate Google AI credentials: " + e.getMessage());
        }
    }

    // === Mistral AI ===

    private List<ModelInfoDTO> discoverMistralAiModels(Map<String, String> credentials) {
        String apiKey = credentials.get("apiKey");
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("Mistral AI API key is required");
        }

        try {
            WebClient client = webClientBuilder
                .baseUrl("https://api.mistral.ai/v1")
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .build();

            String response = client.get()
                .uri("/models")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            for (JsonNode modelNode : root.get("data")) {
                String id = modelNode.get("id").asText();
                models.add(model(id, id));
            }

            return models;

        } catch (Exception e) {
            log.error("Failed to discover Mistral AI models", e);
            throw new RuntimeException("Failed to validate Mistral AI credentials: " + e.getMessage());
        }
    }

    // === Ollama ===

    private List<ModelInfoDTO> discoverOllamaModels(String endpoint) {
        String baseUrl = endpoint != null ? endpoint : "http://localhost:11434";

        try {
            WebClient client = webClientBuilder.baseUrl(baseUrl).build();

            String response = client.get()
                .uri("/api/tags")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            if (root.has("models")) {
                for (JsonNode modelNode : root.get("models")) {
                    String name = modelNode.get("name").asText();
                    models.add(model(name, name));
                }
            }

            return models;

        } catch (Exception e) {
            log.error("Failed to discover Ollama models", e);
            throw new RuntimeException("Failed to connect to Ollama at " + baseUrl + ": " + e.getMessage());
        }
    }

    // === LocalAI ===

    private List<ModelInfoDTO> discoverLocalAiModels(String endpoint) {
        String baseUrl = endpoint != null ? endpoint : "http://localhost:8080";

        try {
            WebClient client = webClientBuilder.baseUrl(baseUrl).build();

            String response = client.get()
                .uri("/v1/models")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            for (JsonNode modelNode : root.get("data")) {
                String id = modelNode.get("id").asText();
                models.add(model(id, id));
            }

            return models;

        } catch (Exception e) {
            log.error("Failed to discover LocalAI models", e);
            throw new RuntimeException("Failed to connect to LocalAI at " + baseUrl + ": " + e.getMessage());
        }
    }

    // === Custom (OpenAI-compatible) ===

    private List<ModelInfoDTO> discoverCustomModels(Map<String, String> credentials, String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new IllegalArgumentException("Custom endpoint URL is required");
        }

        try {
            var clientBuilder = webClientBuilder.baseUrl(endpoint);
            String apiKey = credentials.get("apiKey");
            if (apiKey != null && !apiKey.isBlank()) {
                clientBuilder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey);
            }

            WebClient client = clientBuilder.build();

            String response = client.get()
                .uri("/v1/models")
                .retrieve()
                .bodyToMono(String.class)
                .timeout(TIMEOUT)
                .block();

            JsonNode root = objectMapper.readTree(response);
            List<ModelInfoDTO> models = new ArrayList<>();

            if (root.has("data")) {
                for (JsonNode modelNode : root.get("data")) {
                    String id = modelNode.get("id").asText();
                    models.add(model(id, id));
                }
            }

            return models;

        } catch (Exception e) {
            log.error("Failed to discover custom endpoint models", e);
            throw new RuntimeException("Failed to connect to custom endpoint: " + e.getMessage());
        }
    }

    // === Static model lists ===

    private List<ModelInfoDTO> getClaudeCodeModels() {
        // Models available via Claude Code CLI
        return List.of(
            model("claude-opus-4-5-20251101", "Claude Opus 4.5 (Default)"),
            model("claude-sonnet-4-5-20250929", "Claude Sonnet 4.5"),
            model("claude-sonnet-4-20250514", "Claude Sonnet 4"),
            model("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet"),
            model("claude-3-5-haiku-20241022", "Claude 3.5 Haiku"),
            model("claude-3-opus-20240229", "Claude 3 Opus"),
            model("claude-3-sonnet-20240229", "Claude 3 Sonnet"),
            model("claude-3-haiku-20240307", "Claude 3 Haiku")
        );
    }

    private List<ModelInfoDTO> getHuggingFaceModels() {
        return List.of(
            model("meta-llama/Llama-3.1-70B-Instruct", "Llama 3.1 70B Instruct"),
            model("meta-llama/Llama-3.1-8B-Instruct", "Llama 3.1 8B Instruct"),
            model("mistralai/Mixtral-8x7B-Instruct-v0.1", "Mixtral 8x7B Instruct"),
            model("mistralai/Mistral-7B-Instruct-v0.2", "Mistral 7B Instruct v0.2")
        );
    }

    private List<ModelInfoDTO> getBedrockModels() {
        return List.of(
            model("anthropic.claude-3-5-sonnet-20241022-v2:0", "Claude 3.5 Sonnet v2"),
            model("anthropic.claude-3-5-haiku-20241022-v1:0", "Claude 3.5 Haiku"),
            model("anthropic.claude-3-opus-20240229-v1:0", "Claude 3 Opus"),
            model("anthropic.claude-3-sonnet-20240229-v1:0", "Claude 3 Sonnet"),
            model("anthropic.claude-3-haiku-20240307-v1:0", "Claude 3 Haiku"),
            model("meta.llama3-2-90b-instruct-v1:0", "Llama 3.2 90B Instruct"),
            model("meta.llama3-2-11b-instruct-v1:0", "Llama 3.2 11B Instruct"),
            model("meta.llama3-1-70b-instruct-v1:0", "Llama 3.1 70B Instruct"),
            model("meta.llama3-1-8b-instruct-v1:0", "Llama 3.1 8B Instruct"),
            model("amazon.titan-text-premier-v1:0", "Amazon Titan Text Premier"),
            model("mistral.mistral-large-2407-v1:0", "Mistral Large"),
            model("cohere.command-r-plus-v1:0", "Cohere Command R+")
        );
    }
}
