package org.me.retrocoder.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Enumeration of agent roles that can have different AI configurations.
 */
@Getter
@RequiredArgsConstructor
public enum AgentRole {

    /**
     * Role for creating application specifications from user requirements.
     */
    SPEC_CREATION("Spec Creation", "Creates application specifications from requirements"),

    /**
     * Role for initializing features from the app spec.
     */
    INITIALIZATION("Initialization", "Initializes features from the app specification"),

    /**
     * Role for the main coding agent that implements features.
     */
    CODING("Coding", "Implements features and writes code");

    private final String displayName;
    private final String description;

    /**
     * Get the settings key for storing the agent ID for this role.
     */
    public String getSettingsKey() {
        return switch (this) {
            case SPEC_CREATION -> "spec_creation_agent_id";
            case INITIALIZATION -> "initialization_agent_id";
            case CODING -> "coding_agent_id";
        };
    }

    /**
     * Parse agent role from string (case-insensitive).
     */
    public static AgentRole fromString(String value) {
        if (value == null || value.isBlank()) {
            return CODING;
        }
        try {
            return valueOf(value.toUpperCase().replace("-", "_").replace(" ", "_"));
        } catch (IllegalArgumentException e) {
            return CODING;
        }
    }
}
