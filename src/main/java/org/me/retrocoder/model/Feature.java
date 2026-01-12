package org.me.retrocoder.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Feature entity representing a test case/feature to be implemented.
 * Stored in project-specific SQLite databases (features.db).
 */
@Data
@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "features", indexes = {
    @Index(name = "idx_features_priority", columnList = "priority"),
    @Index(name = "idx_features_passes", columnList = "passes"),
    @Index(name = "idx_features_in_progress", columnList = "in_progress")
})
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Builder.Default
    @Column(nullable = false)
    private Integer priority = 999;

    @Column(length = 100, nullable = false)
    private String category;

    @Column(length = 255, nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    /**
     * JSON-serialized list of test steps.
     */
    @Column(columnDefinition = "TEXT")
    private String steps;

    @Builder.Default
    @Column(name = "passes", nullable = false)
    private Boolean passes = false;

    @Builder.Default
    @Column(name = "in_progress", nullable = false)
    private Boolean inProgress = false;

    /**
     * Convert steps JSON string to List.
     */
    public List<String> getStepsList() {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(steps, mapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * Set steps from a List.
     */
    public void setStepsList(List<String> stepsList) {
        if (stepsList == null || stepsList.isEmpty()) {
            this.steps = "[]";
            return;
        }
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            this.steps = mapper.writeValueAsString(stepsList);
        } catch (Exception e) {
            this.steps = "[]";
        }
    }
}
