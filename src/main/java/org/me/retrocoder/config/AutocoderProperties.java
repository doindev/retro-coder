package org.me.retrocoder.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for Autocoder application.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "autocoder")
public class AutocoderProperties {

    private Database database = new Database();
    private Claude claude = new Claude();
    private Agent agent = new Agent();
    private Webhook webhook = new Webhook();
    private Security security = new Security();
    private List<Model> models = new ArrayList<>();

    @Data
    public static class Database {
        private String type = "sqlite";
        private String registryPath;
        private boolean enabled = true;
    }

    @Data
    public static class Claude {
        private String defaultMode = "CLI_WRAPPER";
        private String cliCommand = "claude";
        private String apiKey;
        private String defaultModel = "claude-opus-4-5-20251101";
    }

    @Data
    public static class Agent {
        private long autoContinueDelay = 3000;
        private int maxIterations = -1;
        private boolean playwrightHeadless = false;
    }

    @Data
    public static class Webhook {
        private String n8nUrl;
    }

    @Data
    public static class Security {
        private boolean localhostOnly = true;
    }

    @Data
    public static class Model {
        private String id;
        private String name;
        private boolean isDefault = false;
    }
}
