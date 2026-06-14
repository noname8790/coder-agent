package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("eval_run")
public class EvalRunPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String evalId;
    private String name;
    private String status;
    private String modelKeys;
    private Double passRate;
    private String reportPath;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
