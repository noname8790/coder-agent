package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("agent_memory_item")
public class MemoryItemPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String memoryId;
    private String workspaceKey;
    private String sourceType;
    private String sourceId;
    private String filePath;
    private String contentHash;
    private LocalDateTime fileMtime;
    private String summaryVersion;
    private String title;
    private String summary;
    private String metadataJson;
    private String freshnessStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
