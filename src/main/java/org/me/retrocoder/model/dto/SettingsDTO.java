package org.me.retrocoder.model.dto;

import org.me.retrocoder.model.ClaudeIntegrationMode;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Settings Data Transfer Object.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SettingsDTO {
    private Boolean yoloMode;
    private String model;
    private ClaudeIntegrationMode integrationMode;
    private Boolean playwrightHeadless;
}
