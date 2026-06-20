package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_checkpoint")
public class AgentCheckpointPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String checkpointId;
    private String conversationId;
    private String workspaceKey;
    private String messageId;
    private String runId;
    private Long messageSeq;
    private String rollbackStatus;
    private LocalDateTime createdAt;
    private LocalDateTime rollbackAt;
}
