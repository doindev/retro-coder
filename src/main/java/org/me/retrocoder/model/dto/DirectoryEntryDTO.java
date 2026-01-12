package org.me.retrocoder.model.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Directory entry DTO for filesystem browsing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryEntryDTO {
    private String name;
    private String path;

    @JsonProperty("is_directory")
    private boolean isDirectory;

    @JsonProperty("is_hidden")
    private boolean isHidden;

    @JsonProperty("has_children")
    private boolean hasChildren;

    private Long size;

    @JsonProperty("last_modified")
    private Long lastModified;
}
