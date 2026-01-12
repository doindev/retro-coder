package org.me.retrocoder.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for listing features by status.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureListResponseDTO {
    private List<FeatureDTO> pending;

    @JsonProperty("in_progress")
    private List<FeatureDTO> inProgress;

    private List<FeatureDTO> done;
}
