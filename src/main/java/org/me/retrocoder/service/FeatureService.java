package org.me.retrocoder.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.config.DatabaseConfig;
import org.me.retrocoder.model.dto.FeatureCreateDTO;
import org.me.retrocoder.model.dto.FeatureDTO;
import org.me.retrocoder.model.dto.FeatureListResponseDTO;
import org.me.retrocoder.model.dto.FeatureStatsDTO;
import org.me.retrocoder.websocket.WebSocketSessionManager;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Service for managing features in project-specific databases.
 * Uses direct JDBC for per-project SQLite database access.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FeatureService {

    private final RegistryService registryService;
    private final WebSocketSessionManager webSocketSessionManager;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get all features grouped by status.
     */
    public FeatureListResponseDTO getFeaturesByStatus(String projectName) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            List<FeatureDTO> pending = new ArrayList<>();
            List<FeatureDTO> inProgress = new ArrayList<>();
            List<FeatureDTO> done = new ArrayList<>();

            String sql = "SELECT * FROM features ORDER BY priority ASC";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    FeatureDTO feature = mapResultSetToDTO(rs);
                    if (feature.getPasses()) {
                        done.add(feature);
                    } else if (feature.getInProgress()) {
                        inProgress.add(feature);
                    } else {
                        pending.add(feature);
                    }
                }
            }

            return FeatureListResponseDTO.builder()
                .pending(pending)
                .inProgress(inProgress)
                .done(done)
                .build();
        } catch (Exception e) {
            log.error("Failed to get features for project: {}", projectName, e);
            throw new RuntimeException("Failed to get features", e);
        }
    }

    /**
     * Get feature statistics.
     */
    public FeatureStatsDTO getStats(String projectName) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            int passing = 0, inProgress = 0, total = 0;

            String sql = "SELECT passes, in_progress FROM features";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    total++;
                    if (rs.getBoolean("passes")) {
                        passing++;
                    } else if (rs.getBoolean("in_progress")) {
                        inProgress++;
                    }
                }
            }

            return FeatureStatsDTO.of(passing, inProgress, total);
        } catch (Exception e) {
            log.error("Failed to get stats for project: {}", projectName, e);
            throw new RuntimeException("Failed to get stats", e);
        }
    }

    /**
     * Get next pending feature.
     */
    public Optional<FeatureDTO> getNext(String projectName) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            String sql = "SELECT * FROM features WHERE passes = 0 AND in_progress = 0 ORDER BY priority ASC LIMIT 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToDTO(rs));
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to get next feature for project: {}", projectName, e);
            throw new RuntimeException("Failed to get next feature", e);
        }
    }

    /**
     * Get random passing features for regression testing.
     */
    public List<FeatureDTO> getForRegression(String projectName, int limit) {
        String projectPath = getProjectPath(projectName);
        limit = Math.max(1, Math.min(10, limit));

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            List<FeatureDTO> features = new ArrayList<>();
            String sql = "SELECT * FROM features WHERE passes = 1";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    features.add(mapResultSetToDTO(rs));
                }
            }

            // Shuffle and take limit
            Collections.shuffle(features);
            return features.subList(0, Math.min(limit, features.size()));
        } catch (Exception e) {
            log.error("Failed to get regression features for project: {}", projectName, e);
            throw new RuntimeException("Failed to get regression features", e);
        }
    }

    /**
     * Mark feature as passing.
     */
    public FeatureDTO markPassing(String projectName, Long featureId) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            String sql = "UPDATE features SET passes = 1, in_progress = 0 WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, featureId);
                pstmt.executeUpdate();
            }

            FeatureDTO feature = getFeatureById(conn, featureId);

            // Broadcast updates via WebSocket
            broadcastProgressUpdate(projectName, conn);
            webSocketSessionManager.broadcastFeatureUpdate(projectName, featureId, true);

            return feature;
        } catch (Exception e) {
            log.error("Failed to mark feature {} as passing", featureId, e);
            throw new RuntimeException("Failed to mark feature as passing", e);
        }
    }

    /**
     * Mark feature as in progress.
     */
    public FeatureDTO markInProgress(String projectName, Long featureId) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            String sql = "UPDATE features SET in_progress = 1 WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, featureId);
                pstmt.executeUpdate();
            }

            FeatureDTO feature = getFeatureById(conn, featureId);

            // Broadcast updates via WebSocket
            broadcastProgressUpdate(projectName, conn);
            webSocketSessionManager.broadcastFeatureUpdate(projectName, featureId, false);

            return feature;
        } catch (Exception e) {
            log.error("Failed to mark feature {} as in progress", featureId, e);
            throw new RuntimeException("Failed to mark feature as in progress", e);
        }
    }

    /**
     * Broadcast progress update via WebSocket.
     */
    private void broadcastProgressUpdate(String projectName, Connection conn) {
        try {
            int passing = 0;
            int inProgress = 0;
            int total = 0;

            String sql = "SELECT passes, in_progress FROM features";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                while (rs.next()) {
                    total++;
                    if (rs.getBoolean("passes")) {
                        passing++;
                    } else if (rs.getBoolean("in_progress")) {
                        inProgress++;
                    }
                }
            }

            webSocketSessionManager.broadcastProgress(projectName, passing, inProgress, total);
        } catch (Exception e) {
            log.warn("Failed to broadcast progress update for {}", projectName, e);
        }
    }

    /**
     * Clear in-progress status.
     */
    public FeatureDTO clearInProgress(String projectName, Long featureId) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            String sql = "UPDATE features SET in_progress = 0 WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, featureId);
                pstmt.executeUpdate();
            }

            FeatureDTO feature = getFeatureById(conn, featureId);

            // Broadcast updates via WebSocket
            broadcastProgressUpdate(projectName, conn);
            webSocketSessionManager.broadcastFeatureUpdate(projectName, featureId, false);

            return feature;
        } catch (Exception e) {
            log.error("Failed to clear in-progress for feature {}", featureId, e);
            throw new RuntimeException("Failed to clear in-progress", e);
        }
    }

    /**
     * Skip feature (move to end of queue).
     */
    public FeatureDTO skip(String projectName, Long featureId) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            // Get max priority
            int maxPriority = 0;
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT MAX(priority) as max_priority FROM features")) {
                if (rs.next()) {
                    maxPriority = rs.getInt("max_priority");
                }
            }

            // Update priority
            String sql = "UPDATE features SET priority = ? WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setInt(1, maxPriority + 1);
                pstmt.setLong(2, featureId);
                pstmt.executeUpdate();
            }

            return getFeatureById(conn, featureId);
        } catch (Exception e) {
            log.error("Failed to skip feature {}", featureId, e);
            throw new RuntimeException("Failed to skip feature", e);
        }
    }

    /**
     * Create a single feature.
     */
    public FeatureDTO createFeature(String projectName, FeatureCreateDTO dto) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            int priority = dto.getPriority() != null ? dto.getPriority() : getNextPriority(conn);
            String steps = dto.getSteps() != null ? objectMapper.writeValueAsString(dto.getSteps()) : "[]";

            String sql = "INSERT INTO features (priority, category, name, description, steps, passes, in_progress) VALUES (?, ?, ?, ?, ?, 0, 0)";
            try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                pstmt.setInt(1, priority);
                pstmt.setString(2, dto.getCategory());
                pstmt.setString(3, dto.getName());
                pstmt.setString(4, dto.getDescription());
                pstmt.setString(5, steps);
                pstmt.executeUpdate();

                try (ResultSet keys = pstmt.getGeneratedKeys()) {
                    if (keys.next()) {
                        return getFeatureById(conn, keys.getLong(1));
                    }
                }
            }
            throw new RuntimeException("Failed to get generated key");
        } catch (Exception e) {
            log.error("Failed to create feature for project: {}", projectName, e);
            throw new RuntimeException("Failed to create feature", e);
        }
    }

    /**
     * Create multiple features in bulk.
     */
    public List<FeatureDTO> createBulk(String projectName, List<FeatureCreateDTO> features) {
        String projectPath = getProjectPath(projectName);
        List<FeatureDTO> created = new ArrayList<>();

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            conn.setAutoCommit(false);
            try {
                int priority = getNextPriority(conn);

                String sql = "INSERT INTO features (priority, category, name, description, steps, passes, in_progress) VALUES (?, ?, ?, ?, ?, 0, 0)";
                try (PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    for (FeatureCreateDTO dto : features) {
                        String steps = dto.getSteps() != null ? objectMapper.writeValueAsString(dto.getSteps()) : "[]";
                        pstmt.setInt(1, priority++);
                        pstmt.setString(2, dto.getCategory());
                        pstmt.setString(3, dto.getName());
                        pstmt.setString(4, dto.getDescription());
                        pstmt.setString(5, steps);
                        pstmt.executeUpdate();

                        try (ResultSet keys = pstmt.getGeneratedKeys()) {
                            if (keys.next()) {
                                created.add(getFeatureById(conn, keys.getLong(1)));
                            }
                        }
                    }
                }
                conn.commit();

                // Broadcast progress update after bulk creation
                broadcastProgressUpdate(projectName, conn);

            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        } catch (Exception e) {
            log.error("Failed to bulk create features for project: {}", projectName, e);
            throw new RuntimeException("Failed to bulk create features", e);
        }

        return created;
    }

    /**
     * Delete a feature.
     */
    public boolean deleteFeature(String projectName, Long featureId) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            String sql = "DELETE FROM features WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
                pstmt.setLong(1, featureId);
                return pstmt.executeUpdate() > 0;
            }
        } catch (Exception e) {
            log.error("Failed to delete feature {}", featureId, e);
            throw new RuntimeException("Failed to delete feature", e);
        }
    }

    /**
     * Check if project has any features.
     */
    public boolean hasFeatures(String projectName) {
        String projectPath = getProjectPath(projectName);

        try (Connection conn = DatabaseConfig.getProjectDatabaseConnection(projectPath)) {
            String sql = "SELECT COUNT(*) as count FROM features";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                if (rs.next()) {
                    return rs.getInt("count") > 0;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to check features for project: {}", projectName, e);
            return false;
        }
    }

    private String getProjectPath(String projectName) {
        return registryService.getProjectPath(projectName)
            .orElseThrow(() -> new IllegalArgumentException("Project not found: " + projectName));
    }

    private int getNextPriority(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COALESCE(MAX(priority), 0) + 1 as next FROM features")) {
            if (rs.next()) {
                return rs.getInt("next");
            }
            return 1;
        }
    }

    private FeatureDTO getFeatureById(Connection conn, Long id) throws SQLException {
        String sql = "SELECT * FROM features WHERE id = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1, id);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapResultSetToDTO(rs);
                }
            }
        }
        throw new RuntimeException("Feature not found: " + id);
    }

    private FeatureDTO mapResultSetToDTO(ResultSet rs) throws SQLException {
        List<String> steps = List.of();
        try {
            String stepsJson = rs.getString("steps");
            if (stepsJson != null && !stepsJson.isEmpty()) {
                steps = objectMapper.readValue(stepsJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
            }
        } catch (Exception e) {
            log.warn("Failed to parse steps JSON", e);
        }

        return FeatureDTO.builder()
            .id(rs.getLong("id"))
            .priority(rs.getInt("priority"))
            .category(rs.getString("category"))
            .name(rs.getString("name"))
            .description(rs.getString("description"))
            .steps(steps)
            .passes(rs.getBoolean("passes"))
            .inProgress(rs.getBoolean("in_progress"))
            .build();
    }
}
