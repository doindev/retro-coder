package org.me.retrocoder.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import org.me.retrocoder.model.ClaudeIntegrationMode;
import org.me.retrocoder.model.Project;

/**
 * Project Data Transfer Object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectDTO {
    private String name;
    private String path;

    @JsonProperty("integration_mode")
    private ClaudeIntegrationMode integrationMode;

    @JsonProperty("created_at")
    private LocalDateTime createdAt;

    @JsonProperty("has_spec")
    private boolean hasSpec;

    @JsonProperty("has_features")
    private boolean hasFeatures;

    @JsonProperty("yolo_mode")
    private Boolean yoloMode;

    private String model;

    private FeatureStatsDTO stats;

    public static ProjectDTO fromEntity(Project project) {
        return ProjectDTO.builder()
            .name(project.getName())
            .path(project.getPath())
            .integrationMode(project.getIntegrationMode())
            .createdAt(project.getCreatedAt())
            .yoloMode(project.getYoloMode())
            .model(project.getModel())
            .build();
    }

    public Project toEntity() {
        return Project.builder()
            .name(this.name)
            .path(this.path)
            .integrationMode(this.integrationMode != null ? this.integrationMode : ClaudeIntegrationMode.CLI_WRAPPER)
            .createdAt(this.createdAt)
            .yoloMode(this.yoloMode)
            .model(this.model)
            .build();
    }
}
