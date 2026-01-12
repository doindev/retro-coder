package org.me.retrocoder.controller;

import java.util.List;

import org.me.retrocoder.model.dto.FeatureCreateDTO;
import org.me.retrocoder.model.dto.FeatureDTO;
import org.me.retrocoder.model.dto.FeatureListResponseDTO;
import org.me.retrocoder.model.dto.FeatureStatsDTO;
import org.me.retrocoder.service.FeatureService;
import org.me.retrocoder.service.RegistryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for feature management.
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectName}/features")
@RequiredArgsConstructor
public class FeatureController {

    private final FeatureService featureService;
    private final RegistryService registryService;

    /**
     * List all features grouped by status.
     */
    @GetMapping
    public ResponseEntity<FeatureListResponseDTO> listFeatures(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok(featureService.getFeaturesByStatus(projectName));
        } catch (Exception e) {
            log.error("Failed to list features for project: {}", projectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get feature statistics.
     */
    @GetMapping("/stats")
    public ResponseEntity<FeatureStatsDTO> getStats(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(featureService.getStats(projectName));
    }

    /**
     * Get next pending feature.
     */
    @GetMapping("/next")
    public ResponseEntity<FeatureDTO> getNext(@PathVariable String projectName) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        return featureService.getNext(projectName)
            .map(ResponseEntity::ok)
            .orElse(ResponseEntity.noContent().build());
    }

    /**
     * Get random passing features for regression testing.
     */
    @GetMapping("/regression")
    public ResponseEntity<List<FeatureDTO>> getForRegression(
            @PathVariable String projectName,
            @RequestParam(defaultValue = "3") int limit) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(featureService.getForRegression(projectName, limit));
    }

    /**
     * Create a new feature.
     */
    @PostMapping
    public ResponseEntity<FeatureDTO> createFeature(
            @PathVariable String projectName,
            @Valid @RequestBody FeatureCreateDTO dto) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            FeatureDTO feature = featureService.createFeature(projectName, dto);
            return ResponseEntity.status(HttpStatus.CREATED).body(feature);
        } catch (Exception e) {
            log.error("Failed to create feature for project: {}", projectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Create features in bulk.
     */
    @PostMapping("/bulk")
    public ResponseEntity<List<FeatureDTO>> createBulk(
            @PathVariable String projectName,
            @RequestBody List<FeatureCreateDTO> features) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            List<FeatureDTO> created = featureService.createBulk(projectName, features);
            return ResponseEntity.status(HttpStatus.CREATED).body(created);
        } catch (Exception e) {
            log.error("Failed to bulk create features for project: {}", projectName, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mark feature as passing.
     */
    @PostMapping("/{featureId}/passing")
    public ResponseEntity<FeatureDTO> markPassing(
            @PathVariable String projectName,
            @PathVariable Long featureId) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok(featureService.markPassing(projectName, featureId));
        } catch (Exception e) {
            log.error("Failed to mark feature {} as passing", featureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Mark feature as in progress.
     */
    @PostMapping("/{featureId}/in-progress")
    public ResponseEntity<FeatureDTO> markInProgress(
            @PathVariable String projectName,
            @PathVariable Long featureId) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok(featureService.markInProgress(projectName, featureId));
        } catch (Exception e) {
            log.error("Failed to mark feature {} as in progress", featureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Clear in-progress status.
     */
    @DeleteMapping("/{featureId}/in-progress")
    public ResponseEntity<FeatureDTO> clearInProgress(
            @PathVariable String projectName,
            @PathVariable Long featureId) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok(featureService.clearInProgress(projectName, featureId));
        } catch (Exception e) {
            log.error("Failed to clear in-progress for feature {}", featureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Skip feature (move to end of queue).
     */
    @PostMapping("/{featureId}/skip")
    public ResponseEntity<FeatureDTO> skipFeature(
            @PathVariable String projectName,
            @PathVariable Long featureId) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        try {
            return ResponseEntity.ok(featureService.skip(projectName, featureId));
        } catch (Exception e) {
            log.error("Failed to skip feature {}", featureId, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Delete a feature.
     */
    @DeleteMapping("/{featureId}")
    public ResponseEntity<Void> deleteFeature(
            @PathVariable String projectName,
            @PathVariable Long featureId) {
        if (!registryService.projectExists(projectName)) {
            return ResponseEntity.notFound().build();
        }

        if (featureService.deleteFeature(projectName, featureId)) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}
