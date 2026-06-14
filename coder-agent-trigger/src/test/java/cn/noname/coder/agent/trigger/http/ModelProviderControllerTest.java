package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.ModelProviderListResponseDTO;
import cn.noname.coder.agent.api.dto.ModelProviderResponseDTO;
import cn.noname.coder.agent.cases.model.IModelProviderCase;
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

@WebMvcTest(controllers = {ModelProviderController.class, GlobalExceptionHandler.class})
class ModelProviderControllerTest {

    @SpringBootApplication(scanBasePackages = "cn.noname.coder.agent.trigger")
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IModelProviderCase modelProviderCase;

    @Test
    void shouldCreateModelProviderGivenValidRequest() throws Exception {
        // Given 模型配置创建成功
        when(modelProviderCase.create(any())).thenReturn(response("glm-5", true));

        // When 调用创建接口
        mockMvc.perform(post("/api/model-providers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "modelKey":"glm-5",
                                  "displayName":"GLM 5",
                                  "provider":"openai-compatible",
                                  "baseUrl":"https://dashscope.aliyuncs.com/compatible-mode/v1",
                                  "apiKey":"sk-test",
                                  "modelName":"glm-5",
                                  "endpointType":"chat-completions",
                                  "streamingEnabled":true,
                                  "toolCallingEnabled":true,
                                  "defaultModel":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0000"))
                .andExpect(jsonPath("$.data.modelKey").value("glm-5"))
                .andExpect(jsonPath("$.data.apiKeyMasked").value("****test"));

        // Then 请求 DTO 传入用例层
        ArgumentCaptor<cn.noname.coder.agent.api.dto.ModelProviderRequestDTO> captor =
                ArgumentCaptor.forClass(cn.noname.coder.agent.api.dto.ModelProviderRequestDTO.class);
        verify(modelProviderCase).create(captor.capture());
        assertEquals("chat-completions", captor.getValue().endpointType());
    }

    @Test
    void shouldListEnabledModelsGivenEnabledOnlyTrue() throws Exception {
        // Given 已启用模型列表
        when(modelProviderCase.list(true)).thenReturn(new ModelProviderListResponseDTO(List.of(response("glm-5", true))));

        // When 查询启用模型 / Then 返回模型候选项
        mockMvc.perform(get("/api/model-providers?enabledOnly=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.models[0].modelKey").value("glm-5"));
    }

    @Test
    void shouldSetDefaultGivenModelKey() throws Exception {
        // Given 默认模型切换成功
        when(modelProviderCase.setDefault("glm-5")).thenReturn(response("glm-5", true));

        // When 调用默认模型接口 / Then 返回默认模型
        mockMvc.perform(post("/api/model-providers/glm-5/default"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultModel").value(true));
    }

    @Test
    void shouldReturnErrorGivenModelInUseWhenDelete() throws Exception {
        // Given 模型正在使用
        when(modelProviderCase.delete("glm-5")).thenThrow(new AppException("MODEL_IN_USE", "模型正在被运行中任务使用：glm-5"));

        // When 删除模型 / Then 返回结构化错误
        mockMvc.perform(delete("/api/model-providers/glm-5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("MODEL_IN_USE"));
    }

    private ModelProviderResponseDTO response(String modelKey, boolean defaultModel) {
        return new ModelProviderResponseDTO(
                modelKey,
                "GLM 5",
                "openai-compatible",
                "https://dashscope.aliyuncs.com/compatible-mode/v1",
                "****test",
                modelKey,
                "chat-completions",
                0.2,
                180,
                true,
                true,
                defaultModel,
                "ENABLED",
                null,
                LocalDateTime.now(),
                LocalDateTime.now());
    }
}
