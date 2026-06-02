package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.*;
import cn.noname.coder.agent.cases.agent.ICancelAgentRunCase;
import cn.noname.coder.agent.cases.agent.ICreateAgentRunCase;
import cn.noname.coder.agent.cases.agent.IQueryAgentRunCase;
import cn.noname.coder.agent.cases.agent.IQueryRunTraceCase;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AgentRunController.class, GlobalExceptionHandler.class})
class AgentRunControllerTest {

    @SpringBootApplication(scanBasePackages = "cn.noname.coder.agent.trigger")
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ICreateAgentRunCase createAgentRunCase;
    @MockBean
    private IQueryAgentRunCase queryAgentRunCase;
    @MockBean
    private IQueryRunTraceCase queryRunTraceCase;
    @MockBean
    private ICancelAgentRunCase cancelAgentRunCase;

    @Test
    void shouldCreateRunGivenValidRequest() throws Exception {
        // Given 创建用例返回 runId
        when(createAgentRunCase.create(any())).thenReturn(new CreateAgentRunResponseDTO("run_1", "CREATED", LocalDateTime.now()));

        // When 调用创建接口 / Then 返回统一成功响应
        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceKey\":\"coder-agent\",\"task\":\"分析仓库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.runId").value("run_1"));
    }

    @Test
    void shouldPassModelKeyWhenCreateRunGivenModelSelected() throws Exception {
        // Given 创建用例返回 runId
        when(createAgentRunCase.create(any())).thenReturn(new CreateAgentRunResponseDTO("run_1", "CREATED", LocalDateTime.now()));

        // When 调用创建接口并传入模型 key
        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceKey\":\"coder-agent\",\"task\":\"分析仓库\",\"model\":\"glm-5\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"));

        // Then Controller 将模型 key 传入用例层
        ArgumentCaptor<CreateAgentRunRequestDTO> captor = ArgumentCaptor.forClass(CreateAgentRunRequestDTO.class);
        verify(createAgentRunCase).create(captor.capture());
        assertEquals("glm-5", captor.getValue().model());
    }

    @Test
    void shouldReturnErrorGivenUnknownWorkspace() throws Exception {
        // Given workspace 未配置
        when(createAgentRunCase.create(any())).thenThrow(new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置"));

        // When 调用创建接口 / Then 返回错误码
        mockMvc.perform(post("/api/agent-runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceKey\":\"missing\",\"task\":\"分析仓库\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WORKSPACE_NOT_FOUND"));
    }

    @Test
    void shouldQueryRunGivenExistingRun() throws Exception {
        // Given 运行存在
        when(queryAgentRunCase.query("run_1")).thenReturn(new AgentRunResponseDTO(
                "run_1", "coder-agent", null, "分析仓库", "model", "L1_READ_ONLY",
                "SUCCEEDED", "完成", null, null, null,
                false, 0, "NOT_RUN",
                1, 1, 0, 100L, LocalDateTime.now(), LocalDateTime.now(), LocalDateTime.now(), List.of()));

        // When 查询运行 / Then 返回状态
        mockMvc.perform(get("/api/agent-runs/run_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void shouldCancelRunGivenRunningRun() throws Exception {
        // Given 运行可取消
        when(cancelAgentRunCase.cancel("run_1")).thenReturn(new CancelAgentRunResponseDTO("run_1", "CANCELLED"));

        // When 取消运行 / Then 返回取消状态
        mockMvc.perform(post("/api/agent-runs/run_1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }
}
