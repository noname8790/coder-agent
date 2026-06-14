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
public class EvalCaseResult {

    private Long id;
    private String evalId;
    private String benchmarkId;
    private String runId;
    private String modelKey;
    private String status;
    private Boolean passed;
    private Integer attempts;
    private Integer modelCalls;
    private Integer toolCalls;
    private Integer toolSteps;
    private Long durationMs;
    private String failureCategory;
    private Double contextCompressionRatio;
    private Integer memoryHitCount;
    private String resultPath;
    private LocalDateTime createdAt;
}
