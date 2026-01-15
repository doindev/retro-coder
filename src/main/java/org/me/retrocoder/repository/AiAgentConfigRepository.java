package org.me.retrocoder.repository;

import org.me.retrocoder.model.AiAgentConfig;
import org.me.retrocoder.model.LlmProviderType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for AI agent configurations.
 */
@Repository
public interface AiAgentConfigRepository extends JpaRepository<AiAgentConfig, Long> {

    /**
     * Find an agent config by its unique name.
     */
    Optional<AiAgentConfig> findByName(String name);

    /**
     * Check if an agent config with the given name exists.
     */
    boolean existsByName(String name);

    /**
     * Find all enabled agent configs ordered by name.
     */
    List<AiAgentConfig> findByEnabledTrueOrderByNameAsc();

    /**
     * Find all agent configs ordered by name.
     */
    List<AiAgentConfig> findAllByOrderByNameAsc();

    /**
     * Find all agent configs by provider type.
     */
    List<AiAgentConfig> findByProviderTypeOrderByNameAsc(LlmProviderType providerType);

    /**
     * Find all enabled agent configs by provider type.
     */
    List<AiAgentConfig> findByProviderTypeAndEnabledTrueOrderByNameAsc(LlmProviderType providerType);
}
