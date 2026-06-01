package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.AgentRunResponseDTO;
import cn.noname.coder.agent.api.dto.RunArtifactDTO;
import cn.noname.coder.agent.cases.agent.IQueryAgentRunCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.types.enums.ArtifactType;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 查询运行用例。
 */
@Service
@RequiredArgsConstructor
public class QueryAgentRunCaseImpl implements IQueryAgentRunCase {

    private final IAgentRunRepository runRepository;
    private final IAgentRecordRepository recordRepository;

    @Override
    public AgentRunResponseDTO query(String runId) {
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        var artifacts = recordRepository.listArtifacts(runId);
        boolean changed = artifacts.stream().anyMatch(v -> v.getArtifactType() == ArtifactType.CHANGED_FILES);
        int changedFileCount = changed ? 1 : 0;
        String testStatus = artifacts.stream().anyMatch(v -> v.getArtifactType() == ArtifactType.TEST_REPORT) ? "RECORDED" : "NOT_RUN";
        return new AgentRunResponseDTO(
                run.getRunId(),
                run.getWorkspaceKey(),
                run.getTask(),
                run.getModel(),
                run.getMode() == null ? "READ_ONLY" : run.getMode().name(),
                run.getStatus().name(),
                run.getFinalAnswer(),
                run.getFailureReason(),
                changed,
                changedFileCount,
                testStatus,
                run.getStepCount(),
                run.getModelCallCount(),
                run.getToolCallCount(),
                run.getDurationMs(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getEndedAt(),
                artifacts.stream()
                        .map(a -> new RunArtifactDTO(a.getArtifactType().name(), a.getRelativePath(), a.getFileSize()))
                        .toList()
        );
    }
}
