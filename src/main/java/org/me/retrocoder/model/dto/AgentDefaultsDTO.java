package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for agent role defaults configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentDefaultsDTO {
    private Long defaultAgentId;
    private Long specCreationAgentId;
    private Long initializationAgentId;
    private Long codingAgentId;
}
