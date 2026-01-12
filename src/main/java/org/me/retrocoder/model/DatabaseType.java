package org.me.retrocoder.model;

/**
 * Supported database types.
 */
public enum DatabaseType {
    /**
     * SQLite database (default).
     * Each project has its own features.db file.
     */
    SQLITE,

    /**
     * H2 embedded database.
     * Single database for all projects.
     */
    H2,

    /**
     * PostgreSQL database.
     * External database server for production.
     */
    POSTGRESQL;

    public static DatabaseType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return SQLITE;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SQLITE;
        }
    }
}
