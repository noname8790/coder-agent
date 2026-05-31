package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * audit_event 表持久化对象。
 */
@Data
@TableName("audit_event")
public class AuditEventPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String eventType;
    private String message;
    private String detail;
    private LocalDateTime createdAt;
}
