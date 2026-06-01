package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent_run 表持久化对象。
 */
@Data
@TableName("agent_run")
public class AgentRunPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String workspaceKey;
    private String task;
    private String model;
    private String mode;
    private String status;
    private String finalAnswer;
    private String failureReason;
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
