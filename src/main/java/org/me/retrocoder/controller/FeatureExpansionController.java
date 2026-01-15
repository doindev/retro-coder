package org.me.retrocoder.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.model.dto.FeatureExpansionRequestDTO;
import org.me.retrocoder.model.dto.FeatureExpansionResponseDTO;
import org.me.retrocoder.service.FeatureExpansionService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for feature expansion.
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectName}/features")
@RequiredArgsConstructor
public class FeatureExpansionController {

    private final FeatureExpansionService featureExpansionService;
    private final RegistryService registryService;

    /**
     * Expand a feature description into a complete feature specification.
     *
     * POST /api/projects/{projectName}/features/expand
     *
     * Request body:
     * {
     *   "description": "Description of the feature..."
     * }
     *
     * Response:
     * {
     *   "name": "Feature name",
     *   "category": "Category",
     *   "description": "Enhanced description",
     *   "steps": ["Step 1", "Step 2"],
     *   "success": true
     * }
     */
    @PostMapping("/expand")
    public ResponseEntity<FeatureExpansionResponseDTO> expandFeature(
            @PathVariable String projectName,
            @Valid @RequestBody FeatureExpansionRequestDTO request) {

        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        log.info("Feature expansion requested for project: {}", projectName);

        try {
            FeatureExpansionResponseDTO response = featureExpansionService.expand(projectName, request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Feature expansion failed for project: {}", projectName, e);
            return ResponseEntity.ok(FeatureExpansionResponseDTO.failure(e.getMessage()));
        }
    }
}
