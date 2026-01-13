package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.me.retrocoder.model.LlmProviderType;

import java.util.Map;

/**
 * DTO for updating an AI agent configuration.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiAgentConfigUpdateDTO {
    private String name;
    private LlmProviderType providerType;
    private Map<String, String> credentials;  // Only set if changing credentials
    private String endpointUrl;
    private String defaultModel;
    private Map<String, Object> providerSettings;
    private Boolean enabled;
}
