package cn.noname.coder.agent.cases.approval.impl;

import cn.noname.coder.agent.api.dto.ToolApprovalListResponseDTO;
import cn.noname.coder.agent.api.dto.ToolApprovalResponseDTO;
import cn.noname.coder.agent.cases.agent.impl.AgentRunExecutor;
import cn.noname.coder.agent.cases.approval.IToolApprovalCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.ToolApprovalRequest;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Slf4j
@Service
public class ToolApprovalCaseImpl implements IToolApprovalCase {

    private final IToolApprovalRepository approvalRepository;
    private final IAgentRunRepository runRepository;
    private final AgentRunExecutor agentRunExecutor;
    private final TaskExecutor taskExecutor;

    public ToolApprovalCaseImpl(IToolApprovalRepository approvalRepository,
                                IAgentRunRepository runRepository,
                                AgentRunExecutor agentRunExecutor,
                                @Qualifier("agentRunTaskExecutor") TaskExecutor taskExecutor) {
        this.approvalRepository = approvalRepository;
        this.runRepository = runRepository;
        this.agentRunExecutor = agentRunExecutor;
        this.taskExecutor = taskExecutor;
    }

    @Override
    public ToolApprovalListResponseDTO listPending(String runId) {
        return new ToolApprovalListResponseDTO(approvalRepository.listPending(runId).stream()
                .map(this::toDto)
                .toList());
    }

    @Override
    public ToolApprovalResponseDTO approve(String approvalId, String reason) {
        ToolApprovalRequest approval = findPending(approvalId);
        approval.setStatus("APPROVED");
        approval.setDecidedAt(LocalDateTime.now());
        approval.setDecisionReason(StringUtils.hasText(reason) ? reason : "用户批准");
        approvalRepository.update(approval);

        AgentRun run = waitingRun(approval);
        run.setStatus(AgentRunStatus.RUNNING);
        runRepository.update(run);
        taskExecutor.execute(() -> agentRunExecutor.execute(run.getRunId()));
        log.info("工具审批已批准 approvalId={} runId={} tool={}", approvalId, run.getRunId(), approval.getToolName());
        return toDto(approval);
    }

    @Override
    public ToolApprovalResponseDTO reject(String approvalId, String reason) {
        ToolApprovalRequest approval = findPending(approvalId);
        approval.setStatus("REJECTED");
        approval.setDecidedAt(LocalDateTime.now());
        approval.setDecisionReason(StringUtils.hasText(reason) ? reason : "用户拒绝");
        approvalRepository.update(approval);

        AgentRun run = waitingRun(approval);
        run.setStatus(AgentRunStatus.RUNNING);
        run.setFailureReason(null);
        runRepository.update(run);
        taskExecutor.execute(() -> agentRunExecutor.execute(run.getRunId()));
        log.info("工具审批已拒绝并恢复 Agent runId={} approvalId={} tool={}", run.getRunId(), approvalId, approval.getToolName());
        return toDto(approval);
    }

    private AgentRun waitingRun(ToolApprovalRequest approval) {
        AgentRun run = runRepository.findByRunId(approval.getRunId())
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + approval.getRunId()));
        if (run.getStatus() != AgentRunStatus.WAITING_APPROVAL) {
            throw new AppException("RUN_NOT_WAITING_APPROVAL", "运行不在等待审批状态");
        }
        return run;
    }

    private ToolApprovalRequest findPending(String approvalId) {
        ToolApprovalRequest approval = approvalRepository.findByApprovalId(approvalId)
                .orElseThrow(() -> new AppException("APPROVAL_NOT_FOUND", "审批请求不存在：" + approvalId));
        if (!"PENDING".equals(approval.getStatus())) {
            throw new AppException("APPROVAL_ALREADY_DECIDED", "审批请求已处理：" + approval.getStatus());
        }
        return approval;
    }

    private ToolApprovalResponseDTO toDto(ToolApprovalRequest request) {
        return new ToolApprovalResponseDTO(
                request.getApprovalId(),
                request.getRunId(),
                request.getWorkspaceKey(),
                request.getToolName(),
                request.getArgumentsJson(),
                request.getRiskSummary(),
                request.getDiffSummary(),
                request.getStatus(),
                request.getRequestedAt(),
                request.getDecidedAt(),
                request.getDecisionReason());
    }
}
