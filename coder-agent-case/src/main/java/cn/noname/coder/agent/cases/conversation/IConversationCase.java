package cn.noname.coder.agent.cases.conversation;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.api.dto.CreateConversationRequestDTO;

import java.util.List;

/**
 * 会话用例。
 */
public interface IConversationCase {

    ConversationResponseDTO create(CreateConversationRequestDTO request);

    List<ConversationResponseDTO> list(String workspaceKey);

    ConversationResponseDTO query(String conversationId);

    List<ConversationMessageDTO> messages(String conversationId);
}
