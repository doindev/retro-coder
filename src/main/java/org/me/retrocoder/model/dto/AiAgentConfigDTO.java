package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.me.retrocoder.model.LlmProviderType;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * DTO for AI agent configuration response.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentConfigDTO {
    private Long id;
    private String name;
    private LlmProviderType providerType;
    private String endpointUrl;
    private String defaultModel;
    private List<ModelInfoDTO> cachedModels;
    private Map<String, Object> providerSettings;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime modelsLastFetched;
}
