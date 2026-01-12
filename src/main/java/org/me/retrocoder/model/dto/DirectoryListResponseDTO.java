package org.me.retrocoder.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Directory listing response DTO.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryListResponseDTO {
    @JsonProperty("current_path")
    private String currentPath;

    @JsonProperty("parent_path")
    private String parentPath;

    private List<DirectoryEntryDTO> entries;
    private List<DriveInfoDTO> drives;
}
