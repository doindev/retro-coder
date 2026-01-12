package org.me.retrocoder.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

import org.me.retrocoder.model.Feature;

/**
 * Feature Data Transfer Object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureDTO {
    private Long id;
    private Integer priority;
    private String category;
    private String name;
    private String description;
    private List<String> steps;
    private Boolean passes;

    @JsonProperty("in_progress")
    private Boolean inProgress;

    public static FeatureDTO fromEntity(Feature feature) {
        return FeatureDTO.builder()
            .id(feature.getId())
            .priority(feature.getPriority())
            .category(feature.getCategory())
            .name(feature.getName())
            .description(feature.getDescription())
            .steps(feature.getStepsList())
            .passes(feature.getPasses())
            .inProgress(feature.getInProgress())
            .build();
    }

    public Feature toEntity() {
        Feature feature = new Feature();
        feature.setId(this.id);
        feature.setPriority(this.priority != null ? this.priority : 999);
        feature.setCategory(this.category);
        feature.setName(this.name);
        feature.setDescription(this.description);
        feature.setStepsList(this.steps);
        feature.setPasses(this.passes != null ? this.passes : false);
        feature.setInProgress(this.inProgress != null ? this.inProgress : false);
        return feature;
    }
}
