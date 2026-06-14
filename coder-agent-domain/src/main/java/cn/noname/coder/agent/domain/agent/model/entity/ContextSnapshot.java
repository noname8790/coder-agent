package cn.noname.coder.agent.domain.agent.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContextSnapshot {

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
