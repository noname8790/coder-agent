package cn.noname.coder.agent.domain.workspace.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单个文件的可逆变更记录。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunFileChange {
    private Long id;
    private String runId;
    private String filePath;
    private String changeType;
    private String beforeHash;
    private String afterHash;
    private String beforeSnapshotPath;
    private String afterSnapshotPath;
    private Boolean reversible;
    private String irreversibleReason;
    private LocalDateTime createdAt;
}
