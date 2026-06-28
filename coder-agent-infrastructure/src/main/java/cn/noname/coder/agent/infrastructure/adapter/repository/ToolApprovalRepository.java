package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.tool.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.tool.model.entity.ToolApprovalRequest;
import cn.noname.coder.agent.infrastructure.dao.IToolApprovalRequestDao;
import cn.noname.coder.agent.infrastructure.dao.po.ToolApprovalRequestPO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
public class ToolApprovalRepository implements IToolApprovalRepository {

    private final IToolApprovalRequestDao dao;

    @Override
    public void save(ToolApprovalRequest request) {
        dao.insert(toPo(request));
    }

    @Override
    public void update(ToolApprovalRequest request) {
        dao.updateById(toPo(request));
    }

    @Override
    public Optional<ToolApprovalRequest> findByApprovalId(String approvalId) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                .eq(ToolApprovalRequestPO::getApprovalId, approvalId)))
                .map(this::toEntity);
    }

    @Override
    public List<ToolApprovalRequest> listPending(String runId) {
        return dao.selectList(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                        .eq(ToolApprovalRequestPO::getRunId, runId)
                        .eq(ToolApprovalRequestPO::getStatus, "PENDING"))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public Optional<ToolApprovalRequest> findPending(String runId, String toolName, String argumentsJson) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                        .eq(ToolApprovalRequestPO::getRunId, runId)
                        .eq(ToolApprovalRequestPO::getToolName, toolName)
                        .eq(ToolApprovalRequestPO::getArgumentsJson, argumentsJson)
                        .eq(ToolApprovalRequestPO::getStatus, "PENDING")
                        .last("LIMIT 1")))
                .map(this::toEntity);
    }

    @Override
    public Optional<ToolApprovalRequest> findApproved(String runId, String toolName, String argumentsJson) {
        return Optional.ofNullable(dao.selectOne(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                        .eq(ToolApprovalRequestPO::getRunId, runId)
                        .eq(ToolApprovalRequestPO::getToolName, toolName)
                        .eq(ToolApprovalRequestPO::getArgumentsJson, argumentsJson)
                        .in(ToolApprovalRequestPO::getStatus, Set.of("APPROVED", "APPROVED_EXECUTED"))
                .last("LIMIT 1")))
                .map(this::toEntity);
    }

    @Override
    public List<ToolApprovalRequest> listApproved(String runId) {
        return dao.selectList(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                        .eq(ToolApprovalRequestPO::getRunId, runId)
                        .in(ToolApprovalRequestPO::getStatus, Set.of("APPROVED", "APPROVED_EXECUTED")))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public List<ToolApprovalRequest> listApprovedPendingExecution(String runId) {
        return dao.selectList(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                        .eq(ToolApprovalRequestPO::getRunId, runId)
                        .eq(ToolApprovalRequestPO::getStatus, "APPROVED"))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void markExecuted(String approvalId) {
        findByApprovalId(approvalId).ifPresent(request -> {
            request.setStatus("APPROVED_EXECUTED");
            update(request);
        });
    }

    @Override
    public List<ToolApprovalRequest> listRejectedPendingReturn(String runId) {
        return dao.selectList(new LambdaQueryWrapper<ToolApprovalRequestPO>()
                        .eq(ToolApprovalRequestPO::getRunId, runId)
                        .eq(ToolApprovalRequestPO::getStatus, "REJECTED"))
                .stream()
                .map(this::toEntity)
                .toList();
    }

    @Override
    public void markReturned(String approvalId) {
        findByApprovalId(approvalId).ifPresent(request -> {
            request.setStatus("REJECTED_RETURNED");
            update(request);
        });
    }

    @Override
    public void deleteByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        dao.delete(new LambdaQueryWrapper<ToolApprovalRequestPO>().in(ToolApprovalRequestPO::getRunId, runIds));
    }

    private ToolApprovalRequestPO toPo(ToolApprovalRequest request) {
        ToolApprovalRequestPO po = new ToolApprovalRequestPO();
        po.setId(request.getId());
        po.setApprovalId(request.getApprovalId());
        po.setRunId(request.getRunId());
        po.setWorkspaceKey(request.getWorkspaceKey());
        po.setToolName(request.getToolName());
        po.setArgumentsJson(request.getArgumentsJson());
        po.setRiskSummary(request.getRiskSummary());
        po.setDiffSummary(request.getDiffSummary());
        po.setStatus(request.getStatus());
        po.setRequestedAt(request.getRequestedAt());
        po.setDecidedAt(request.getDecidedAt());
        po.setDecisionReason(request.getDecisionReason());
        return po;
    }

    private ToolApprovalRequest toEntity(ToolApprovalRequestPO po) {
        return ToolApprovalRequest.builder()
                .id(po.getId())
                .approvalId(po.getApprovalId())
                .runId(po.getRunId())
                .workspaceKey(po.getWorkspaceKey())
                .toolName(po.getToolName())
                .argumentsJson(po.getArgumentsJson())
                .riskSummary(po.getRiskSummary())
                .diffSummary(po.getDiffSummary())
                .status(po.getStatus())
                .requestedAt(po.getRequestedAt())
                .decidedAt(po.getDecidedAt())
                .decisionReason(po.getDecisionReason())
                .build();
    }
}
