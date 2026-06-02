package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent_conversation 表持久化对象。
 */
@Data
@TableName("agent_conversation")
public class AgentConversationPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String conversationId;
    private String workspaceKey;
    private String title;
    private String defaultModel;
    private String defaultPermissionLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
