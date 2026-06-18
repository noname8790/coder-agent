package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.api.dto.PermissionLevelDTO;
import cn.noname.coder.agent.cases.agent.IQueryPermissionLevelCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {PermissionLevelController.class, GlobalExceptionHandler.class})
class PermissionLevelControllerTest {

    @SpringBootApplication(scanBasePackages = "cn.noname.coder.agent.trigger")
    static class TestApplication {
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private IQueryPermissionLevelCase queryPermissionLevelCase;

    @Test
    void shouldReturnPermissionLevelsGivenQuery() throws Exception {
        // Given 权限等级用例返回只读档
        when(queryPermissionLevelCase.list()).thenReturn(List.of(new PermissionLevelDTO(
                "READ_ONLY", "只读", "只允许读取和分析仓库",
                List.of("读取"), List.of("写入"), "低风险", "book-open", false)));

        // When 查询权限等级 / Then 返回说明
        mockMvc.perform(get("/api/permission-levels"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("READ_ONLY"));
    }
}

