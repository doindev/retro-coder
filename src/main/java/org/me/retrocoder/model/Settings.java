package org.me.retrocoder.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Key-value settings entity for global application settings.
 * Stored in the global registry database (~/.autocoder/registry.db).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "settings")
public class Settings {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(name = "value", nullable = false)
    private String value;

    /**
     * Common setting keys.
     */
    public static final String KEY_YOLO_MODE = "yolo_mode";
    public static final String KEY_DEFAULT_MODEL = "model";
    public static final String KEY_DEFAULT_INTEGRATION_MODE = "integration_mode";
    public static final String KEY_PLAYWRIGHT_HEADLESS = "playwright_headless";

    /**
     * Get boolean value.
     */
    public boolean getBooleanValue() {
        return "true".equalsIgnoreCase(value) || "1".equals(value);
    }

    /**
     * Create a boolean setting.
     */
    public static Settings booleanSetting(String key, boolean value) {
        return Settings.builder()
            .key(key)
            .value(String.valueOf(value))
            .build();
    }

    /**
     * Create a string setting.
     */
    public static Settings stringSetting(String key, String value) {
        return Settings.builder()
            .key(key)
            .value(value != null ? value : "")
            .build();
    }
}
