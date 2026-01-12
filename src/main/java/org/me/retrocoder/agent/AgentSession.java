package org.me.retrocoder.agent;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Represents an agent session.
 */
@Data
@Builder
public class AgentSession {
    private String projectName;
    private String projectPath;
    private int sessionNumber;
    private boolean isInitializer;
    private boolean yoloMode;
    private String model;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private int toolUseCounts;
    private String lastResponse;

    /**
     * Check if session is active.
     */
    public boolean isActive() {
        return endTime == null;
    }

    /**
     * Get session duration in seconds.
     */
    public long getDurationSeconds() {
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        return java.time.Duration.between(startTime, end).getSeconds();
    }

    /**
     * End the session.
     */
    public void end() {
        this.endTime = LocalDateTime.now();
    }
}
