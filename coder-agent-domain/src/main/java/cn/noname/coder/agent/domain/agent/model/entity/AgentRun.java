package cn.noname.coder.agent.domain.agent.model.entity;

import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AgentRun 是一次仓库任务执行的聚合根。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentRun {

    private Long id;
    private String runId;
    private String workspaceKey;
    private String conversationId;
    private String task;
    private String model;
    private AgentPermissionLevel permissionLevel;
    private AgentRunStatus status;
    private String finalAnswer;
    private String failureReason;
    private String gitBranch;
    private String commitHash;
    private Integer maxSteps;
    private Integer maxModelCalls;
    private Integer maxToolCalls;
    private Integer timeoutSeconds;
    private Integer stepCount;
    private Integer modelCallCount;
    private Integer toolCallCount;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private Long durationMs;
}
