package org.me.retrocoder.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Entity representing an AI agent configuration.
 * Stores provider type, encrypted credentials, model selection, and settings.
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "ai_agent_configs")
public class AiAgentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * User-friendly name for this configuration.
     */
    @Column(nullable = false, unique = true, length = 100)
    private String name;

    /**
     * The LLM provider type.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "provider_type", nullable = false, length = 50)
    private LlmProviderType providerType;

    /**
     * Encrypted credentials JSON.
     * Format depends on provider, e.g., {"apiKey": "..."} or {"accessKeyId": "...", "secretAccessKey": "..."}
     */
    @Column(name = "encrypted_credentials", columnDefinition = "TEXT")
    private String encryptedCredentials;

    /**
     * Custom endpoint URL (for self-hosted or custom providers).
     */
    @Column(name = "endpoint_url", length = 500)
    private String endpointUrl;

    /**
     * Selected model ID/name for this configuration.
     */
    @Column(name = "default_model", length = 200)
    private String defaultModel;

    /**
     * Cached list of available models (JSON array).
     * Refreshed periodically or on demand.
     */
    @Column(name = "cached_models", columnDefinition = "TEXT")
    private String cachedModels;

    /**
     * Provider-specific settings (JSON object).
     * Examples: temperature, max_tokens, region, etc.
     */
    @Column(name = "provider_settings", columnDefinition = "TEXT")
    private String providerSettings;

    /**
     * Whether this configuration is enabled.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean enabled = true;

    /**
     * When this configuration was created.
     */
    @Column(name = "created_at")
    private LocalDateTime createdAt;

    /**
     * When this configuration was last updated.
     */
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * When the model list was last fetched.
     */
    @Column(name = "models_last_fetched")
    private LocalDateTime modelsLastFetched;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
