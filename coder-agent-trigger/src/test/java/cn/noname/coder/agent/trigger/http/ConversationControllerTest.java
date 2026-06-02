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
                new ConversationMessageDTO("msg_1", "conv_1", "run_1", "USER", "修复测试", LocalDateTime.now()),
                new ConversationMessageDTO("msg_2", "conv_1", "run_1", "AGENT", "已完成", LocalDateTime.now())
        ));

        // When 查询消息 / Then 按列表返回
        mockMvc.perform(get("/api/conversations/conv_1/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].role").value("USER"))
                .andExpect(jsonPath("$.data[1].role").value("AGENT"));
    }

    private ConversationResponseDTO conversation() {
        return new ConversationResponseDTO("conv_1", "demo", "新会话", "glm-5",
                "L2_SAFE_EDIT", LocalDateTime.now(), LocalDateTime.now());
    }
}
