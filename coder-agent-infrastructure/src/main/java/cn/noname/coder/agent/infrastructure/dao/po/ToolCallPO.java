package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * tool_call 表持久化对象。
 */
@Data
@TableName("tool_call")
public class ToolCallPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Integer callNo;
    private String toolName;
    private String argumentsSummary;
    private String resultSummary;
    private Integer exitCode;
    private String status;
    private Long latencyMs;
    private String errorMessage;
    private LocalDateTime createdAt;
}
