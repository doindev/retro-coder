package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for bug investigation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugInvestigationResponseDTO {

    /**
     * The original bug description from the request.
     */
    private String description;

    /**
     * The root cause identified by the investigation.
     */
    private String rootCause;

    /**
     * List of files affected by the bug.
     */
    private List<String> affectedFiles;

    /**
     * Suggested steps to fix the bug.
     */
    private List<String> steps;

    /**
     * Additional notes from the investigation.
     */
    private String additionalNotes;

    /**
     * Suggested name for the bugfix feature.
     */
    private String suggestedName;

    /**
     * Whether the investigation was successful.
     */
    private boolean success;

    /**
     * Error message if investigation failed.
     */
    private String error;

    /**
     * Create a successful response.
     */
    public static BugInvestigationResponseDTO success(
            String description,
            String rootCause,
            List<String> affectedFiles,
            List<String> steps,
            String additionalNotes,
            String suggestedName) {
        return BugInvestigationResponseDTO.builder()
                .description(description)
                .rootCause(rootCause)
                .affectedFiles(affectedFiles)
                .steps(steps)
                .additionalNotes(additionalNotes)
                .suggestedName(suggestedName)
                .success(true)
                .build();
    }

    /**
     * Create a failed response.
     */
    public static BugInvestigationResponseDTO failure(String description, String error) {
        return BugInvestigationResponseDTO.builder()
                .description(description)
                .success(false)
                .error(error)
                .build();
    }
}
