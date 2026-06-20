package cn.noname.coder.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 单次 Agent Run 的文件变更集。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RunChangeSet {
    private Long id;
    private String runId;
    private String workspaceKey;
    private String conversationId;
    private String status;
    private Boolean reversible;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
