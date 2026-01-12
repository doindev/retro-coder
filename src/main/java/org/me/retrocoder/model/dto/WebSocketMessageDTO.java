package org.me.retrocoder.model.dto;

import org.me.retrocoder.model.AgentStatus;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WebSocket message DTO for real-time updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WebSocketMessageDTO {
    private String type;
    private Object data;

    // Progress message fields
    private Integer passing;
    private Integer inProgress;
    private Integer total;
    private Double percentage;

    // Feature update fields
    private Long featureId;
    private Boolean passes;

    // Log message fields
    private String line;
    private String timestamp;

    // Agent status fields
    private AgentStatus status;

    public static WebSocketMessageDTO progress(int passing, int inProgress, int total) {
        double percentage = total > 0 ? (passing * 100.0 / total) : 0.0;
        return WebSocketMessageDTO.builder()
            .type("progress")
            .passing(passing)
            .inProgress(inProgress)
            .total(total)
            .percentage(Math.round(percentage * 100.0) / 100.0)
            .build();
    }

    public static WebSocketMessageDTO featureUpdate(Long featureId, boolean passes) {
        return WebSocketMessageDTO.builder()
            .type("feature_update")
            .featureId(featureId)
            .passes(passes)
            .build();
    }

    public static WebSocketMessageDTO log(String line) {
        return WebSocketMessageDTO.builder()
            .type("log")
            .line(line)
            .timestamp(java.time.Instant.now().toString())
            .build();
    }

    public static WebSocketMessageDTO agentStatus(AgentStatus status) {
        return WebSocketMessageDTO.builder()
            .type("agent_status")
            .status(status)
            .build();
    }

    public static WebSocketMessageDTO pong() {
        return WebSocketMessageDTO.builder()
            .type("pong")
            .build();
    }
}
