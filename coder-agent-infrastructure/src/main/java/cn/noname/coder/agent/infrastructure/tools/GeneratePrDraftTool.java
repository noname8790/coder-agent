package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.agent.model.valobj.ToolDefinition;
import cn.noname.coder.agent.domain.agent.model.valobj.ToolResult;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.enums.CallStatus;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

@Component
public class GeneratePrDraftTool implements LocalTool {

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition("generate_pr_draft", "生成本地 PR 草稿 pull-request.md。参数：title、summary。",
                Map.of("type", "object",
                        "properties", Map.of(
                                "title", Map.of("type", "string"),
                                "summary", Map.of("type", "string"))));
    }

    @Override
    public ToolResult execute(String runId, WorkspaceDescriptor workspace, String argumentsJson) {
        Map<String, Object> args = ToolJson.parse(argumentsJson);
        String title = ToolJson.string(args, "title", "Agent changes");
        String summary = ToolJson.string(args, "summary", "本地 Agent 生成的仓库变更。");
        try {
            var stat = GitToolSupport.git(workspace, Duration.ofSeconds(120), "diff", "--stat");
            String content = """
                    # %s

                    ## 摘要
                    %s

                    ## 变更文件
                    ```text
                    %s
                    ```

                    ## 测试结果
                    - 待用户确认或查看 run 工件中的测试报告。

                    ## 风险与回滚
                    - 请审查 changed-files.json、patch.diff 和 rollback.patch。

                    ## Reviewer Checklist
                    - [ ] 变更范围符合任务要求
                    - [ ] 测试结果可接受
                    - [ ] 无敏感信息泄露
                    """.formatted(title, summary, stat.output().isBlank() ? "无未提交变更" : stat.output());
            Path file = GitToolSupport.runDir(workspace, runId).resolve("pull-request.md");
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
            return new ToolResult(CallStatus.SUCCESS, "PR 草稿已生成: .coder/runs/" + runId + "/pull-request.md",
                    content, 0, null);
        } catch (Exception e) {
            return new ToolResult(CallStatus.FAILED, "generate_pr_draft 执行失败: " + e.getMessage(), "", 1, e.getMessage());
        }
    }
}
