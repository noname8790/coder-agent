package cn.noname.coder.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * AgentStep 记录执行循环中的一个决策步骤，便于审计和回放。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentStep {

    private Long id;
    private String runId;
    private Integer stepNo;
    private String stepType;
    private String summary;
    private LocalDateTime createdAt;
}
