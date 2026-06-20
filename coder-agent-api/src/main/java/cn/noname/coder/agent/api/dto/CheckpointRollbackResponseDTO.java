package cn.noname.coder.agent.api.dto;

import java.util.List;

public record CheckpointRollbackResponseDTO(
        String checkpointId,
        String conversationId,
        String status,
        List<String> revertedRunIds,
        List<String> conflictFiles,
        List<String> irreversibleFiles,
        String message
) {
}
