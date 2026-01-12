package org.me.retrocoder.config;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;

/**
 * Database configuration supporting multiple database types.
 * Default: SQLite with per-project databases
 */
@Slf4j
@Configuration
public class DatabaseConfig {

    @Value("${autocoder.database.type:sqlite}")
    private String databaseType;

    @Value("${autocoder.database.registry-path:${user.home}/.autocoder/registry.db}")
    private String registryPath;

    /**
     * Initialize the autocoder directory and registry database on startup.
     * Only active when database type is sqlite.
     */
    @Bean
    @ConditionalOnProperty(name = "autocoder.database.type", havingValue = "sqlite", matchIfMissing = true)
    public DatabaseInitializer databaseInitializer() {
        return new DatabaseInitializer(registryPath);
    }

    public static class DatabaseInitializer {
        private final String registryPath;

        public DatabaseInitializer(String registryPath) {
            this.registryPath = registryPath;
            initialize();
        }

        private void initialize() {
            try {
                // Ensure .autocoder directory exists
                Path autocoderDir = Paths.get(System.getProperty("user.home"), ".autocoder");
                if (!Files.exists(autocoderDir)) {
                    Files.createDirectories(autocoderDir);
                    log.info("Created autocoder directory: {}", autocoderDir);
                }

                // Initialize registry database
                initializeRegistryDatabase();
            } catch (Exception e) {
                log.error("Failed to initialize database", e);
                throw new RuntimeException("Database initialization failed", e);
            }
        }

        private void initializeRegistryDatabase() {
            String url = "jdbc:sqlite:" + registryPath;
            try (Connection conn = DriverManager.getConnection(url);
                 Statement stmt = conn.createStatement()) {

                // Create projects table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS projects (
                        name TEXT PRIMARY KEY,
                        path TEXT NOT NULL,
                        integration_mode TEXT DEFAULT 'CLI_WRAPPER',
                        created_at TEXT DEFAULT CURRENT_TIMESTAMP
                    )
                """);

                // Create settings table
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS settings (
                        key TEXT PRIMARY KEY,
                        value TEXT NOT NULL
                    )
                """);

                log.info("Registry database initialized at: {}", registryPath);
            } catch (Exception e) {
                log.error("Failed to initialize registry database", e);
                throw new RuntimeException("Registry database initialization failed", e);
            }
        }
    }

    /**
     * Create a SQLite connection for a specific project's features database.
     */
    public static Connection getProjectDatabaseConnection(String projectPath) throws Exception {
        String dbPath = Paths.get(projectPath, "features.db").toString();
        String url = "jdbc:sqlite:" + dbPath;
        Connection conn = DriverManager.getConnection(url);

        // Initialize features table if not exists
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS features (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    priority INTEGER NOT NULL DEFAULT 999,
                    category TEXT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT NOT NULL,
                    steps TEXT,
                    passes INTEGER DEFAULT 0,
                    in_progress INTEGER DEFAULT 0
                )
            """);

            // Create indexes
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_priority ON features(priority)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_passes ON features(passes)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_features_in_progress ON features(in_progress)");
        }

        return conn;
    }
}
