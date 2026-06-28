package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.model.entity.*;
import cn.noname.coder.agent.domain.tool.model.entity.ToolCall;
import cn.noname.coder.agent.infrastructure.dao.*;
import cn.noname.coder.agent.infrastructure.dao.po.*;
import cn.noname.coder.agent.types.enums.ArtifactType;
import cn.noname.coder.agent.types.enums.AuditEventType;
import cn.noname.coder.agent.types.enums.CallStatus;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Agent 执行记录仓储实现，集中落库步骤、调用、审计和工件索引。
 */
@Repository
@RequiredArgsConstructor
public class AgentRecordRepository implements IAgentRecordRepository {

    private final IAgentStepDao stepDao;
    private final IModelCallDao modelCallDao;
    private final IToolCallDao toolCallDao;
    private final IAuditEventDao auditEventDao;
    private final IRunArtifactDao runArtifactDao;

    @Override
    public void saveStep(AgentStep step) {
        AgentStepPO po = new AgentStepPO();
        po.setRunId(step.getRunId());
        po.setStepNo(step.getStepNo());
        po.setStepType(step.getStepType());
        po.setSummary(step.getSummary());
        po.setCreatedAt(step.getCreatedAt());
        stepDao.insert(po);
    }

    @Override
    public void saveModelCall(ModelCall modelCall) {
        ModelCallPO po = new ModelCallPO();
        po.setRunId(modelCall.getRunId());
        po.setCallNo(modelCall.getCallNo());
        po.setProvider(modelCall.getProvider());
        po.setModel(modelCall.getModel());
        po.setRequestSummary(modelCall.getRequestSummary());
        po.setResponseSummary(modelCall.getResponseSummary());
        po.setStatus(modelCall.getStatus().name());
        po.setLatencyMs(modelCall.getLatencyMs());
        po.setErrorMessage(modelCall.getErrorMessage());
        po.setCreatedAt(modelCall.getCreatedAt());
        modelCallDao.insert(po);
    }

    @Override
    public void saveToolCall(ToolCall toolCall) {
        ToolCallPO po = new ToolCallPO();
        po.setRunId(toolCall.getRunId());
        po.setCallNo(toolCall.getCallNo());
        po.setToolName(toolCall.getToolName());
        po.setArgumentsSummary(toolCall.getArgumentsSummary());
        po.setResultSummary(toolCall.getResultSummary());
        po.setExitCode(toolCall.getExitCode());
        po.setStatus(toolCall.getStatus().name());
        po.setLatencyMs(toolCall.getLatencyMs());
        po.setErrorMessage(toolCall.getErrorMessage());
        po.setCreatedAt(toolCall.getCreatedAt());
        toolCallDao.insert(po);
    }

    @Override
    public void saveAuditEvent(AuditEvent event) {
        AuditEventPO po = new AuditEventPO();
        po.setRunId(event.getRunId());
        po.setEventType(event.getEventType().name());
        po.setMessage(event.getMessage());
        po.setDetail(event.getDetail());
        po.setCreatedAt(event.getCreatedAt());
        auditEventDao.insert(po);
    }

    @Override
    public void saveArtifact(RunArtifact artifact) {
        RunArtifactPO po = new RunArtifactPO();
        po.setRunId(artifact.getRunId());
        po.setArtifactType(artifact.getArtifactType().name());
        po.setRelativePath(artifact.getRelativePath());
        po.setFileSize(artifact.getFileSize());
        po.setCreatedAt(artifact.getCreatedAt());
        runArtifactDao.insert(po);
    }

    @Override
    public List<AuditEvent> listAuditEvents(String runId) {
        return auditEventDao.selectList(new LambdaQueryWrapper<AuditEventPO>().eq(AuditEventPO::getRunId, runId))
                .stream().map(po -> AuditEvent.builder()
                        .id(po.getId())
                        .runId(po.getRunId())
                        .eventType(AuditEventType.valueOf(po.getEventType()))
                        .message(po.getMessage())
                        .detail(po.getDetail())
                        .createdAt(po.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public List<RunArtifact> listArtifacts(String runId) {
        return runArtifactDao.selectList(new LambdaQueryWrapper<RunArtifactPO>().eq(RunArtifactPO::getRunId, runId))
                .stream().map(po -> RunArtifact.builder()
                        .id(po.getId())
                        .runId(po.getRunId())
                        .artifactType(ArtifactType.valueOf(po.getArtifactType()))
                        .relativePath(po.getRelativePath())
                        .fileSize(po.getFileSize())
                        .createdAt(po.getCreatedAt())
                        .build())
                .toList();
    }

    @Override
    public void deleteByRunIds(Collection<String> runIds) {
        if (runIds == null || runIds.isEmpty()) {
            return;
        }
        stepDao.delete(new LambdaQueryWrapper<AgentStepPO>().in(AgentStepPO::getRunId, runIds));
        modelCallDao.delete(new LambdaQueryWrapper<ModelCallPO>().in(ModelCallPO::getRunId, runIds));
        toolCallDao.delete(new LambdaQueryWrapper<ToolCallPO>().in(ToolCallPO::getRunId, runIds));
        auditEventDao.delete(new LambdaQueryWrapper<AuditEventPO>().in(AuditEventPO::getRunId, runIds));
        runArtifactDao.delete(new LambdaQueryWrapper<RunArtifactPO>().in(RunArtifactPO::getRunId, runIds));
    }
}
