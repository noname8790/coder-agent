package cn.noname.coder.agent.cases.conversation;

import cn.noname.coder.agent.api.dto.CheckpointRollbackResponseDTO;

public interface ICheckpointCase {

    CheckpointRollbackResponseDTO rollback(String conversationId, String messageId);

    CheckpointRollbackResponseDTO rollbackByCheckpointId(String conversationId, String checkpointId);
}
