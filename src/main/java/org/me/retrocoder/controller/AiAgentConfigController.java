package org.me.retrocoder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.model.dto.*;
import org.me.retrocoder.service.AiAgentConfigService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST controller for AI agent configuration management.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai-agents")
@RequiredArgsConstructor
public class AiAgentConfigController {

    private final AiAgentConfigService service;

    /**
     * List all AI agent configurations.
     */
    @GetMapping
    public ResponseEntity<List<AiAgentConfigDTO>> listAll() {
        return ResponseEntity.ok(service.listAll());
    }

    /**
     * List all enabled AI agent configurations.
     */
    @GetMapping("/enabled")
    public ResponseEntity<List<AiAgentConfigDTO>> listEnabled() {
        return ResponseEntity.ok(service.listEnabled());
    }

    /**
     * Get AI agent configuration by ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<AiAgentConfigDTO> getById(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(service.getById(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Create a new AI agent configuration.
     */
    @PostMapping
    public ResponseEntity<?> create(@RequestBody AiAgentConfigCreateDTO dto) {
        try {
            AiAgentConfigDTO created = service.create(dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to create AI agent config", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Update an existing AI agent configuration.
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id, @RequestBody AiAgentConfigUpdateDTO dto) {
        try {
            AiAgentConfigDTO updated = service.update(id, dto);
            return ResponseEntity.ok(updated);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to update AI agent config", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Delete an AI agent configuration.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Failed to delete AI agent config", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Discover models from a provider (validates credentials).
     */
    @PostMapping("/discover-models")
    public ResponseEntity<?> discoverModels(@RequestBody DiscoverModelsRequestDTO request) {
        try {
            List<ModelInfoDTO> models = service.discoverModels(request);
            return ResponseEntity.ok(models);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Failed to discover models", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Refresh cached models for an agent configuration.
     */
    @PostMapping("/{id}/refresh-models")
    public ResponseEntity<?> refreshModels(@PathVariable Long id) {
        try {
            List<ModelInfoDTO> models = service.refreshModels(id);
            return ResponseEntity.ok(models);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        } catch (RuntimeException e) {
            log.error("Failed to refresh models for agent {}", id, e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get all supported providers with their configuration schema.
     */
    @GetMapping("/providers")
    public ResponseEntity<List<ProviderInfoDTO>> getProviders() {
        return ResponseEntity.ok(service.getProviders());
    }

    /**
     * Get agent defaults settings.
     */
    @GetMapping("/defaults")
    public ResponseEntity<AgentDefaultsDTO> getDefaults() {
        return ResponseEntity.ok(service.getAgentDefaults());
    }

    /**
     * Update agent defaults settings.
     */
    @PutMapping("/defaults")
    public ResponseEntity<?> updateDefaults(@RequestBody AgentDefaultsDTO dto) {
        try {
            AgentDefaultsDTO updated = service.updateAgentDefaults(dto);
            return ResponseEntity.ok(updated);
        } catch (Exception e) {
            log.error("Failed to update agent defaults", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}
