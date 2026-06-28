package cn.noname.coder.agent.domain.memory.model.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryRecall {

    private Long id;
    private String recallId;
    private String runId;
    private String workspaceKey;
    private String queryText;
    private Integer topK;
    private Double minScore;
    private Integer hitCount;
    private Integer selectedCount;
    private Integer candidateCount;
    private Integer filteredCount;
    private String detailJson;
    private LocalDateTime createdAt;
}
