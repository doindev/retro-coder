package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for starting an agent.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStartRequestDTO {
    private Boolean yoloMode;
    private String model;
}
