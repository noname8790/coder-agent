package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent_step 表持久化对象。
 */
@Data
@TableName("agent_step")
public class AgentStepPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private Integer stepNo;
    private String stepType;
    private String summary;
    private LocalDateTime createdAt;
}
