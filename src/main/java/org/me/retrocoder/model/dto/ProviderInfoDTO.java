package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.me.retrocoder.model.LlmProviderType;

import java.util.List;

/**
 * DTO for provider information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProviderInfoDTO {
    private LlmProviderType type;
    private String displayName;
    private String description;
    private List<CredentialFieldDTO> requiredCredentials;
    private boolean supportsCustomEndpoint;
    private boolean supportsStreaming;
}
