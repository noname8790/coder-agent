package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_benchmark")
public class EvalBenchmarkPO {
    @TableId(type = IdType.AUTO)
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
