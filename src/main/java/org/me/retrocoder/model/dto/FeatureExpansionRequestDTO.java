package org.me.retrocoder.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for feature expansion.
 */
@Data
public class FeatureExpansionRequestDTO {

    @NotBlank(message = "Feature description is required")
    private String description;
}
