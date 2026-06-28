package cn.noname.coder.agent.cases.memory;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.model.adapter.port.IModelGateway;
import cn.noname.coder.agent.domain.model.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.model.model.valobj.ModelResponse;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemorySummaryServiceTest {

    @Test
    void shouldUseModelJsonSummaryGivenModelAvailable() {
        // Given 模型可用且返回内部摘要 JSON
        MemorySummaryService service = new MemorySummaryService(provider(request -> new ModelResponse(
                "resp_1",
                """
                        {
                          "task_goal": "新增 VersionInfo 能力",
                          "status": "SUCCEEDED",
                          "completed": ["新增 VersionInfo.java", "提供 appName 和 version 两个静态方法"],
                          "changed_files": ["src/main/java/cn/noname/demo/VersionInfo.java"],
                          "verification": ["mvn test 通过"],
                          "followups": [],
                          "risks": []
                        }
                        """,
                List.of(),
                "{}")));
        AgentRun run = AgentRun.builder()
                .runId("run_1")
                .workspaceKey("repo-a")
                .task("新增版本信息类")
                .model("glm")
                .status(AgentRunStatus.SUCCEEDED)
                .finalAnswer("RAW_VISIBLE_FINAL_ANSWER_SHOULD_NOT_BE_USED")
                .build();

        // When 生成运行内部摘要
        String summary = service.summarizeRun(run);

        // Then 使用模型 JSON 标准化结果，不落入用户可见原文
        assertTrue(summary.contains("用户目标：新增 VersionInfo 能力"));
        assertTrue(summary.contains("- 新增 VersionInfo.java"));
        assertFalse(summary.contains("RAW_VISIBLE_FINAL_ANSWER_SHOULD_NOT_BE_USED"));
    }

    @Test
    void shouldFallbackToStructuredFileSummaryGivenModelUnavailable() {
        // Given 模型不可用
        MemorySummaryService service = new MemorySummaryService(null);

        // When 生成文件摘要
        String summary = service.summarizeFile(null,
                "src/main/java/cn/noname/demo/Calculator.java",
                """
                        package cn.noname.demo;

                        public class Calculator {
                            public int add(int left, int right) {
                                return left + right;
                            }
                        }
                        """);

        // Then 仍然生成结构化摘要，而不是文件全文
        assertTrue(summary.contains("文件：src/main/java/cn/noname/demo/Calculator.java"));
        assertTrue(summary.contains("主要符号"));
        assertTrue(summary.contains("Calculator"));
        assertFalse(summary.contains("return left + right"));
    }

    private ObjectProvider<IModelGateway> provider(IModelGateway gateway) {
        return new ObjectProvider<>() {
            @Override
            public IModelGateway getObject(Object... args) {
                return gateway;
            }

            @Override
            public IModelGateway getIfAvailable() {
                return gateway;
            }

            @Override
            public IModelGateway getIfUnique() {
                return gateway;
            }

            @Override
            public IModelGateway getObject() {
                return gateway;
            }

            @Override
            public Iterator<IModelGateway> iterator() {
                return List.of(gateway).iterator();
            }

            @Override
            public Stream<IModelGateway> stream() {
                return Stream.of(gateway);
            }

            @Override
            public Stream<IModelGateway> orderedStream() {
                return Stream.of(gateway);
            }
        };
    }
}
