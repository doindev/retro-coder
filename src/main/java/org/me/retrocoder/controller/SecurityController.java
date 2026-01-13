package org.me.retrocoder.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.me.retrocoder.service.EncryptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller for security-related operations, including encryption unlock.
 */
@Slf4j
@RestController
@RequestMapping("/api/security")
@RequiredArgsConstructor
public class SecurityController {

    private final EncryptionService encryptionService;

    /**
     * Get the current security/encryption status.
     */
    @GetMapping("/status")
    public ResponseEntity<SecurityStatusDTO> getStatus() {
        SecurityStatusDTO status = new SecurityStatusDTO(
            encryptionService.isUnlocked(),
            encryptionService.getPasswordSource()
        );
        return ResponseEntity.ok(status);
    }

    /**
     * Unlock the encryption service with a password.
     */
    @PostMapping("/unlock")
    public ResponseEntity<?> unlock(@RequestBody UnlockRequest request) {
        if (request.password() == null || request.password().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Password is required"
            ));
        }

        try {
            encryptionService.unlock(request.password());
            return ResponseEntity.ok(new SecurityStatusDTO(
                true,
                encryptionService.getPasswordSource()
            ));
        } catch (Exception e) {
            log.warn("Failed to unlock encryption service: {}", e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to unlock: " + e.getMessage()
            ));
        }
    }

    /**
     * Lock the encryption service (clears session key only).
     */
    @PostMapping("/lock")
    public ResponseEntity<SecurityStatusDTO> lock() {
        encryptionService.lock();
        return ResponseEntity.ok(new SecurityStatusDTO(
            encryptionService.isUnlocked(),
            encryptionService.getPasswordSource()
        ));
    }

    /**
     * DTO for security status response.
     */
    public record SecurityStatusDTO(
        boolean unlocked,
        String passwordSource
    ) {}

    /**
     * Request body for unlock endpoint.
     */
    public record UnlockRequest(String password) {}
}
