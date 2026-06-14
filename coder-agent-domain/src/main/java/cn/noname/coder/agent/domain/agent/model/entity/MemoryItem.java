package cn.noname.coder.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryItem {

    private Long id;
    private String memoryId;
    private String workspaceKey;
    private String sourceType;
    private String sourceId;
    private String filePath;
    private String contentHash;
    private LocalDateTime fileMtime;
    private String summaryVersion;
    private String title;
    private String summary;
    private String metadataJson;
    private String freshnessStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
