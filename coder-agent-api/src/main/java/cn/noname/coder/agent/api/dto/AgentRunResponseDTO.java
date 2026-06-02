package cn.noname.coder.agent.api.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 运行查询响应，返回摘要、预算使用量和工件索引。
 */
public record AgentRunResponseDTO(
        String runId,
        String workspaceKey,
        String conversationId,
        String task,
        String model,
        String permissionLevel,
        String status,
        String finalAnswer,
        String failureReason,
        String gitBranch,
        String commitHash,
        Boolean changed,
        Integer changedFileCount,
        String testStatus,
        Integer stepCount,
        Integer modelCallCount,
        Integer toolCallCount,
        Long durationMs,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        List<RunArtifactDTO> artifacts
) {
}
