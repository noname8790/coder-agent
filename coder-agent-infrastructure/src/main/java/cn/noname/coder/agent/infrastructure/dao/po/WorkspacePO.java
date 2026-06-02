package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * agent_workspace 表持久化对象。
 */
@Data
@TableName("agent_workspace")
public class WorkspacePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String workspaceKey;
    private String rootPath;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
