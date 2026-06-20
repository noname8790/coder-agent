package cn.noname.coder.agent.cases.conversation.impl;

import cn.noname.coder.agent.api.dto.CheckpointRollbackResponseDTO;
import cn.noname.coder.agent.api.dto.RunChangeActionResponseDTO;
import cn.noname.coder.agent.cases.agent.RunChangeService;
import cn.noname.coder.agent.cases.conversation.ICheckpointCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentCheckpointRepository;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentConversationRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentCheckpoint;
import cn.noname.coder.agent.domain.agent.model.entity.AgentConversation;
import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckpointCaseImpl implements ICheckpointCase {

    private final IAgentConversationRepository conversationRepository;
    private final IAgentCheckpointRepository checkpointRepository;
    private final RunChangeService runChangeService;

    @Override
    public CheckpointRollbackResponseDTO rollback(String conversationId, String messageId) {
        AgentConversation conversation = loadConversation(conversationId);
        AgentMessage checkpointMessage = conversationRepository.findMessage(messageId)
                .orElseThrow(() -> new AppException("MESSAGE_NOT_FOUND", "消息不存在：" + messageId));
        validateCheckpointMessage(conversationId, checkpointMessage);
        AgentCheckpoint checkpoint = checkpointRepository.findByMessageId(messageId)
                .orElseGet(() -> createCheckpoint(conversation.getWorkspaceKey(), checkpointMessage));
        return rollbackToCheckpoint(conversationId, checkpoint);
    }

    @Override
    public CheckpointRollbackResponseDTO rollbackByCheckpointId(String conversationId, String checkpointId) {
        loadConversation(conversationId);
        AgentCheckpoint checkpoint = checkpointRepository.findByCheckpointId(checkpointId)
                .orElseThrow(() -> new AppException("CHECKPOINT_NOT_FOUND", "检查点不存在：" + checkpointId));
        if (!conversationId.equals(checkpoint.getConversationId())) {
            throw new AppException("CHECKPOINT_CONVERSATION_MISMATCH", "检查点所属会话与请求不一致");
        }
        return rollbackToCheckpoint(conversationId, checkpoint);
    }

    private AgentConversation loadConversation(String conversationId) {
        return conversationRepository.findConversation(conversationId)
                .orElseThrow(() -> new AppException("CONVERSATION_NOT_FOUND", "会话不存在：" + conversationId));
    }

    private void validateCheckpointMessage(String conversationId, AgentMessage message) {
        if (!conversationId.equals(message.getConversationId())) {
            throw new AppException("MESSAGE_CONVERSATION_MISMATCH", "消息所属会话与请求不一致");
        }
        if (!"AGENT".equals(message.getRole())) {
            throw new AppException("INVALID_CHECKPOINT_MESSAGE", "只能还原到 Agent 消息检查点");
        }
    }

    private CheckpointRollbackResponseDTO rollbackToCheckpoint(String conversationId, AgentCheckpoint checkpoint) {
        List<String> runIds = affectedRunIds(conversationId, checkpoint.getMessageSeq());
        List<String> conflicts = new ArrayList<>();
        List<String> irreversible = new ArrayList<>();
        for (String runId : runIds) {
            RunChangeActionResponseDTO validation = validateRun(runId);
            if (validation == null) {
                continue;
            }
            conflicts.addAll(validation.conflictFiles());
            irreversible.addAll(validation.irreversibleFiles());
            if (!"READY".equals(validation.status())
                    && validation.conflictFiles().isEmpty()
                    && validation.irreversibleFiles().isEmpty()) {
                conflicts.add(runId + "：" + validation.message());
            }
        }
        if (!conflicts.isEmpty() || !irreversible.isEmpty()) {
            return new CheckpointRollbackResponseDTO(checkpoint.getCheckpointId(), conversationId, "CONFLICTED",
                    List.of(), conflicts, irreversible, "检查点回滚被拒绝");
        }

        List<String> reverted = new ArrayList<>();
        for (int i = runIds.size() - 1; i >= 0; i--) {
            String runId = runIds.get(i);
            if (validateRun(runId) == null) {
                continue;
            }
            runChangeService.revert(runId);
            reverted.add(runId);
        }
        checkpoint.setRollbackStatus("ROLLED_BACK");
        checkpoint.setRollbackAt(LocalDateTime.now());
        checkpointRepository.update(checkpoint);
        conversationRepository.markMessagesRolledBackAfter(conversationId, checkpoint.getMessageSeq(), checkpoint.getCheckpointId());
        log.info("会话已回滚到检查点 conversationId={} checkpointId={} revertedRuns={}",
                conversationId, checkpoint.getCheckpointId(), reverted.size());
        return new CheckpointRollbackResponseDTO(checkpoint.getCheckpointId(), conversationId, "ROLLED_BACK",
                reverted, List.of(), List.of(), "已还原到检查点");
    }

    private RunChangeActionResponseDTO validateRun(String runId) {
        try {
            return runChangeService.validateRevert(runId);
        } catch (AppException e) {
            if ("CHANGE_SET_NOT_FOUND".equals(e.getCode())) {
                return null;
            }
            throw e;
        }
    }

    private AgentCheckpoint createCheckpoint(String workspaceKey, AgentMessage message) {
        AgentCheckpoint checkpoint = AgentCheckpoint.builder()
                .checkpointId("chkpt_" + UUID.randomUUID().toString().replace("-", ""))
                .conversationId(message.getConversationId())
                .workspaceKey(workspaceKey)
                .messageId(message.getMessageId())
                .runId(message.getRunId())
                .messageSeq(message.getSequenceNo() == null ? 0L : message.getSequenceNo())
                .rollbackStatus("ACTIVE")
                .createdAt(LocalDateTime.now())
                .build();
        checkpointRepository.save(checkpoint);
        return checkpoint;
    }

    private List<String> affectedRunIds(String conversationId, long checkpointSeq) {
        Set<String> runIds = new LinkedHashSet<>();
        for (AgentMessage message : conversationRepository.listMessages(conversationId)) {
            if (message.getSequenceNo() == null || message.getSequenceNo() <= checkpointSeq) {
                continue;
            }
            if ("ROLLED_BACK".equals(message.getVisibilityStatus())) {
                continue;
            }
            if (StringUtils.hasText(message.getRunId())) {
                runIds.add(message.getRunId());
            }
        }
        return List.copyOf(runIds);
    }
}
