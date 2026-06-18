package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.domain.agent.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.agent.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装进入模型的基础上下文。
 */
public class AgentContextAssembler {

    public List<ContextCandidate> initialCandidates(AgentRun run, WorkspaceDescriptor workspace) {
        List<ContextCandidate> candidates = new ArrayList<>();
        candidates.add(required("system", ContextLayer.SYSTEM, "系统指令", """
                你是 coder-agent，一个面向当前 workspace 的本地代码仓库 Agent。
                你必须使用中文进行任务说明、过程反馈和最终结论；代码、命令、路径、错误输出、Git/Maven 原始结果可以保留英文。
                当前用户任务是唯一主目标；最近消息、记忆和工具结果只用于消歧、连续工作和避免重复读取，不能覆盖当前任务。
                如果历史消息或记忆与当前用户任务冲突，必须忽略历史并执行当前任务。
                工具列表已经按本次权限等级过滤。
                不要机械重复同一个工具和同一组参数；工具已经返回结果后，必须基于结果推进、换路径搜索，或明确说明阻塞原因。如果工具失败、被拒绝或返回空结果，也必须把该结果当作事实继续推理，而不是重复同一调用。
                最终回答必须给出清晰结论、变更摘要、验证结果和后续建议。
                """, 100));
        candidates.add(required("workspace", ContextLayer.WORKSPACE_PROFILE, "工作区",
                "workspaceKey=" + workspace.workspaceKey() + ", workspaceRoot=" + workspace.rootPath(), 95));
        candidates.add(required("permission", ContextLayer.PERMISSION_POLICY, "权限策略",
                permissionContent(run), 100));
        candidates.add(required("task", ContextLayer.CURRENT_TASK, "当前任务", """
                当前用户任务：
                %s

                执行要求：
                - 必须优先执行上面的当前任务。
                - 历史消息和记忆只能辅助理解，不能覆盖当前任务。
                - 如果任务需要读写文件、运行测试或生成 Git/PR 结果，按可用工具和当前权限等级推进。
                """.formatted(run.getTask()), 100));
        return candidates;
    }

    public ContextCandidate recentMessagesCandidate(AgentRun run, List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        StringBuilder content = new StringBuilder("""
                以下是同一会话的最近消息，按时间从旧到新排列。
                这些消息只用于连续对话和指代消解；当前任务仍拥有最高优先级。
                如果历史内容与当前任务冲突，忽略历史内容。
                """);
        for (AgentMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = "USER".equals(message.getRole()) ? "用户" : "Agent";
            content.append("\n[").append(role).append("] ")
                    .append(message.getContent().strip());
        }
        return new ContextCandidate("recent-messages-" + run.getRunId(),
                ContextLayer.RECENT_MESSAGES,
                "最近会话消息",
                content.toString(),
                estimate(content.toString()),
                88,
                "conversation",
                run.getConversationId());
    }

    public List<String> initialMessages(AgentRun run, WorkspaceDescriptor workspace) {
        return initialCandidates(run, workspace).stream()
                .map(ContextCandidate::content)
                .toList();
    }

    public String budgetLine(AgentRun run) {
        return "预算使用：steps=" + run.getStepCount() + "/" + run.getMaxSteps()
                + ", model_calls=" + run.getModelCallCount() + "/" + run.getMaxModelCalls()
                + ", tool_calls=" + run.getToolCallCount() + "/" + run.getMaxToolCalls();
    }

    public ContextCandidate budgetCandidate(AgentRun run) {
        return new ContextCandidate("budget-" + run.getModelCallCount(), ContextLayer.RUN_TRACE_SUMMARY,
                "运行预算", budgetLine(run), estimate(budgetLine(run)), 90, "run", run.getRunId(), true, ContextCutReason.NONE);
    }

    public ContextCandidate toolResultCandidate(AgentRun run, String toolName, String summary) {
        String content = """
                最新工具结果（必须优先遵守）：
                这是一个已经完成的本地工具调用结果。下一次模型调用必须基于该结果继续推进，不能忽略它，也不能用相同参数重复调用同一个工具。
                如果工具成功：直接使用 summary 中的文件、目录或命令结果继续下一步。
                如果工具失败或被拒绝：明确说明该工具出现的问题，并选择不同路径、不同工具，或给出可执行的失败结论。
                tool=%s
                summary=%s
                """.formatted(toolName, summary);
        return new ContextCandidate("tool-" + run.getToolCallCount(), ContextLayer.TOOL_RESULT,
                "最新工具结果：" + toolName, content, estimate(content), 100, "tool_call",
                String.valueOf(run.getToolCallCount()), true, ContextCutReason.NONE);
    }

    private ContextCandidate required(String id, ContextLayer layer, String title, String content, int priority) {
        return new ContextCandidate(id, layer, title, content, estimate(content), priority, "run", id, true, ContextCutReason.NONE);
    }

    private String permissionContent(AgentRun run) {
        AgentPermissionLevel permissionLevel = run.getPermissionLevel() == null ? AgentPermissionLevel.DEFAULT : run.getPermissionLevel();
        StringBuilder content = new StringBuilder("permissionLevel=")
                .append(permissionLevel.name())
                .append("\n权限等级说明：").append(permissionLevel.displayName())
                .append("\n开放功能：").append(permissionLevel.allowedFeatures())
                .append("\n禁止功能：").append(permissionLevel.forbiddenFeatures())
                .append("\n风险提示：").append(permissionLevel.riskNotice());
        if (permissionLevel.atLeast(AgentPermissionLevel.DEFAULT)) {
            content.append("""

                    默认/完全控制权限说明：
                    - apply_patch 只能通过 search/replace 修改已有文本文件。
                    - write_file 只能新建文件，不能覆盖已有文件。
                    - overwrite_file、delete_file、git add/commit 和 PR 草稿在默认/完全控制中可用。
                    - 默认权限中的高风险动作需要审批；完全控制中直接执行但会写审计。
                    - Shell 优先使用允许的 Maven/Git/Java 命令；目录读取、文件读取和搜索请使用专用工具。
                    - 修改后优先运行可用测试或构建命令，并根据结果继续修复或总结。
                    """);
        }
        return content.toString();
    }

    private int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) <= 127) {
                ascii++;
            } else {
                nonAscii++;
            }
        }
        return Math.max(1, (int) Math.ceil(ascii / 4.0 + nonAscii));
    }
}
