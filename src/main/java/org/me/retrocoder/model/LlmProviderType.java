package org.me.retrocoder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.List;

/**
 * Enumeration of all supported LLM providers.
 * Each provider has display information and configuration metadata.
 */
@Getter
@RequiredArgsConstructor
public enum LlmProviderType {

    // Default - uses existing Claude CLI wrapper
    CLAUDE_CODE("Claude Code CLI", "Uses the Claude CLI tool (requires claude CLI installed)", false, true),

    // Major cloud providers
    OPENAI("OpenAI", "GPT-4, GPT-3.5 and other OpenAI models", false, true),
    ANTHROPIC("Anthropic", "Claude models via Anthropic API", false, true),
    AZURE_OPENAI("Azure OpenAI", "OpenAI models deployed on Azure", true, true),
    GOOGLE_GEMINI("Google AI Gemini", "Google Gemini models via AI Studio", false, true),
    MISTRAL_AI("Mistral AI", "Mistral and Mixtral models", false, true),

    // Local/self-hosted
    OLLAMA("Ollama", "Local models via Ollama (Llama, Mistral, etc.)", true, true),
    LOCAL_AI("LocalAI", "Self-hosted OpenAI-compatible API", true, true),

    // Cloud platforms
    AMAZON_BEDROCK("Amazon Bedrock", "AWS Bedrock (Claude, Llama, Titan, etc.)", true, true),
    HUGGING_FACE("Hugging Face", "Hugging Face Inference API", false, false),

    // Custom/manual configuration
    CUSTOM("Custom Endpoint", "Manually configured OpenAI-compatible endpoint", true, true);

    private final String displayName;
    private final String description;
    private final boolean supportsCustomEndpoint;
    private final boolean supportsStreaming;

    /**
     * Get the credential fields required for this provider.
     */
    public List<CredentialField> getRequiredCredentialFields() {
        return switch (this) {
            case CLAUDE_CODE -> List.of(); // Uses CLI, no API credentials

            case OPENAI -> List.of(
                new CredentialField("apiKey", "API Key", "password", true, "sk-...")
            );

            case ANTHROPIC -> List.of(
                new CredentialField("apiKey", "API Key", "password", true, "sk-ant-...")
            );

            case AZURE_OPENAI -> List.of(
                new CredentialField("apiKey", "API Key", "password", true, "Your Azure OpenAI key"),
                new CredentialField("endpoint", "Endpoint URL", "url", true, "https://your-resource.openai.azure.com"),
                new CredentialField("deploymentName", "Deployment Name", "text", true, "gpt-4")
            );

            case GOOGLE_GEMINI -> List.of(
                new CredentialField("apiKey", "API Key", "password", true, "Your Google AI Studio API key")
            );

            case MISTRAL_AI -> List.of(
                new CredentialField("apiKey", "API Key", "password", true, "Your Mistral AI API key")
            );

            case OLLAMA -> List.of(
                new CredentialField("endpoint", "Endpoint URL", "url", false, "http://localhost:11434")
            );

            case LOCAL_AI -> List.of(
                new CredentialField("endpoint", "Endpoint URL", "url", false, "http://localhost:8080")
            );

            case AMAZON_BEDROCK -> List.of(
                new CredentialField("accessKeyId", "Access Key ID", "text", true, "AKIA..."),
                new CredentialField("secretAccessKey", "Secret Access Key", "password", true, "Your AWS secret key"),
                new CredentialField("region", "AWS Region", "text", true, "us-east-1")
            );

            case HUGGING_FACE -> List.of(
                new CredentialField("apiKey", "Access Token", "password", true, "hf_...")
            );

            case CUSTOM -> List.of(
                new CredentialField("endpoint", "Endpoint URL", "url", true, "https://your-endpoint.com/v1"),
                new CredentialField("apiKey", "API Key (optional)", "password", false, "Optional API key")
            );
        };
    }

    /**
     * Check if this provider requires an API key.
     */
    public boolean requiresApiKey() {
        return switch (this) {
            case CLAUDE_CODE, OLLAMA, LOCAL_AI -> false;
            default -> true;
        };
    }

    /**
     * Parse provider type from string (case-insensitive).
     */
    public static LlmProviderType fromString(String value) {
        if (value == null || value.isBlank()) {
            return CLAUDE_CODE;
        }
        try {
            return valueOf(value.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return CLAUDE_CODE;
        }
    }

    /**
     * Credential field definition for provider configuration forms.
     */
    @Getter
    @RequiredArgsConstructor
    public static class CredentialField {
        private final String name;
        private final String label;
        private final String type; // "text", "password", "url"
        private final boolean required;
        private final String placeholder;
    }
}
