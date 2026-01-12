package org.me.retrocoder.controller;

import java.util.List;
import java.util.stream.Collectors;

import org.me.retrocoder.config.AutocoderProperties;
import org.me.retrocoder.model.Settings;
import org.me.retrocoder.model.dto.ModelInfoDTO;
import org.me.retrocoder.model.dto.ModelsResponseDTO;
import org.me.retrocoder.model.dto.SettingsDTO;
import org.me.retrocoder.service.RegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for global settings.
 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsController {

    private final RegistryService registryService;
    private final AutocoderProperties properties;

    /**
     * Get available models.
     */
    @GetMapping("/models")
    public ResponseEntity<ModelsResponseDTO> getAvailableModels() {
        List<ModelInfoDTO> models = properties.getModels().stream()
            .map(m -> ModelInfoDTO.builder()
                .id(m.getId())
                .name(m.getName())
                .isDefault(m.isDefault())
                .build())
            .collect(Collectors.toList());

        // Find the default model
        String defaultModel = properties.getModels().stream()
            .filter(AutocoderProperties.Model::isDefault)
            .map(AutocoderProperties.Model::getId)
            .findFirst()
            .orElse(models.isEmpty() ? null : models.get(0).getId());

        ModelsResponseDTO response = ModelsResponseDTO.builder()
            .models(models)
            .defaultModel(defaultModel)
            .build();

        return ResponseEntity.ok(response);
    }

    /**
     * Get current settings.
     */
    @GetMapping
    public ResponseEntity<SettingsDTO> getSettings() {
        SettingsDTO settings = SettingsDTO.builder()
            .yoloMode(registryService.isYoloMode())
            .model(registryService.getDefaultModel())
            .integrationMode(registryService.getDefaultIntegrationMode())
            .playwrightHeadless(registryService.getBooleanSetting(
                Settings.KEY_PLAYWRIGHT_HEADLESS,
                properties.getAgent().isPlaywrightHeadless()))
            .build();

        return ResponseEntity.ok(settings);
    }

    /**
     * Update settings.
     */
    @PatchMapping
    public ResponseEntity<SettingsDTO> updateSettings(@RequestBody SettingsDTO settings) {
        try {
            if (settings.getYoloMode() != null) {
                registryService.setBooleanSetting(Settings.KEY_YOLO_MODE, settings.getYoloMode());
            }

            if (settings.getModel() != null) {
                registryService.setSetting(Settings.KEY_DEFAULT_MODEL, settings.getModel());
            }

            if (settings.getIntegrationMode() != null) {
                registryService.setSetting(
                    Settings.KEY_DEFAULT_INTEGRATION_MODE,
                    settings.getIntegrationMode().name()
                );
            }

            if (settings.getPlaywrightHeadless() != null) {
                registryService.setBooleanSetting(
                    Settings.KEY_PLAYWRIGHT_HEADLESS,
                    settings.getPlaywrightHeadless()
                );
            }

            return getSettings();
        } catch (Exception e) {
            log.error("Failed to update settings", e);
            return ResponseEntity.internalServerError().build();
        }
    }
}
