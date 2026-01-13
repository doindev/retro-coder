package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.me.retrocoder.model.LlmProviderType;

import java.util.Map;

/**
 * DTO for requesting model discovery from a provider.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscoverModelsRequestDTO {
    private LlmProviderType providerType;
    private Map<String, String> credentials;
    private String endpointUrl;
}
