package org.me.retrocoder.agent;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Consumer;

/**
 * Claude client using a community Java SDK.
 * This is a placeholder implementation - actual SDK integration to be added.
 */
@Slf4j
public class JavaSdkClient implements ClaudeClient {

    private final String apiKey;
    private final String model;

    public JavaSdkClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public String sendPrompt(String prompt, String projectPath, Consumer<String> onChunk) {
        // TODO: Implement actual Java SDK integration
        // For now, delegate to REST API implementation
        log.warn("Java SDK client not yet implemented, falling back to REST API");

        throw new UnsupportedOperationException(
            "Java SDK client not yet implemented. Please use CLI_WRAPPER or REST_API mode."
        );
    }

    @Override
    public boolean isReady() {
        return apiKey != null && !apiKey.isEmpty();
    }

    @Override
    public String getType() {
        return "JAVA_SDK";
    }
}
