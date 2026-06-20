package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_change_set")
public class RunChangeSetPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String workspaceKey;
    private String conversationId;
    private String status;
    private Boolean reversible;
    private String failureReason;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
