package org.me.retrocoder.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Feature progress statistics DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeatureStatsDTO {
    private int passing;

    @JsonProperty("in_progress")
    private int inProgress;

    private int total;
    private double percentage;

    public static FeatureStatsDTO of(int passing, int inProgress, int total) {
        double percentage = total > 0 ? (passing * 100.0 / total) : 0.0;
        return FeatureStatsDTO.builder()
            .passing(passing)
            .inProgress(inProgress)
            .total(total)
            .percentage(Math.round(percentage * 100.0) / 100.0)
            .build();
    }
}
