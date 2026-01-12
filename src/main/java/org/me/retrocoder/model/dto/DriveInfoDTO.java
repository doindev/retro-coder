package org.me.retrocoder.model.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Drive information DTO for Windows drive listing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DriveInfoDTO {
    private String letter;
    private String path;
    private String label;
    private Long totalSpace;
    private Long freeSpace;
}
