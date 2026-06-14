package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.ConversationMessageDTO;
import cn.noname.coder.agent.api.dto.ConversationResponseDTO;
import cn.noname.coder.agent.cases.conversation.IConversationCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {ConversationController.class, GlobalExceptionHandler.class})
class ConversationControllerTest {

    @SpringBootApplication(scanBasePackages = "cn.noname.coder.agent.trigger")
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IConversationCase conversationCase;

    @Test
    void shouldCreateConversationGivenValidRequest() throws Exception {
        // Given 会话用例返回新会话
        when(conversationCase.create(any())).thenReturn(conversation());

        // When 创建会话 / Then 返回 conversationId
        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceKey\":\"demo\",\"title\":\"新会话\",\"defaultModel\":\"glm-5\",\"defaultPermissionLevel\":\"L2_SAFE_EDIT\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("conv_1"));
    }

    @Test
    void shouldListConversationMessagesGivenConversationId() throws Exception {
        // Given 会话下存在用户消息和 Agent 消息
        when(conversationCase.messages("conv_1")).thenReturn(List.of(
                new ConversationMessageDTO("msg_1", "conv_1", "run_1", "USER", "修复测试", "RUNNING", null, LocalDateTime.now()),
                new ConversationMessageDTO("msg_2", "conv_1", "run_1", "AGENT", "已完成", "SUCCEEDED", null, LocalDateTime.now())
        ));

        // When 查询消息 / Then 按列表返回
        mockMvc.perform(get("/api/conversations/conv_1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].role").value("AGENT"));
    }

    @Test
    void shouldDeleteConversationGivenConversationId() throws Exception {
        // Given 会话存在
        when(conversationCase.delete("conv_1")).thenReturn(conversation());

        // When 删除会话 / Then 返回被删除的会话并调用删除用例
        mockMvc.perform(delete("/api/conversations/conv_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.conversationId").value("conv_1"));
        verify(conversationCase).delete("conv_1");
    }

    @Test
    void shouldUpdateUserMessageGivenMessageId() throws Exception {
        // Given 用户消息存在
        when(conversationCase.updateMessage(any(), any(), any())).thenReturn(
                new ConversationMessageDTO("msg_1", "conv_1", "run_1", "USER", "修改后的任务", "RUNNING", null, LocalDateTime.now()));

        // When 修改消息 / Then 返回更新后的内容
        mockMvc.perform(put("/api/conversations/conv_1/messages/msg_1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"修改后的任务\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").value("修改后的任务"));
        verify(conversationCase).updateMessage(any(), any(), any());
    }

    @Test
    void shouldDeleteMessageGivenMessageId() throws Exception {
        // Given 消息存在
        when(conversationCase.deleteMessage("conv_1", "msg_1")).thenReturn(
                new ConversationMessageDTO("msg_1", "conv_1", "run_1", "USER", "任务", "RUNNING", null, LocalDateTime.now()));

        // When 删除消息 / Then 返回被删除消息
        mockMvc.perform(delete("/api/conversations/conv_1/messages/msg_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messageId").value("msg_1"));
        verify(conversationCase).deleteMessage("conv_1", "msg_1");
    }

    private ConversationResponseDTO conversation() {
        return new ConversationResponseDTO("conv_1", "demo", "新会话", "glm-5",
                "L2_SAFE_EDIT", LocalDateTime.now(), LocalDateTime.now());
    }
}
