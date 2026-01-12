package org.me.retrocoder.agent;

import java.util.function.Consumer;

/**
 * Interface for Claude API clients.
 * Supports multiple integration modes.
 */
public interface ClaudeClient {

    /**
     * Send a prompt and receive streaming response.
     *
     * @param prompt The prompt to send
     * @param projectPath The project path for context
     * @param onChunk Callback for each response chunk
     * @return The complete response
     */
    String sendPrompt(String prompt, String projectPath, Consumer<String> onChunk);

    /**
     * Check if the client is authenticated/ready.
     */
    boolean isReady();

    /**
     * Get the client type.
     */
    String getType();

    /**
     * Close/cleanup the client.
     */
    default void close() {
        // Default no-op
    }
}
