package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * model_call 表持久化对象。
 */
@Data
@TableName("model_call")
public class ModelCallPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Integer callNo;
    private String provider;
    private String model;
    private String requestSummary;
    private String responseSummary;
    private String status;
    private Long latencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
