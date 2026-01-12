package org.me.retrocoder.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a conversation in the assistant chat.
 * Stored in the project's assistant.db SQLite database.
 */
@Entity
@Table(name = "conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", nullable = false)
    private String projectName;

    @Column(name = "title")
    private String title;

    @Builder.Default
    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConversationMessage> messages = new ArrayList<>();

    /**
     * Add a message to this conversation.
     */
    public void addMessage(String role, String content) {
        ConversationMessage message = ConversationMessage.builder()
                .conversation(this)
                .role(role)
                .content(content)
                .build();
        messages.add(message);
        updatedAt = Instant.now();
    }

    /**
     * Get the message count.
     */
    public int getMessageCount() {
        return messages != null ? messages.size() : 0;
    }
}
