package org.me.retrocoder.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.anthropic.AnthropicChatModel;
import dev.langchain4j.model.anthropic.AnthropicStreamingChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.azure.AzureOpenAiChatModel;
import dev.langchain4j.model.azure.AzureOpenAiStreamingChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiStreamingChatModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.mistralai.MistralAiStreamingChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.ollama.OllamaStreamingChatModel;
import dev.langchain4j.model.huggingface.HuggingFaceChatModel;
import dev.langchain4j.model.localai.LocalAiChatModel;
import dev.langchain4j.model.localai.LocalAiStreamingChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.config.RetrocoderProperties;
import org.me.retrocoder.model.AiAgentConfig;
import org.me.retrocoder.model.LlmProviderType;
import org.me.retrocoder.service.EncryptionService;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Factory for creating LangChain4j chat models based on AI agent configuration.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LangChain4jClientFactory {

    private final EncryptionService encryptionService;
    private final ObjectMapper objectMapper;
    private final RetrocoderProperties properties;

    /**
     * Create a ChatModel from an AI agent configuration.
     */
    public ChatModel createChatModel(AiAgentConfig config) {
        Map<String, String> credentials = decryptCredentials(config);
        String model = config.getDefaultModel();
        String endpoint = config.getEndpointUrl();

        return switch (config.getProviderType()) {
            case OPENAI -> createOpenAiModel(credentials, model);
            case ANTHROPIC -> createAnthropicModel(credentials, model);
            case AZURE_OPENAI -> createAzureOpenAiModel(credentials, model, endpoint);
            case GOOGLE_GEMINI -> createGoogleGeminiModel(credentials, model);
            case MISTRAL_AI -> createMistralAiModel(credentials, model);
            case OLLAMA -> createOllamaModel(endpoint, model);
            case HUGGING_FACE -> createHuggingFaceModel(credentials, model);
            case LOCAL_AI -> createLocalAiModel(endpoint, model);
            case CUSTOM -> createCustomModel(credentials, endpoint, model);
            case AMAZON_BEDROCK -> createBedrockModel(credentials, model);
            case CLAUDE_CODE -> throw new UnsupportedOperationException(
                "Claude Code uses CLI wrapper, not LangChain4j");
        };
    }

    /**
     * Create a StreamingChatModel from an AI agent configuration.
     */
    public StreamingChatModel createStreamingChatModel(AiAgentConfig config) {
        Map<String, String> credentials = decryptCredentials(config);
        String model = config.getDefaultModel();
        String endpoint = config.getEndpointUrl();

        return switch (config.getProviderType()) {
            case OPENAI -> createOpenAiStreamingModel(credentials, model);
            case ANTHROPIC -> createAnthropicStreamingModel(credentials, model);
            case AZURE_OPENAI -> createAzureOpenAiStreamingModel(credentials, model, endpoint);
            case GOOGLE_GEMINI -> createGoogleGeminiStreamingModel(credentials, model);
            case MISTRAL_AI -> createMistralAiStreamingModel(credentials, model);
            case OLLAMA -> createOllamaStreamingModel(endpoint, model);
            case LOCAL_AI -> createLocalAiStreamingModel(endpoint, model);
            case CUSTOM -> createCustomStreamingModel(credentials, endpoint, model);
            case AMAZON_BEDROCK -> createBedrockStreamingModel(credentials, model);
            case HUGGING_FACE, CLAUDE_CODE -> throw new UnsupportedOperationException(
                "Streaming not supported for provider: " + config.getProviderType());
        };
    }

    /**
     * Check if a provider supports streaming.
     */
    public boolean supportsStreaming(LlmProviderType providerType) {
        return providerType.isSupportsStreaming();
    }

    /**
     * Create a ClaudeClient from an AI agent configuration.
     * This is the main entry point for creating clients that implement the ClaudeClient interface.
     *
     * For CLAUDE_CODE provider: creates a CliWrapperClient that uses the local claude CLI.
     * For other providers: creates LangChain4j models wrapped in LangChain4jClaudeClient.
     */
    public ClaudeClient createClaudeClient(AiAgentConfig config) {
        if (config.getProviderType() == LlmProviderType.CLAUDE_CODE) {
            log.info("Creating Claude CLI wrapper client");
            String cliCommand = properties.getClaude().getCliCommand();
            return new CliWrapperClient(cliCommand);
        }

        // For other providers, use LangChain4j
        log.info("Creating LangChain4j client for provider: {}", config.getProviderType());

        ChatModel chatModel = createChatModel(config);
        StreamingChatModel streamingModel = null;

        if (supportsStreaming(config.getProviderType())) {
            try {
                streamingModel = createStreamingChatModel(config);
            } catch (Exception e) {
                log.warn("Failed to create streaming model for {}, falling back to non-streaming",
                    config.getProviderType(), e);
            }
        }

        return new LangChain4jClaudeClient(chatModel, streamingModel,
            config.getProviderType().name().toLowerCase());
    }

    /**
     * Check if the given config uses the Claude CLI wrapper.
     */
    public boolean isCliWrapper(AiAgentConfig config) {
        return config != null && config.getProviderType() == LlmProviderType.CLAUDE_CODE;
    }

    // === OpenAI ===

    private ChatModel createOpenAiModel(Map<String, String> credentials, String model) {
        return OpenAiChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "gpt-4")
            .build();
    }

    private StreamingChatModel createOpenAiStreamingModel(Map<String, String> credentials, String model) {
        return OpenAiStreamingChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "gpt-4")
            .build();
    }

    // === Anthropic ===

    private ChatModel createAnthropicModel(Map<String, String> credentials, String model) {
        return AnthropicChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "claude-3-5-sonnet-20241022")
            .build();
    }

    private StreamingChatModel createAnthropicStreamingModel(Map<String, String> credentials, String model) {
        return AnthropicStreamingChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "claude-3-5-sonnet-20241022")
            .build();
    }

    // === Azure OpenAI ===

    private ChatModel createAzureOpenAiModel(Map<String, String> credentials, String model, String endpoint) {
        return AzureOpenAiChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .endpoint(endpoint != null ? endpoint : credentials.get("endpoint"))
            .deploymentName(credentials.get("deploymentName"))
            .build();
    }

    private StreamingChatModel createAzureOpenAiStreamingModel(Map<String, String> credentials, String model, String endpoint) {
        return AzureOpenAiStreamingChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .endpoint(endpoint != null ? endpoint : credentials.get("endpoint"))
            .deploymentName(credentials.get("deploymentName"))
            .build();
    }

    // === Google Gemini ===

    private ChatModel createGoogleGeminiModel(Map<String, String> credentials, String model) {
        return GoogleAiGeminiChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "gemini-1.5-pro")
            .build();
    }

    private StreamingChatModel createGoogleGeminiStreamingModel(Map<String, String> credentials, String model) {
        return GoogleAiGeminiStreamingChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "gemini-1.5-pro")
            .build();
    }

    // === Mistral AI ===

    private ChatModel createMistralAiModel(Map<String, String> credentials, String model) {
        return MistralAiChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "mistral-large-latest")
            .build();
    }

    private StreamingChatModel createMistralAiStreamingModel(Map<String, String> credentials, String model) {
        return MistralAiStreamingChatModel.builder()
            .apiKey(credentials.get("apiKey"))
            .modelName(model != null ? model : "mistral-large-latest")
            .build();
    }

    // === Ollama ===

    private ChatModel createOllamaModel(String endpoint, String model) {
        return OllamaChatModel.builder()
            .baseUrl(endpoint != null ? endpoint : "http://localhost:11434")
            .modelName(model != null ? model : "llama3")
            .build();
    }

    private StreamingChatModel createOllamaStreamingModel(String endpoint, String model) {
        return OllamaStreamingChatModel.builder()
            .baseUrl(endpoint != null ? endpoint : "http://localhost:11434")
            .modelName(model != null ? model : "llama3")
            .build();
    }

    // === Hugging Face ===

    private ChatModel createHuggingFaceModel(Map<String, String> credentials, String model) {
        return HuggingFaceChatModel.builder()
            .accessToken(credentials.get("apiKey"))
            .modelId(model)
            .build();
    }

    // === LocalAI ===

    private ChatModel createLocalAiModel(String endpoint, String model) {
        return LocalAiChatModel.builder()
            .baseUrl(endpoint != null ? endpoint : "http://localhost:8080")
            .modelName(model)
            .build();
    }

    private StreamingChatModel createLocalAiStreamingModel(String endpoint, String model) {
        return LocalAiStreamingChatModel.builder()
            .baseUrl(endpoint != null ? endpoint : "http://localhost:8080")
            .modelName(model)
            .build();
    }

    // === Amazon Bedrock ===

    private ChatModel createBedrockModel(Map<String, String> credentials, String model) {
        var region = software.amazon.awssdk.regions.Region.of(credentials.get("region"));
        var credentialsProvider = software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                credentials.get("accessKeyId"),
                credentials.get("secretAccessKey")
            )
        );

        var client = software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();

        return dev.langchain4j.model.bedrock.BedrockChatModel.builder()
            .client(client)
            .modelId(model != null ? model : "anthropic.claude-3-5-sonnet-20241022-v2:0")
            .build();
    }

    private StreamingChatModel createBedrockStreamingModel(Map<String, String> credentials, String model) {
        var region = software.amazon.awssdk.regions.Region.of(credentials.get("region"));
        var credentialsProvider = software.amazon.awssdk.auth.credentials.StaticCredentialsProvider.create(
            software.amazon.awssdk.auth.credentials.AwsBasicCredentials.create(
                credentials.get("accessKeyId"),
                credentials.get("secretAccessKey")
            )
        );

        var asyncClient = software.amazon.awssdk.services.bedrockruntime.BedrockRuntimeAsyncClient.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .build();

        return dev.langchain4j.model.bedrock.BedrockStreamingChatModel.builder()
            .client(asyncClient)
            .modelId(model != null ? model : "anthropic.claude-3-5-sonnet-20241022-v2:0")
            .build();
    }

    // === Custom (OpenAI-compatible) ===

    private ChatModel createCustomModel(Map<String, String> credentials, String endpoint, String model) {
        var builder = OpenAiChatModel.builder()
            .modelName(model);

        if (endpoint != null) {
            builder.baseUrl(endpoint);
        }
        if (credentials.containsKey("apiKey")) {
            builder.apiKey(credentials.get("apiKey"));
        }

        return builder.build();
    }

    private StreamingChatModel createCustomStreamingModel(Map<String, String> credentials, String endpoint, String model) {
        var builder = OpenAiStreamingChatModel.builder()
            .modelName(model);

        if (endpoint != null) {
            builder.baseUrl(endpoint);
        }
        if (credentials.containsKey("apiKey")) {
            builder.apiKey(credentials.get("apiKey"));
        }

        return builder.build();
    }

    // === Utilities ===

    /**
     * Decrypt credentials from the AI agent config.
     */
    private Map<String, String> decryptCredentials(AiAgentConfig config) {
        if (config.getEncryptedCredentials() == null || config.getEncryptedCredentials().isBlank()) {
            return Map.of();
        }

        try {
            String decrypted = encryptionService.decrypt(config.getEncryptedCredentials());
            return objectMapper.readValue(decrypted, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to decrypt credentials for agent: {}", config.getName(), e);
            throw new RuntimeException("Failed to decrypt credentials", e);
        }
    }
}
