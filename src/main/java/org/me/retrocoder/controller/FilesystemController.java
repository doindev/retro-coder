package org.me.retrocoder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.me.retrocoder.model.dto.DirectoryListResponseDTO;
import org.me.retrocoder.service.FilesystemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST controller for filesystem operations.
 */
@Slf4j
@RestController
@RequestMapping("/api/filesystem")
@RequiredArgsConstructor
public class FilesystemController {

    private final FilesystemService filesystemService;

    /**
     * List directory contents.
     */
    @GetMapping("/list")
    public ResponseEntity<DirectoryListResponseDTO> listDirectory(
            @RequestParam(required = false) String path,
            @RequestParam(defaultValue = "false") boolean showHidden) {
        try {
            String targetPath = path != null && !path.isEmpty()
                ? path : filesystemService.getUserHome();

            DirectoryListResponseDTO response = filesystemService.listDirectory(targetPath, showHidden);
            return ResponseEntity.ok(response);
        } catch (SecurityException e) {
            log.warn("Security violation: {}", e.getMessage());
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            log.warn("Invalid path: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        } catch (Exception e) {
            log.error("Error listing directory", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Validate a path.
     */
    @GetMapping("/validate")
    public ResponseEntity<Map<String, Object>> validatePath(@RequestParam String path) {
        boolean valid = filesystemService.validatePath(path);
        return ResponseEntity.ok(Map.of(
            "valid", valid,
            "path", path
        ));
    }

    /**
     * Create a directory.
     */
    @PostMapping("/create-dir")
    public ResponseEntity<Map<String, Object>> createDirectory(@RequestBody Map<String, String> request) {
        String path = request.get("path");
        if (path == null || path.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "error", "Path is required"
            ));
        }

        try {
            boolean success = filesystemService.createDirectory(path);
            return ResponseEntity.ok(Map.of(
                "success", success,
                "path", path
            ));
        } catch (SecurityException e) {
            return ResponseEntity.status(403).body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", e.getMessage()
            ));
        }
    }
}
