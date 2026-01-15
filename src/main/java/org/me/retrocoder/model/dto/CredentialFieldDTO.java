package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for credential field information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CredentialFieldDTO {
    private String name;
    private String label;
    private String type;  // "text", "password", "url"
    private boolean required;
    private String placeholder;
}
