package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent_message 表持久化对象。
 */
@Data
@TableName("agent_message")
public class AgentMessagePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String messageId;
    private String conversationId;
    private String runId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
