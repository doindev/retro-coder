package org.me.retrocoder.model.dto;

import org.me.retrocoder.model.AgentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent status response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStatusDTO {
    private AgentStatus status;
    private Long pid;
    private String message;

    public static AgentStatusDTO stopped() {
        return AgentStatusDTO.builder()
            .status(AgentStatus.STOPPED)
            .build();
    }

    public static AgentStatusDTO running(Long pid) {
        return AgentStatusDTO.builder()
            .status(AgentStatus.RUNNING)
            .pid(pid)
            .build();
    }

    public static AgentStatusDTO paused(Long pid) {
        return AgentStatusDTO.builder()
            .status(AgentStatus.PAUSED)
            .pid(pid)
            .build();
    }

    public static AgentStatusDTO crashed(String message) {
        return AgentStatusDTO.builder()
            .status(AgentStatus.CRASHED)
            .message(message)
            .build();
    }
}
