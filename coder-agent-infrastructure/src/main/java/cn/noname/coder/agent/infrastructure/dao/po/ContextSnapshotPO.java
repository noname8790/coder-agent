package cn.noname.coder.agent.infrastructure.dao.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("context_snapshot")
public class ContextSnapshotPO {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String snapshotId;
    private String runId;
    private String workspaceKey;
    private Integer modelCallNo;
    private String modelKey;
    private String budgetSource;
    private Integer rawEstimatedTokens;
    private Integer finalEstimatedTokens;
    private Double compressionRatio;
    private Integer memoryHitCount;
    private Integer staleMemoryCount;
    private Integer selectedFileSummaryCount;
    private Integer selectedRawSnippetCount;
    private String snapshotPath;
    private LocalDateTime createdAt;
}
