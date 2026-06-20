package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_run_file_change")
public class RunFileChangePO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String filePath;
    private String changeType;
    private String beforeHash;
    private String afterHash;
    private String beforeSnapshotPath;
    private String afterSnapshotPath;
    private Boolean reversible;
    private String irreversibleReason;
    private LocalDateTime createdAt;
}
