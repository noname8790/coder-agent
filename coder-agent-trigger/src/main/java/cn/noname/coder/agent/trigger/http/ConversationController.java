package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;
import cn.noname.coder.agent.api.dto.CheckpointRollbackResponseDTO;
import cn.noname.coder.agent.api.dto.UpdateConversationRequestDTO;
import cn.noname.coder.agent.api.dto.UpdateConversationMessageRequestDTO;
import cn.noname.coder.agent.cases.conversation.ICheckpointCase;
import cn.noname.coder.agent.cases.conversation.IConversationCase;
import cn.noname.coder.agent.types.common.Response;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Agent 会话 REST API。
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/conversations")
public class ConversationController {

    private final IConversationCase conversationCase;
    private final ICheckpointCase checkpointCase;

    @PostMapping
    public Response<ConversationResponseDTO> create(@RequestBody CreateConversationRequestDTO request) {
        return Response.ok(conversationCase.create(request));
    }

    @GetMapping
    public Response<List<ConversationResponseDTO>> list(@RequestParam(value = "workspaceKey", required = false) String workspaceKey) {
        return Response.ok(conversationCase.list(workspaceKey));
    }

    @GetMapping("/{conversationId}")
    public Response<ConversationResponseDTO> query(@PathVariable("conversationId") String conversationId) {
        return Response.ok(conversationCase.query(conversationId));
    }

    @PatchMapping("/{conversationId}")
    public Response<ConversationResponseDTO> update(@PathVariable("conversationId") String conversationId,
                                                    @RequestBody UpdateConversationRequestDTO request) {
        return Response.ok(conversationCase.update(conversationId, request));
    }

    @GetMapping("/{conversationId}/messages")
    public Response<List<ConversationMessageDTO>> messages(@PathVariable("conversationId") String conversationId) {
        return Response.ok(conversationCase.messages(conversationId));
    }

    @DeleteMapping("/{conversationId}")
    public Response<ConversationResponseDTO> delete(@PathVariable("conversationId") String conversationId) {
        return Response.ok(conversationCase.delete(conversationId));
    }

    @PutMapping("/{conversationId}/messages/{messageId}")
    public Response<ConversationMessageDTO> updateMessage(@PathVariable("conversationId") String conversationId,
                                                          @PathVariable("messageId") String messageId,
                                                          @RequestBody UpdateConversationMessageRequestDTO request) {
        return Response.ok(conversationCase.updateMessage(conversationId, messageId, request));
    }

    @DeleteMapping("/{conversationId}/messages/{messageId}")
    public Response<ConversationMessageDTO> deleteMessage(@PathVariable("conversationId") String conversationId,
                                                          @PathVariable("messageId") String messageId) {
        return Response.ok(conversationCase.deleteMessage(conversationId, messageId));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/checkpoint/rollback")
    public Response<CheckpointRollbackResponseDTO> rollbackCheckpoint(@PathVariable("conversationId") String conversationId,
                                                                      @PathVariable("messageId") String messageId) {
        return Response.ok(checkpointCase.rollback(conversationId, messageId));
    }

    @PostMapping("/{conversationId}/checkpoints/{checkpointId}/rollback")
    public Response<CheckpointRollbackResponseDTO> rollbackCheckpointById(@PathVariable("conversationId") String conversationId,
                                                                          @PathVariable("checkpointId") String checkpointId) {
        return Response.ok(checkpointCase.rollbackByCheckpointId(conversationId, checkpointId));
    }
}
