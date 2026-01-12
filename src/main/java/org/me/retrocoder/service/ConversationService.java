package org.me.retrocoder.service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.me.retrocoder.model.Conversation;
import org.me.retrocoder.model.ConversationMessage;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service for managing assistant conversations.
 * Uses per-project SQLite database (assistant.db).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final RegistryService registryService;

    private static final String CREATE_TABLES_SQL = """
            CREATE TABLE IF NOT EXISTS conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                project_name TEXT NOT NULL,
                title TEXT,
                created_at TEXT NOT NULL,
                updated_at TEXT NOT NULL
            );

            CREATE TABLE IF NOT EXISTS conversation_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                conversation_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT,
                timestamp TEXT NOT NULL,
                FOREIGN KEY (conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
            );

            CREATE INDEX IF NOT EXISTS idx_messages_conversation ON conversation_messages(conversation_id);
            """;

    /**
     * Get connection to project's assistant database.
     */
    private Connection getConnection(String projectPath) throws SQLException {
        Path dbPath = Paths.get(projectPath, "assistant.db");
        String url = "jdbc:sqlite:" + dbPath.toAbsolutePath();
        Connection conn = DriverManager.getConnection(url);

        // Initialize tables if needed
        try (Statement stmt = conn.createStatement()) {
            for (String sql : CREATE_TABLES_SQL.split(";")) {
                if (!sql.trim().isEmpty()) {
                    stmt.execute(sql.trim());
                }
            }
        }

        return conn;
    }

    /**
     * List all conversations for a project.
     */
    public List<Conversation> getConversations(String projectName) {
        Optional<String> pathOpt = registryService.getProjectPath(projectName);
        if (pathOpt.isEmpty()) {
            return List.of();
        }

        String projectPath = pathOpt.get();
        List<Conversation> conversations = new ArrayList<>();

        try (Connection conn = getConnection(projectPath)) {
            String sql = """
                    SELECT c.id, c.project_name, c.title, c.created_at, c.updated_at,
                           (SELECT COUNT(*) FROM conversation_messages WHERE conversation_id = c.id) as message_count
                    FROM conversations c
                    WHERE c.project_name = ?
                    ORDER BY c.updated_at DESC
                    """;

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, projectName);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    Conversation conv = Conversation.builder()
                            .id(rs.getLong("id"))
                            .projectName(rs.getString("project_name"))
                            .title(rs.getString("title"))
                            .createdAt(Instant.parse(rs.getString("created_at")))
                            .updatedAt(Instant.parse(rs.getString("updated_at")))
                            .build();
                    conversations.add(conv);
                }
            }
        } catch (SQLException e) {
            log.error("Failed to get conversations for {}", projectName, e);
        }

        return conversations;
    }

    /**
     * Get a specific conversation with all messages.
     */
    public Optional<Conversation> getConversation(String projectName, Long conversationId) {
        Optional<String> pathOpt = registryService.getProjectPath(projectName);
        if (pathOpt.isEmpty()) {
            return Optional.empty();
        }

        String projectPath = pathOpt.get();

        try (Connection conn = getConnection(projectPath)) {
            // Get conversation
            String convSql = "SELECT id, project_name, title, created_at, updated_at FROM conversations WHERE id = ?";
            Conversation conversation = null;

            try (PreparedStatement stmt = conn.prepareStatement(convSql)) {
                stmt.setLong(1, conversationId);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    conversation = Conversation.builder()
                            .id(rs.getLong("id"))
                            .projectName(rs.getString("project_name"))
                            .title(rs.getString("title"))
                            .createdAt(Instant.parse(rs.getString("created_at")))
                            .updatedAt(Instant.parse(rs.getString("updated_at")))
                            .messages(new ArrayList<>())
                            .build();
                }
            }

            if (conversation == null) {
                return Optional.empty();
            }

            // Get messages
            String msgSql = "SELECT id, role, content, timestamp FROM conversation_messages WHERE conversation_id = ? ORDER BY timestamp";
            try (PreparedStatement stmt = conn.prepareStatement(msgSql)) {
                stmt.setLong(1, conversationId);
                ResultSet rs = stmt.executeQuery();

                while (rs.next()) {
                    ConversationMessage msg = ConversationMessage.builder()
                            .id(rs.getLong("id"))
                            .conversation(conversation)
                            .role(rs.getString("role"))
                            .content(rs.getString("content"))
                            .timestamp(Instant.parse(rs.getString("timestamp")))
                            .build();
                    conversation.getMessages().add(msg);
                }
            }

            return Optional.of(conversation);

        } catch (SQLException e) {
            log.error("Failed to get conversation {} for {}", conversationId, projectName, e);
            return Optional.empty();
        }
    }

    /**
     * Create a new conversation.
     */
    public Conversation createConversation(String projectName) {
        Optional<String> pathOpt = registryService.getProjectPath(projectName);
        if (pathOpt.isEmpty()) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        String projectPath = pathOpt.get();
        Instant now = Instant.now();

        try (Connection conn = getConnection(projectPath)) {
            String sql = "INSERT INTO conversations (project_name, title, created_at, updated_at) VALUES (?, ?, ?, ?)";

            try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, projectName);
                stmt.setString(2, null); // title set later based on first message
                stmt.setString(3, now.toString());
                stmt.setString(4, now.toString());
                stmt.executeUpdate();

                ResultSet rs = stmt.getGeneratedKeys();
                if (rs.next()) {
                    return Conversation.builder()
                            .id(rs.getLong(1))
                            .projectName(projectName)
                            .createdAt(now)
                            .updatedAt(now)
                            .messages(new ArrayList<>())
                            .build();
                }
            }

            throw new RuntimeException("Failed to create conversation");

        } catch (SQLException e) {
            log.error("Failed to create conversation for {}", projectName, e);
            throw new RuntimeException("Failed to create conversation", e);
        }
    }

    /**
     * Delete a conversation.
     */
    public boolean deleteConversation(String projectName, Long conversationId) {
        Optional<String> pathOpt = registryService.getProjectPath(projectName);
        if (pathOpt.isEmpty()) {
            return false;
        }

        String projectPath = pathOpt.get();

        try (Connection conn = getConnection(projectPath)) {
            // Delete messages first (foreign key)
            String deleteMsgSql = "DELETE FROM conversation_messages WHERE conversation_id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteMsgSql)) {
                stmt.setLong(1, conversationId);
                stmt.executeUpdate();
            }

            // Delete conversation
            String deleteConvSql = "DELETE FROM conversations WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(deleteConvSql)) {
                stmt.setLong(1, conversationId);
                return stmt.executeUpdate() > 0;
            }

        } catch (SQLException e) {
            log.error("Failed to delete conversation {} for {}", conversationId, projectName, e);
            return false;
        }
    }

    /**
     * Add a message to a conversation.
     */
    public void addMessage(String projectName, Long conversationId, String role, String content) {
        Optional<String> pathOpt = registryService.getProjectPath(projectName);
        if (pathOpt.isEmpty()) {
            throw new IllegalArgumentException("Project not found: " + projectName);
        }

        String projectPath = pathOpt.get();
        Instant now = Instant.now();

        try (Connection conn = getConnection(projectPath)) {
            // Insert message
            String insertSql = "INSERT INTO conversation_messages (conversation_id, role, content, timestamp) VALUES (?, ?, ?, ?)";
            try (PreparedStatement stmt = conn.prepareStatement(insertSql)) {
                stmt.setLong(1, conversationId);
                stmt.setString(2, role);
                stmt.setString(3, content);
                stmt.setString(4, now.toString());
                stmt.executeUpdate();
            }

            // Update conversation updated_at and optionally title
            String updateSql = "UPDATE conversations SET updated_at = ? WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(updateSql)) {
                stmt.setString(1, now.toString());
                stmt.setLong(2, conversationId);
                stmt.executeUpdate();
            }

            // Set title from first user message if not set
            if ("user".equals(role)) {
                String checkTitleSql = "SELECT title FROM conversations WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkTitleSql)) {
                    stmt.setLong(1, conversationId);
                    ResultSet rs = stmt.executeQuery();
                    if (rs.next() && rs.getString("title") == null) {
                        // Set title to first 50 chars of first user message
                        String title = content.length() > 50 ? content.substring(0, 50) + "..." : content;
                        String setTitleSql = "UPDATE conversations SET title = ? WHERE id = ?";
                        try (PreparedStatement titleStmt = conn.prepareStatement(setTitleSql)) {
                            titleStmt.setString(1, title);
                            titleStmt.setLong(2, conversationId);
                            titleStmt.executeUpdate();
                        }
                    }
                }
            }

        } catch (SQLException e) {
            log.error("Failed to add message to conversation {} for {}", conversationId, projectName, e);
            throw new RuntimeException("Failed to add message", e);
        }
    }
}
