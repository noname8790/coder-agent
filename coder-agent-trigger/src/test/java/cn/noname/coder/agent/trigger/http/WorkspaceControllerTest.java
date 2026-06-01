package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.WorkspaceListResponseDTO;
import cn.noname.coder.agent.api.dto.WorkspaceResponseDTO;
import cn.noname.coder.agent.cases.workspace.ICreateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.IDeactivateWorkspaceCase;
import cn.noname.coder.agent.cases.workspace.IQueryWorkspaceCase;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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

@WebMvcTest(controllers = {WorkspaceController.class, GlobalExceptionHandler.class})
class WorkspaceControllerTest {

    @SpringBootApplication(scanBasePackages = "cn.noname.coder.agent.trigger")
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ICreateWorkspaceCase createWorkspaceCase;
    @MockBean
    private IQueryWorkspaceCase queryWorkspaceCase;
    @MockBean
    private IDeactivateWorkspaceCase deactivateWorkspaceCase;

    @Test
    void shouldCreateWorkspaceGivenValidRequest() throws Exception {
        // Given 注册用例返回 workspace
        when(createWorkspaceCase.create(any())).thenReturn(workspace("demo", "ACTIVE"));

        // When 调用注册接口
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceKey\":\"demo\",\"rootPath\":\"E:/demo\",\"capabilities\":[\"READ_REPOSITORY\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceKey").value("demo"));

        // Then Controller 透传请求参数
        ArgumentCaptor<cn.noname.coder.agent.api.dto.CreateWorkspaceRequestDTO> captor =
                ArgumentCaptor.forClass(cn.noname.coder.agent.api.dto.CreateWorkspaceRequestDTO.class);
        verify(createWorkspaceCase).create(captor.capture());
        assertEquals("demo", captor.getValue().workspaceKey());
    }

    @Test
    void shouldReturnErrorGivenInvalidPath() throws Exception {
        // Given 用例拒绝路径
        when(createWorkspaceCase.create(any())).thenThrow(new AppException("WORKSPACE_PATH_INVALID", "rootPath 非法"));

        // When / Then 返回错误码
        mockMvc.perform(post("/api/workspaces")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workspaceKey\":\"bad\",\"rootPath\":\"relative\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("WORKSPACE_PATH_INVALID"));
    }

    @Test
    void shouldListAndQueryWorkspace() throws Exception {
        // Given 查询用例返回 workspace
        when(queryWorkspaceCase.listActive()).thenReturn(new WorkspaceListResponseDTO(List.of(workspace("demo", "ACTIVE"))));
        when(queryWorkspaceCase.query("demo")).thenReturn(workspace("demo", "ACTIVE"));

        // When / Then 查询列表和详情
        mockMvc.perform(get("/api/workspaces"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaces[0].workspaceKey").value("demo"));
        mockMvc.perform(get("/api/workspaces/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.workspaceKey").value("demo"));
    }

    @Test
    void shouldDeactivateWorkspace() throws Exception {
        // Given 停用用例返回 inactive
        when(deactivateWorkspaceCase.deactivate("demo")).thenReturn(workspace("demo", "INACTIVE"));

        // When / Then DELETE 返回 inactive
        mockMvc.perform(delete("/api/workspaces/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INACTIVE"));
    }

    private WorkspaceResponseDTO workspace(String key, String status) {
        return new WorkspaceResponseDTO(key, "E:\\demo", List.of("READ_REPOSITORY"), status,
                LocalDateTime.now(), LocalDateTime.now(), null);
    }
}
