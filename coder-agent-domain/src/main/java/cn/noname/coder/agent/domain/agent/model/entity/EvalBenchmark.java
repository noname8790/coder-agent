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
public class EvalBenchmark {

    private Long id;
    private String benchmarkId;
    private String name;
    private String workspaceKey;
    private String task;
    private String permissionLevel;
    private String modelKey;
    private String expectedOutcome;
    private String evaluatorType;
    private Integer timeoutSeconds;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
