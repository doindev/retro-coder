package org.me.retrocoder.model.dto;

import org.me.retrocoder.model.ClaudeIntegrationMode;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for creating a new project.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectCreateDTO {

    @NotBlank(message = "Project name is required")
    @Size(min = 1, max = 50, message = "Project name must be 1-50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Project name must be alphanumeric with dashes and underscores only")
    private String name;

    @NotBlank(message = "Project path is required")
    private String path;

    private ClaudeIntegrationMode integrationMode;
}
