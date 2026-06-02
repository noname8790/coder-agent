package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;
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

    @GetMapping("/{conversationId}/messages")
    public Response<List<ConversationMessageDTO>> messages(@PathVariable("conversationId") String conversationId) {
        return Response.ok(conversationCase.messages(conversationId));
    }
}
