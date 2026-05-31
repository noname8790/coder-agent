package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * run_artifact 表持久化对象。
 */
@Data
@TableName("run_artifact")
public class RunArtifactPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String runId;
    private String artifactType;
    private String relativePath;
    private Long fileSize;
    private LocalDateTime createdAt;
}
