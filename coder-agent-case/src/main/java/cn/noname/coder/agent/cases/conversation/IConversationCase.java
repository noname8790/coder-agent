package cn.noname.coder.agent.cases.conversation;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;
import cn.noname.coder.agent.api.dto.UpdateConversationRequestDTO;
import cn.noname.coder.agent.api.dto.UpdateConversationMessageRequestDTO;

import java.util.List;

/**
 * 会话用例。
 */
public interface IConversationCase {

    ConversationResponseDTO create(CreateConversationRequestDTO request);

    List<ConversationResponseDTO> list(String workspaceKey);

    ConversationResponseDTO query(String conversationId);

    ConversationResponseDTO update(String conversationId, UpdateConversationRequestDTO request);

    List<ConversationMessageDTO> messages(String conversationId);

    ConversationResponseDTO delete(String conversationId);

    ConversationMessageDTO updateMessage(String conversationId, String messageId, UpdateConversationMessageRequestDTO request);

    ConversationMessageDTO deleteMessage(String conversationId, String messageId);
}
