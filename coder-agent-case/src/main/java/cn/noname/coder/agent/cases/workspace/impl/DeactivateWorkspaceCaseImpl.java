package cn.noname.coder.agent.cases.workspace.impl;

import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.cases.workspace.IDeactivateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.WorkspaceAssembler;
import cn.noname.coder.agent.domain.agent.adapter.port.IVectorMemoryPort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRecordRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IContextSnapshotRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IMemoryRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IToolApprovalRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class DeactivateWorkspaceCaseImpl implements IDeactivateWorkspaceCase {

    private final IWorkspaceRepository workspaceRepository;
    private final IAgentRunRepository runRepository;
    private final IAgentConversationRepository conversationRepository;
    private final IAgentRecordRepository recordRepository;
    private final IContextSnapshotRepository contextSnapshotRepository;
    private final IMemoryRepository memoryRepository;
    private final IVectorMemoryPort vectorMemoryPort;
    private final IToolApprovalRepository toolApprovalRepository;
    private final WorkspaceAssembler assembler = new WorkspaceAssembler();

    public DeactivateWorkspaceCaseImpl(IWorkspaceRepository workspaceRepository) {
        this(workspaceRepository, null, null, null, null, null, null, null);
    }

    @Autowired
    public DeactivateWorkspaceCaseImpl(IWorkspaceRepository workspaceRepository,
                                       IAgentRunRepository runRepository,
                                       IAgentConversationRepository conversationRepository,
                                       IAgentRecordRepository recordRepository,
                                       IContextSnapshotRepository contextSnapshotRepository,
                                       IMemoryRepository memoryRepository,
                                       IVectorMemoryPort vectorMemoryPort,
                                       IToolApprovalRepository toolApprovalRepository) {
        this.workspaceRepository = workspaceRepository;
        this.runRepository = runRepository;
        this.conversationRepository = conversationRepository;
        this.recordRepository = recordRepository;
        this.contextSnapshotRepository = contextSnapshotRepository;
        this.memoryRepository = memoryRepository;
        this.vectorMemoryPort = vectorMemoryPort;
        this.toolApprovalRepository = toolApprovalRepository;
    }

    @Override
    public WorkspaceResponseDTO deactivate(String workspaceKey) {
        log.info("开始停用 workspace workspaceKey={}", workspaceKey);
        var workspace = workspaceRepository.findActiveByWorkspaceKey(workspaceKey)
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspace 不存在或不可用：" + workspaceKey));
        cleanupWorkspaceData(workspaceKey);
        LocalDateTime now = LocalDateTime.now();
        workspace.setStatus(WorkspaceStatus.INACTIVE);
        workspace.setUpdatedAt(now);
        workspace.setDeletedAt(now);
        workspaceRepository.update(workspace);
        log.info("已停用 workspace workspaceKey={} deletedAt={}", workspaceKey, now);
        return assembler.toDTO(workspace);
    }

    private void cleanupWorkspaceData(String workspaceKey) {
        if (runRepository == null || conversationRepository == null || recordRepository == null
                || contextSnapshotRepository == null || memoryRepository == null || vectorMemoryPort == null
                || toolApprovalRepository == null) {
            return;
        }
        List<String> runIds = runRepository.listByWorkspaceKey(workspaceKey).stream()
                .map(run -> run.getRunId())
                .filter(runId -> runId != null && !runId.isBlank())
                .toList();
        if (!runIds.isEmpty()) {
            contextSnapshotRepository.deleteByRunIds(runIds);
            memoryRepository.deleteByRunIds(workspaceKey, runIds);
            vectorMemoryPort.deleteByRunIds(workspaceKey, runIds);
            toolApprovalRepository.deleteByRunIds(runIds);
            recordRepository.deleteByRunIds(runIds);
            runRepository.deleteByRunIds(runIds);
        }
        memoryRepository.deleteByWorkspaceKey(workspaceKey);
        vectorMemoryPort.deleteByWorkspaceKey(workspaceKey);

        List<AgentConversation> conversations = conversationRepository.listConversations(workspaceKey);
        for (AgentConversation conversation : conversations) {
            conversationRepository.deleteMessagesByConversationId(conversation.getConversationId());
            conversationRepository.deleteConversation(conversation.getConversationId());
        }
        log.info("workspace 关联数据已清理 workspaceKey={} runCount={} conversationCount={}",
                workspaceKey, runIds.size(), conversations.size());
    }
}
