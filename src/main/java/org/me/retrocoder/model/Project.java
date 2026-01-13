package org.me.retrocoder.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Project entity for the project registry.
 * Stored in the global registry database (~/.autocoder/registry.db).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "projects")
public class Project {

    @Id
    @Column(length = 50)
    private String name;

    @Column(nullable = false)
    private String path;

    @Builder.Default
    @Column(name = "integration_mode", length = 20)
    @Enumerated(EnumType.STRING)
    private ClaudeIntegrationMode integrationMode = ClaudeIntegrationMode.CLI_WRAPPER;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "yolo_mode")
    private Boolean yoloMode;

    @Column(name = "model", length = 100)
    private String model;

    /**
     * Project-level default AI agent configuration ID.
     * If null, uses global default.
     */
    @Column(name = "ai_agent_id")
    private Long aiAgentId;

    /**
     * Override AI agent for spec creation role.
     * If null, uses project's aiAgentId or global default.
     */
    @Column(name = "spec_creation_agent_id")
    private Long specCreationAgentId;

    /**
     * Override AI agent for initialization role.
     * If null, uses project's aiAgentId or global default.
     */
    @Column(name = "initialization_agent_id")
    private Long initializationAgentId;

    /**
     * Override AI agent for coding role.
     * If null, uses project's aiAgentId or global default.
     */
    @Column(name = "coding_agent_id")
    private Long codingAgentId;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (integrationMode == null) {
            integrationMode = ClaudeIntegrationMode.CLI_WRAPPER;
        }
    }

    /**
     * Validate project name format.
     * Must be alphanumeric with dashes and underscores, 1-50 chars.
     */
    public static boolean isValidName(String name) {
        if (name == null || name.isEmpty() || name.length() > 50) {
            return false;
        }
        return name.matches("^[a-zA-Z0-9_-]+$");
    }

    /**
     * Get the path in POSIX format (forward slashes).
     */
    public String getPosixPath() {
        return path != null ? path.replace("\\", "/") : null;
    }
}
