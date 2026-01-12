package org.me.retrocoder.model.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for available models.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelsResponseDTO {

    private List<ModelInfoDTO> models;

    @JsonProperty("default")
    private String defaultModel;
}
