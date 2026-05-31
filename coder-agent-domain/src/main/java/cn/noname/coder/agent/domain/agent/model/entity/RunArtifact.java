package cn.noname.coder.agent.domain.agent.model.entity;

import cn.noname.coder.agent.types.enums.ArtifactType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 运行工件索引，数据库只保存文件路径和类型。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunArtifact {

    private Long id;
    private String runId;
    private ArtifactType artifactType;
    private String relativePath;
    private Long fileSize;
    private LocalDateTime createdAt;
}
