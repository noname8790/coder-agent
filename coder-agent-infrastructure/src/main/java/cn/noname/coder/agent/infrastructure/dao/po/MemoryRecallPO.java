package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("memory_recall")
public class MemoryRecallPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String recallId;
    private String runId;
    private String workspaceKey;
    private String queryText;
    private Integer topK;
    private Double minScore;
    private Integer hitCount;
    private Integer selectedCount;
    private String detailJson;
    private LocalDateTime createdAt;
}
