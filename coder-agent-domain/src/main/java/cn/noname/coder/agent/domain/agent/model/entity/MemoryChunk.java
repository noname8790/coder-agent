package cn.noname.coder.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryChunk {

    private Long id;
    private String chunkId;
    private String workspaceKey;
    private String memoryId;
    private String sourceType;
    private String sourceId;
    private String filePath;
    private String contentHash;
    private String freshnessStatus;
    private String content;
    private String metadataJson;
    private List<Double> embedding;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
