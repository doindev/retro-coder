package org.me.retrocoder.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.model.dto.BugInvestigationRequestDTO;
import org.me.retrocoder.model.dto.BugInvestigationResponseDTO;
import org.me.retrocoder.service.BugInvestigationService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for bug investigation.
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectName}/bugs")
@RequiredArgsConstructor
public class BugInvestigationController {

    private final BugInvestigationService bugInvestigationService;
    private final RegistryService registryService;

    /**
     * Investigate a bug and return suggested fix steps.
     *
     * POST /api/projects/{projectName}/bugs/investigate
     *
     * Request body:
     * {
     *   "description": "Description of the bug..."
     * }
     *
     * Response:
     * {
     *   "description": "Original description",
     *   "rootCause": "What's causing the bug",
     *   "affectedFiles": ["file1.ts", "file2.java"],
     *   "steps": ["Step 1", "Step 2"],
     *   "additionalNotes": "Extra info",
     *   "suggestedName": "Fix: the issue",
     *   "success": true
     * }
     */
    @PostMapping("/investigate")
    public ResponseEntity<BugInvestigationResponseDTO> investigateBug(
            @PathVariable String projectName,
            @Valid @RequestBody BugInvestigationRequestDTO request) {

        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        log.info("Bug investigation requested for project: {}", projectName);

        try {
            BugInvestigationResponseDTO response = bugInvestigationService.investigate(projectName, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Bug investigation failed for project: {}", projectName, e);
            return ResponseEntity.ok(
                    BugInvestigationResponseDTO.failure(request.getDescription(), e.getMessage())
            );
        }
    }
}
