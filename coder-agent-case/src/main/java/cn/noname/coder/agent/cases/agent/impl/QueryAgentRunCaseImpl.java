package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.AgentRunResponseDTO;
import cn.noname.coder.agent.api.dto.RunArtifactDTO;
import cn.noname.coder.agent.cases.agent.DiffSummaryAssembler;
import cn.noname.coder.agent.cases.agent.IQueryAgentRunCase;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.agent.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.ContextSnapshot;
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
    private final IContextSnapshotRepository contextSnapshotRepository;
    private final IWorkspacePort workspacePort;
    private final IArtifactPort artifactPort;
    private final DiffSummaryAssembler diffSummaryAssembler;

    @Override
    public AgentRunResponseDTO query(String runId) {
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        var artifacts = recordRepository.listArtifacts(runId);
        boolean changed = artifacts.stream().anyMatch(v -> v.getArtifactType() == ArtifactType.CHANGED_FILES);
        int changedFileCount = changed ? 1 : 0;
        String testStatus = artifacts.stream().anyMatch(v -> v.getArtifactType() == ArtifactType.TEST_REPORT) ? "RECORDED" : "NOT_RUN";
        ContextSnapshot latestSnapshot = contextSnapshotRepository.listByRunId(runId).stream()
                .reduce((previous, current) -> current)
                .orElse(null);
        var workspace = workspacePort.resolve(run.getWorkspaceKey()).orElse(null);
        var diffSummary = diffSummaryAssembler.load(artifactPort, workspace, runId);
        return new AgentRunResponseDTO(
                run.getRunId(),
                run.getWorkspaceKey(),
                run.getConversationId(),
                run.getTask(),
                run.getModel(),
                run.getPermissionLevel() == null ? "DEFAULT" : run.getPermissionLevel().name(),
                run.getStatus().name(),
                run.getFinalAnswer(),
                run.getFailureReason(),
                run.getGitBranch(),
                run.getCommitHash(),
                changed,
                changedFileCount,
                testStatus,
                run.getStepCount(),
                run.getModelCallCount(),
                run.getToolCallCount(),
                latestSnapshot == null ? null : latestSnapshot.getRawEstimatedTokens(),
                latestSnapshot == null ? null : latestSnapshot.getFinalEstimatedTokens(),
                latestSnapshot == null ? null : latestSnapshot.getCompressionRatio(),
                latestSnapshot == null ? null : latestSnapshot.getMemoryHitCount(),
                latestSnapshot == null ? null : latestSnapshot.getStaleMemoryCount(),
                latestSnapshot == null ? null : latestSnapshot.getSelectedFileSummaryCount(),
                latestSnapshot == null ? null : latestSnapshot.getSelectedRawSnippetCount(),
                latestSnapshot == null ? null : latestSnapshot.getSnapshotPath(),
                run.getDurationMs(),
                run.getCreatedAt(),
                run.getStartedAt(),
                run.getEndedAt(),
                artifacts.stream()
                        .map(a -> new RunArtifactDTO(a.getArtifactType().name(), a.getRelativePath(), a.getFileSize()))
                        .toList(),
                diffSummary
        );
    }
}
