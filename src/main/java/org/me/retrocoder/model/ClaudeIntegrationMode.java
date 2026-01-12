package org.me.retrocoder.model;

/**
 * Claude API integration mode options.
 */
public enum ClaudeIntegrationMode {
    /**
     * Use Claude CLI as subprocess (default).
     * Requires Claude CLI to be installed and authenticated.
     */
    CLI_WRAPPER,

    /**
     * Direct Anthropic REST API via WebClient.
     * Requires ANTHROPIC_API_KEY environment variable.
     */
    REST_API,

    /**
     * Community Java SDK wrapper.
     * Uses third-party Java SDK for Anthropic API.
     */
    JAVA_SDK;

    public static ClaudeIntegrationMode fromString(String value) {
        if (value == null || value.isEmpty()) {
            return CLI_WRAPPER;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return CLI_WRAPPER;
        }
    }
}
