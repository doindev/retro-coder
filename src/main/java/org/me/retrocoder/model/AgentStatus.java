package org.me.retrocoder.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Agent execution status.
 */
public enum AgentStatus {
    /**
     * Agent is not running.
     */
    STOPPED,

    /**
     * Agent is actively running.
     */
    RUNNING,

    /**
     * Agent is paused (waiting to resume).
     */
    PAUSED,

    /**
     * Agent crashed unexpectedly.
     */
    CRASHED;

    /**
     * Serialize as lowercase for frontend compatibility.
     */
    @JsonValue
    public String toValue() {
        return name().toLowerCase();
    }

    public boolean isActive() {
        return this == RUNNING || this == PAUSED;
    }

    public static AgentStatus fromString(String value) {
        if (value == null || value.isEmpty()) {
            return STOPPED;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return STOPPED;
        }
    }
}
