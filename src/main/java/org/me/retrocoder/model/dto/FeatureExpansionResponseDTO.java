package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for feature expansion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureExpansionResponseDTO {

    private String name;
    private String category;
    private String description;
    private List<String> steps;
    private boolean success;
    private String error;

    /**
     * Create a successful response.
     */
    public static FeatureExpansionResponseDTO success(String name, String category, String description, List<String> steps) {
        return FeatureExpansionResponseDTO.builder()
                .name(name)
                .category(category)
                .description(description)
                .steps(steps)
                .success(true)
                .build();
    }

    /**
     * Create a failure response.
     */
    public static FeatureExpansionResponseDTO failure(String error) {
        return FeatureExpansionResponseDTO.builder()
                .success(false)
                .error(error)
                .build();
    }
}
