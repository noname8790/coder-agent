package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_case_result")
public class EvalCaseResultPO {
    @TableId(type = IdType.AUTO)
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
    private Double retainedAnchorRate;
    private Double memoryRecallPrecision;
    private Double memoryRecallAtK;
    private Double staleBlockRate;
    private Integer repeatedReadCount;
    private Long tokenCost;
    private String resultPath;
    private LocalDateTime createdAt;
}
