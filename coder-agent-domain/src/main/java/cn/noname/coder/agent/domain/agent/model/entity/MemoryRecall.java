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
    private String detailJson;
    private LocalDateTime createdAt;
}
