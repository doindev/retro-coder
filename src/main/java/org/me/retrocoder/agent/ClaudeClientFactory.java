package org.me.retrocoder.agent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.config.RetrocoderProperties;
import org.me.retrocoder.model.ClaudeIntegrationMode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Factory for creating Claude API clients based on integration mode.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClaudeClientFactory {

    private final RetrocoderProperties properties;
    private final WebClient.Builder webClientBuilder;

    /**
     * Create a Claude client for the specified mode.
     */
    public ClaudeClient createClient(ClaudeIntegrationMode mode) {
        return switch (mode) {
            case CLI_WRAPPER -> createCliWrapperClient();
            case REST_API -> createRestApiClient();
            case JAVA_SDK -> createJavaSdkClient();
        };
    }

    /**
     * Create the default client based on configuration.
     */
    public ClaudeClient createDefaultClient() {
        ClaudeIntegrationMode mode = ClaudeIntegrationMode.fromString(
            properties.getClaude().getDefaultMode()
        );
        return createClient(mode);
    }

    private ClaudeClient createCliWrapperClient() {
        log.info("Creating CLI wrapper client");
        return new CliWrapperClient(properties.getClaude().getCliCommand());
    }

    private ClaudeClient createRestApiClient() {
        log.info("Creating REST API client");
        String apiKey = properties.getClaude().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required for REST API mode");
        }
        return new RestApiClient(webClientBuilder, apiKey, properties.getClaude().getDefaultModel());
    }

    private ClaudeClient createJavaSdkClient() {
        log.info("Creating Java SDK client");
        String apiKey = properties.getClaude().getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("ANTHROPIC_API_KEY is required for Java SDK mode");
        }
        return new JavaSdkClient(apiKey, properties.getClaude().getDefaultModel());
    }
}
