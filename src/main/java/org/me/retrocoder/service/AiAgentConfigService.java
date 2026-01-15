package org.me.retrocoder.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.model.AgentRole;
import org.me.retrocoder.model.AiAgentConfig;
import org.me.retrocoder.model.LlmProviderType;
import org.me.retrocoder.model.Project;
import org.me.retrocoder.model.Settings;
import org.me.retrocoder.model.dto.*;
import org.me.retrocoder.repository.AiAgentConfigRepository;
import org.me.retrocoder.repository.ProjectRepository;
import org.me.retrocoder.repository.SettingsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing AI agent configurations.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiAgentConfigService {

    private final AiAgentConfigRepository repository;
    private final SettingsRepository settingsRepository;
    private final ProjectRepository projectRepository;
    private final EncryptionService encryptionService;
    private final ModelDiscoveryService modelDiscoveryService;
    private final ObjectMapper objectMapper;

    /**
     * List all AI agent configurations.
     */
    public List<AiAgentConfigDTO> listAll() {
        return repository.findAllByOrderByNameAsc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * List all enabled AI agent configurations.
     */
    public List<AiAgentConfigDTO> listEnabled() {
        return repository.findByEnabledTrueOrderByNameAsc().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * Get AI agent configuration by ID.
     */
    public AiAgentConfigDTO getById(Long id) {
        return repository.findById(id)
            .map(this::toDTO)
            .orElseThrow(() -> new IllegalArgumentException("AI agent config not found: " + id));
    }

    /**
     * Get AI agent configuration entity by ID.
     */
    public AiAgentConfig getEntityById(Long id) {
        return repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("AI agent config not found: " + id));
    }

    /**
     * Get AI agent configuration by name.
     */
    public AiAgentConfigDTO getByName(String name) {
        return repository.findByName(name)
            .map(this::toDTO)
            .orElseThrow(() -> new IllegalArgumentException("AI agent config not found: " + name));
    }

    /**
     * Create a new AI agent configuration.
     */
    @Transactional
    public AiAgentConfigDTO create(AiAgentConfigCreateDTO dto) {
        if (repository.existsByName(dto.getName())) {
            throw new IllegalArgumentException("AI agent config with name '" + dto.getName() + "' already exists");
        }

        AiAgentConfig config = AiAgentConfig.builder()
            .name(dto.getName())
            .providerType(dto.getProviderType())
            .endpointUrl(dto.getEndpointUrl())
            .defaultModel(dto.getDefaultModel())
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        // Encrypt and store credentials
        if (dto.getCredentials() != null && !dto.getCredentials().isEmpty()) {
            String credentialsJson = toJson(dto.getCredentials());
            config.setEncryptedCredentials(encryptionService.encrypt(credentialsJson));
        }

        // Store provider settings
        if (dto.getProviderSettings() != null && !dto.getProviderSettings().isEmpty()) {
            config.setProviderSettings(toJson(dto.getProviderSettings()));
        }

        AiAgentConfig saved = repository.save(config);
        log.info("Created AI agent config: {} ({})", saved.getName(), saved.getProviderType());

        return toDTO(saved);
    }

    /**
     * Update an existing AI agent configuration.
     */
    @Transactional
    public AiAgentConfigDTO update(Long id, AiAgentConfigUpdateDTO dto) {
        AiAgentConfig config = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("AI agent config not found: " + id));

        // Check name uniqueness if changing
        if (dto.getName() != null && !dto.getName().equals(config.getName())) {
            if (repository.existsByName(dto.getName())) {
                throw new IllegalArgumentException("AI agent config with name '" + dto.getName() + "' already exists");
            }
            config.setName(dto.getName());
        }

        if (dto.getProviderType() != null) {
            config.setProviderType(dto.getProviderType());
        }

        if (dto.getEndpointUrl() != null) {
            config.setEndpointUrl(dto.getEndpointUrl());
        }

        if (dto.getDefaultModel() != null) {
            config.setDefaultModel(dto.getDefaultModel());
        }

        if (dto.getEnabled() != null) {
            config.setEnabled(dto.getEnabled());
        }

        // Update credentials if provided
        if (dto.getCredentials() != null && !dto.getCredentials().isEmpty()) {
            String credentialsJson = toJson(dto.getCredentials());
            config.setEncryptedCredentials(encryptionService.encrypt(credentialsJson));
        }

        // Update provider settings if provided
        if (dto.getProviderSettings() != null) {
            config.setProviderSettings(toJson(dto.getProviderSettings()));
        }

        config.setUpdatedAt(LocalDateTime.now());
        AiAgentConfig saved = repository.save(config);
        log.info("Updated AI agent config: {}", saved.getName());

        return toDTO(saved);
    }

    /**
     * Delete an AI agent configuration.
     */
    @Transactional
    public void delete(Long id) {
        AiAgentConfig config = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("AI agent config not found: " + id));

        // Check if this is set as any default
        String idStr = String.valueOf(id);
        checkNotUsedAsDefault(idStr, Settings.KEY_DEFAULT_AI_AGENT_ID, "global default");
        checkNotUsedAsDefault(idStr, Settings.KEY_SPEC_CREATION_AGENT_ID, "spec creation default");
        checkNotUsedAsDefault(idStr, Settings.KEY_INITIALIZATION_AGENT_ID, "initialization default");
        checkNotUsedAsDefault(idStr, Settings.KEY_CODING_AGENT_ID, "coding default");

        repository.delete(config);
        log.info("Deleted AI agent config: {}", config.getName());
    }

    private void checkNotUsedAsDefault(String idStr, String settingKey, String roleName) {
        settingsRepository.findByKey(settingKey).ifPresent(setting -> {
            if (idStr.equals(setting.getValue())) {
                throw new IllegalStateException("Cannot delete: this agent is set as " + roleName);
            }
        });
    }

    /**
     * Discover models from provider (validates credentials).
     */
    public List<ModelInfoDTO> discoverModels(DiscoverModelsRequestDTO request) {
        return modelDiscoveryService.discoverModels(
            request.getProviderType(),
            request.getCredentials(),
            request.getEndpointUrl()
        );
    }

    /**
     * Refresh cached models for an agent configuration.
     */
    @Transactional
    public List<ModelInfoDTO> refreshModels(Long id) {
        AiAgentConfig config = repository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("AI agent config not found: " + id));

        Map<String, String> credentials = decryptCredentials(config);
        List<ModelInfoDTO> models = modelDiscoveryService.discoverModels(
            config.getProviderType(),
            credentials,
            config.getEndpointUrl()
        );

        // Cache the models
        config.setCachedModels(toJson(models));
        config.setModelsLastFetched(LocalDateTime.now());
        config.setUpdatedAt(LocalDateTime.now());
        repository.save(config);

        log.info("Refreshed {} models for agent: {}", models.size(), config.getName());
        return models;
    }

    /**
     * Get the AI agent configuration for a specific role.
     * Resolution order: Project role override -> Project default -> Global role override -> Global default -> Default Claude CLI
     */
    public AiAgentConfig getAgentForRole(AgentRole role, String projectName) {
        Long agentId = null;

        // 1. Check project-level role override
        if (projectName != null) {
            Optional<Project> projectOpt = projectRepository.findByName(projectName);
            if (projectOpt.isPresent()) {
                Project project = projectOpt.get();
                agentId = switch (role) {
                    case SPEC_CREATION -> project.getSpecCreationAgentId();
                    case INITIALIZATION -> project.getInitializationAgentId();
                    case CODING -> project.getCodingAgentId();
                };

                // 2. Fall back to project default
                if (agentId == null) {
                    agentId = project.getAiAgentId();
                }
            }
        }

        // 3. Check global role override
        if (agentId == null) {
            String settingKey = switch (role) {
                case SPEC_CREATION -> Settings.KEY_SPEC_CREATION_AGENT_ID;
                case INITIALIZATION -> Settings.KEY_INITIALIZATION_AGENT_ID;
                case CODING -> Settings.KEY_CODING_AGENT_ID;
            };
            agentId = getSettingAsLong(settingKey);
        }

        // 4. Fall back to global default
        if (agentId == null) {
            agentId = getSettingAsLong(Settings.KEY_DEFAULT_AI_AGENT_ID);
        }

        // 5. Try to find by ID
        if (agentId != null) {
            Optional<AiAgentConfig> config = repository.findById(agentId)
                .filter(c -> Boolean.TRUE.equals(c.getEnabled()));
            if (config.isPresent()) {
                return config.get();
            }
        }

        // 6. Fall back to default Claude Code CLI agent
        return getDefaultClaudeCodeAgent();
    }

    /**
     * Get the default Claude Code CLI agent.
     * Returns the agent if it exists and is enabled, null otherwise.
     */
    public AiAgentConfig getDefaultClaudeCodeAgent() {
        return repository.findByName(DEFAULT_CLAUDE_CODE_AGENT_NAME)
            .filter(config -> Boolean.TRUE.equals(config.getEnabled()))
            .orElse(null);
    }

    /**
     * The default name for the Claude Code CLI agent.
     */
    public static final String DEFAULT_CLAUDE_CODE_AGENT_NAME = "Claude Code CLI";

    /**
     * Get agent defaults settings.
     */
    public AgentDefaultsDTO getAgentDefaults() {
        return AgentDefaultsDTO.builder()
            .defaultAgentId(getSettingAsLong(Settings.KEY_DEFAULT_AI_AGENT_ID))
            .specCreationAgentId(getSettingAsLong(Settings.KEY_SPEC_CREATION_AGENT_ID))
            .initializationAgentId(getSettingAsLong(Settings.KEY_INITIALIZATION_AGENT_ID))
            .codingAgentId(getSettingAsLong(Settings.KEY_CODING_AGENT_ID))
            .build();
    }

    /**
     * Update agent defaults settings.
     */
    @Transactional
    public AgentDefaultsDTO updateAgentDefaults(AgentDefaultsDTO dto) {
        saveSetting(Settings.KEY_DEFAULT_AI_AGENT_ID, dto.getDefaultAgentId());
        saveSetting(Settings.KEY_SPEC_CREATION_AGENT_ID, dto.getSpecCreationAgentId());
        saveSetting(Settings.KEY_INITIALIZATION_AGENT_ID, dto.getInitializationAgentId());
        saveSetting(Settings.KEY_CODING_AGENT_ID, dto.getCodingAgentId());

        log.info("Updated agent defaults");
        return getAgentDefaults();
    }

    /**
     * Get all provider information.
     */
    public List<ProviderInfoDTO> getProviders() {
        return Arrays.stream(LlmProviderType.values())
            .map(this::toProviderInfo)
            .collect(Collectors.toList());
    }

    // === Helper Methods ===

    private AiAgentConfigDTO toDTO(AiAgentConfig config) {
        List<ModelInfoDTO> cachedModels = null;
        if (config.getCachedModels() != null && !config.getCachedModels().isBlank()) {
            try {
                cachedModels = objectMapper.readValue(
                    config.getCachedModels(),
                    new TypeReference<List<ModelInfoDTO>>() {}
                );
            } catch (Exception e) {
                log.warn("Failed to parse cached models for {}", config.getName(), e);
            }
        }

        Map<String, Object> providerSettings = null;
        if (config.getProviderSettings() != null && !config.getProviderSettings().isBlank()) {
            try {
                providerSettings = objectMapper.readValue(
                    config.getProviderSettings(),
                    new TypeReference<Map<String, Object>>() {}
                );
            } catch (Exception e) {
                log.warn("Failed to parse provider settings for {}", config.getName(), e);
            }
        }

        return AiAgentConfigDTO.builder()
            .id(config.getId())
            .name(config.getName())
            .providerType(config.getProviderType())
            .endpointUrl(config.getEndpointUrl())
            .defaultModel(config.getDefaultModel())
            .cachedModels(cachedModels)
            .providerSettings(providerSettings)
            .enabled(config.getEnabled())
            .createdAt(config.getCreatedAt())
            .updatedAt(config.getUpdatedAt())
            .modelsLastFetched(config.getModelsLastFetched())
            .build();
    }

    private ProviderInfoDTO toProviderInfo(LlmProviderType type) {
        List<CredentialFieldDTO> credentialFields = type.getRequiredCredentialFields().stream()
            .map(field -> CredentialFieldDTO.builder()
                .name(field.getName())
                .label(field.getLabel())
                .type(field.getType())
                .required(field.isRequired())
                .placeholder(field.getPlaceholder())
                .build())
            .collect(Collectors.toList());

        return ProviderInfoDTO.builder()
            .type(type)
            .displayName(type.getDisplayName())
            .description(type.getDescription())
            .requiredCredentials(credentialFields)
            .supportsCustomEndpoint(type.isSupportsCustomEndpoint())
            .supportsStreaming(type.isSupportsStreaming())
            .build();
    }

    private Map<String, String> decryptCredentials(AiAgentConfig config) {
        if (config.getEncryptedCredentials() == null || config.getEncryptedCredentials().isBlank()) {
            return Map.of();
        }

        try {
            String decrypted = encryptionService.decrypt(config.getEncryptedCredentials());
            return objectMapper.readValue(decrypted, new TypeReference<Map<String, String>>() {});
        } catch (Exception e) {
            log.error("Failed to decrypt credentials for agent: {}", config.getName(), e);
            throw new RuntimeException("Failed to decrypt credentials", e);
        }
    }

    private Long getSettingAsLong(String key) {
        return settingsRepository.findByKey(key)
            .filter(s -> s.getValue() != null && !s.getValue().isBlank())
            .map(s -> {
                try {
                    return Long.parseLong(s.getValue());
                } catch (NumberFormatException e) {
                    return null;
                }
            })
            .orElse(null);
    }

    private void saveSetting(String key, Long value) {
        if (value == null) {
            settingsRepository.findByKey(key).ifPresent(settingsRepository::delete);
        } else {
            Settings setting = settingsRepository.findByKey(key)
                .orElse(Settings.builder().key(key).build());
            setting.setValue(String.valueOf(value));
            settingsRepository.save(setting);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
