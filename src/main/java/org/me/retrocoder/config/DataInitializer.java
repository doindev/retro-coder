package org.me.retrocoder.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.model.AiAgentConfig;
import org.me.retrocoder.model.LlmProviderType;
import org.me.retrocoder.model.Settings;
import org.me.retrocoder.repository.AiAgentConfigRepository;
import org.me.retrocoder.repository.SettingsRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Initializes default data on application startup.
 * Creates the default Claude Code CLI agent if it doesn't exist.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {

    public static final String DEFAULT_AGENT_NAME = "Claude Code CLI";

    private final AiAgentConfigRepository agentRepository;
    private final SettingsRepository settingsRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createDefaultClaudeCodeAgent();
    }

    /**
     * Create the default Claude Code CLI agent if it doesn't exist.
     * This agent uses the locally installed claude CLI tool and requires no credentials.
     */
    private void createDefaultClaudeCodeAgent() {
        if (agentRepository.existsByName(DEFAULT_AGENT_NAME)) {
            log.debug("Default Claude Code CLI agent already exists");
            return;
        }

        log.info("Creating default Claude Code CLI agent...");

        AiAgentConfig defaultAgent = AiAgentConfig.builder()
            .name(DEFAULT_AGENT_NAME)
            .providerType(LlmProviderType.CLAUDE_CODE)
            .defaultModel("claude-opus-4-5-20251101")
            .enabled(true)
            .createdAt(LocalDateTime.now())
            .updatedAt(LocalDateTime.now())
            .build();

        AiAgentConfig saved = agentRepository.save(defaultAgent);
        log.info("Created default Claude Code CLI agent with ID: {}", saved.getId());

        // Set as global default if no default is configured
        if (!settingsRepository.findByKey(Settings.KEY_DEFAULT_AI_AGENT_ID).isPresent()) {
            Settings defaultSetting = Settings.builder()
                .key(Settings.KEY_DEFAULT_AI_AGENT_ID)
                .value(String.valueOf(saved.getId()))
                .build();
            settingsRepository.save(defaultSetting);
            log.info("Set Claude Code CLI as the global default AI agent");
        }
    }
}
