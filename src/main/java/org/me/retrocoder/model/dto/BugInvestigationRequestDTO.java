package org.me.retrocoder.model.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for bug investigation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BugInvestigationRequestDTO {

    @NotBlank(message = "Bug description is required")
    private String description;
}
