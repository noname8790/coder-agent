package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.domain.workspace.model.entity.AgentMessage;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCandidate;
import cn.noname.coder.agent.domain.context.model.valobj.ContextCutReason;
import cn.noname.coder.agent.domain.context.model.valobj.ContextLayer;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * 组装进入模型的基础上下文。
 */
public class AgentContextAssembler {

    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "([A-Za-z]:)?[\\\\/\\w.\\-]+\\.(java|xml|md|yml|yaml|json|sql|properties|ts|tsx|js|jsx|vue|py|txt)",
            Pattern.CASE_INSENSITIVE);

    public List<ContextCandidate> initialCandidates(AgentRun run, WorkspaceDescriptor workspace) {
        List<ContextCandidate> candidates = new ArrayList<>();
        candidates.add(required("system", ContextLayer.SYSTEM, "系统指令", """
                你是 coder-agent，一个面向当前 workspace 的本地代码仓库 Agent。以下是不同标签的规则声明：
                - <agent_workflow>...</agent_workflow> 描述你的工作流程和决策逻辑。
                - <workspace>...</workspace> 描述本次任务唯一允许操作的工作区；不要访问或修改工作区之外的路径。
                - <permission>...</permission> 描述当前权限等级、可用能力和禁止能力，必须严格遵守。
                - <memory>...</memory>、<context>...</context>、<work_status>...</work_status> 只用于连续工作、减少重复读取和理解当前任务背景。
                - <current_task>...</current_task> 是本轮最高优先级目标；如果其他标签内容与它冲突，必须执行当前任务。
                
                你必须使用中文进行任务说明、过程反馈和最终结论；
                当前用户任务(【...】的内容)是唯一主目标；最近消息、记忆和工具结果只用于消歧、连续工作和避免重复读取，不能覆盖当前任务。
                如果历史消息或记忆与当前用户任务冲突，必须忽略历史并执行当前任务。
                工具列表已经按本次权限等级过滤。
                工具已经返回结果后，必须基于结果推进、换路径搜索，或明确说明阻塞原因。如果工具失败、被拒绝或返回空结果，也必须把该结果当作事实继续推理，切勿重复同一调用。
                最终回答必须给出清晰结论、变更摘要、验证结果和后续建议。
                """, 100));
        candidates.add(required("workspace", ContextLayer.WORKSPACE_PROFILE, "工作区",
                workspaceContent(workspace), 95));
        candidates.add(required("permission", ContextLayer.PERMISSION_POLICY, "权限策略",
                permissionContent(run), 100));
        candidates.add(required("task", ContextLayer.CURRENT_TASK, "当前任务", """
                当前用户任务：
                【%s】

                执行要求：
                - 必须优先执行上面的当前任务。
                - 历史消息和记忆只能辅助理解，不能覆盖当前任务。
                - 如果任务需要读写文件、运行测试或生成 Git/PR 结果，按可用工具和当前权限等级推进。
                """.formatted(run.getTask()), 100));
        return candidates;
    }

    public ContextCandidate workStatusCandidate(AgentRun run, List<String> summaries) {
        String content = summaries == null || summaries.isEmpty()
                ? "null"
                : """
                以下是同一会话近期任务的工作状态摘要，按新到旧排列，窗口与 <context> 的最近消息窗口对齐。
                它记录之前大概做过哪些文件改动、撤销或还原状态；只能辅助理解当前工作进度，不能覆盖当前任务。

                %s
                """.formatted(String.join("\n", summaries));
        return new ContextCandidate("work-status-" + run.getRunId(),
                ContextLayer.RUN_TRACE_SUMMARY,
                "近期任务状态",
                content,
                estimate(content),
                86,
                "run_change",
                run.getConversationId());
    }

    public ContextCandidate recentMessagesCandidate(AgentRun run, List<AgentMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        StringBuilder content = new StringBuilder("""
                以下是同一会话的最近消息，最多保留最近 8 条完整内容，按时间从旧到新排列，不同消息之间已用分割线隔离。
                这些消息只用于连续对话和指代消解；当前任务仍拥有最高优先级。
                如果历史内容与当前任务冲突，忽略历史内容。
                """);
        for (AgentMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String role = "USER".equals(message.getRole()) ? "用户任务" : "coder-agent";
            content.append("\n---------------------------------------------------------------\n-").append(role).append("：")
                    .append(message.getContent().strip());
        }
        content.append("\n---------------------------------------------------------------");
        return new ContextCandidate("recent-messages-" + run.getRunId(),
                ContextLayer.RECENT_MESSAGES,
                "最近会话消息",
                content.toString(),
                estimate(content.toString()),
                88,
                "conversation",
                run.getConversationId());
    }

    public ContextCandidate conversationSummaryCandidate(AgentRun run, List<AgentMessage> olderMessages) {
        if (olderMessages == null || olderMessages.isEmpty()) {
            return null;
        }
        ConversationSummary summary = summarizeOlderMessages(olderMessages);
        String content = """
                以下是同一会话更早消息的结构化压缩摘要。
                它由历史用户任务和 coder-agent 回复提炼而来，只保留长链路任务承接所需事实；不包含完整原文。
                这些内容只能作为背景，不得覆盖当前任务。

                -历史消息数量：%d
                -用户目标：
                %s
                -已完成事项：
                %s
                -涉及文件：
                %s
                -验证结果：
                %s
                -关键决策/约束：
                %s
                -待注意事项：
                %s
                """.formatted(olderMessages.size(),
                bulletList(summary.userGoals, "无明确历史用户目标"),
                bulletList(summary.completedItems, "无明确历史完成事项"),
                bulletList(summary.files, "无明确历史文件线索"),
                bulletList(summary.verifications, "无明确历史验证结果"),
                bulletList(summary.constraints, "无明确历史决策或约束"),
                bulletList(summary.warnings, "无明确历史注意事项"));
        return new ContextCandidate("conversation-summary-" + run.getRunId(),
                ContextLayer.CONVERSATION_SUMMARY,
                "更早会话摘要",
                content,
                estimate(content),
                82,
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

    public ContextCandidate rawSnippetCandidate(AgentRun run, String filePath, String content) {
        String safePath = filePath == null || filePath.isBlank() ? "unknown" : filePath;
        String snippet = content == null ? "" : content;
        String body = """
                RAW_FILE_SNIPPET
                path=%s
                这是本轮工具刚读取到的当前文件原始片段；涉及该文件的修改、测试或判断时，优先以此为当前事实。

                %s
                """.formatted(safePath, snippet);
        return new ContextCandidate("raw-snippet-" + run.getToolCallCount() + "-" + safePath,
                ContextLayer.RAW_SNIPPET,
                "原始文件片段：" + safePath,
                body,
                estimate(body),
                82,
                "read_file",
                safePath);
    }

    private ContextCandidate required(String id, ContextLayer layer, String title, String content, int priority) {
        return new ContextCandidate(id, layer, title, content, estimate(content), priority, "run", id, true, ContextCutReason.NONE);
    }

    private String workspaceContent(WorkspaceDescriptor workspace) {
        StringBuilder content = new StringBuilder()
                .append("workspaceKey=").append(workspace.workspaceKey())
                .append("\nworkspaceRoot=").append(workspace.rootPath())
                .append("\n项目标识：").append(projectType(workspace.rootPath()))
                .append("\n浅层结构：\n")
                .append(shallowOutline(workspace.rootPath()));
        return content.toString();
    }

    private String projectType(Path root) {
        if (root == null) {
            return "UNKNOWN";
        }
        if (Files.exists(root.resolve("pom.xml"))) {
            return "Maven";
        }
        if (Files.exists(root.resolve("build.gradle")) || Files.exists(root.resolve("build.gradle.kts"))) {
            return "Gradle";
        }
        if (Files.exists(root.resolve("package.json"))) {
            return "Node";
        }
        return "UNKNOWN";
    }

    private String shallowOutline(Path root) {
        if (root == null || !Files.isDirectory(root)) {
            return "null";
        }
        try (Stream<Path> stream = Files.list(root)) {
            List<Path> entries = stream
                    .filter(path -> !ignoredTopLevelName(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .limit(40)
                    .toList();
            List<String> lines = new ArrayList<>();
            int lineBudget = 180;
            for (Path entry : entries) {
                if (lines.size() >= lineBudget) {
                    lines.add("...（结构已截断）");
                    break;
                }
                lines.add(entry.getFileName().toString());
                if (Files.isDirectory(entry)) {
                    List<String> treeLines = sanitizedTree(entry);
                    int remaining = lineBudget - lines.size();
                    if (treeLines.size() > remaining) {
                        lines.addAll(treeLines.subList(0, Math.max(0, remaining)));
                        lines.add("...（" + entry.getFileName() + " 结构已截断）");
                        break;
                    }
                    lines.addAll(treeLines);
                }
            }
            return lines.isEmpty() ? "null" : String.join("\n", lines);
        } catch (IOException e) {
            return "扫描失败：" + e.getMessage();
        }
    }

    private List<String> sanitizedTree(Path directory) {
        return javaTreeLines(directory, "", 8, new int[]{0}, 120);
    }

    private List<String> javaTreeLines(Path directory, String prefix, int maxDepth, int[] counter, int maxLines) {
        if (maxDepth <= 0 || counter[0] >= maxLines) {
            return List.of();
        }
        List<Path> children;
        try (Stream<Path> stream = Files.list(directory)) {
            children = stream
                    .filter(path -> !ignoredTreeName(path.getFileName().toString()))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .toList();
        } catch (IOException e) {
            return List.of("扫描失败：" + e.getMessage());
        }
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < children.size() && counter[0] < maxLines; i++) {
            Path child = children.get(i);
            boolean last = i == children.size() - 1;
            String connector = last ? "└─" : "├─";
            lines.add(prefix + connector + child.getFileName());
            counter[0]++;
            if (Files.isDirectory(child)) {
                lines.addAll(javaTreeLines(child, prefix + (last ? "    " : "│  "), maxDepth - 1, counter, maxLines));
            }
        }
        return lines;
    }

    private boolean ignoredTopLevelName(String name) {
        return ".git".equals(name)
                || ".coder".equals(name)
                || "target".equals(name)
                || "node_modules".equals(name)
                || ".idea".equals(name);
    }

    private boolean ignoredTreeName(String name) {
        return ignoredTopLevelName(name)
                || ".gradle".equals(name)
                || "build".equals(name)
                || "dist".equals(name)
                || "out".equals(name);
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

    private String abbreviate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, max) + "...";
    }

    private ConversationSummary summarizeOlderMessages(List<AgentMessage> messages) {
        ConversationSummary summary = new ConversationSummary();
        for (AgentMessage message : messages) {
            if (message == null || message.getContent() == null || message.getContent().isBlank()) {
                continue;
            }
            String content = message.getContent().strip();
            if ("USER".equals(message.getRole())) {
                addLimited(summary.userGoals, summarizeUserGoal(content), 6);
                collectFilePaths(content, summary.files, 14);
                continue;
            }
            summarizeAgentContent(content, summary);
        }
        return summary;
    }

    private String summarizeUserGoal(String content) {
        String firstLine = firstMeaningfulLine(content);
        return abbreviate(cleanSummaryText(firstLine), 160);
    }

    private void summarizeAgentContent(String content, ConversationSummary summary) {
        collectFilePaths(content, summary.files, 14);
        for (String rawLine : content.split("\\R")) {
            String line = cleanSummaryText(rawLine);
            if (line.isBlank()) {
                continue;
            }
            if (looksLikeCompletion(line)) {
                addLimited(summary.completedItems, abbreviate(line, 180), 7);
            }
            if (looksLikeVerification(line)) {
                addLimited(summary.verifications, abbreviate(line, 180), 6);
            }
            if (looksLikeConstraint(line)) {
                addLimited(summary.constraints, abbreviate(line, 180), 6);
            }
            if (looksLikeWarning(line)) {
                addLimited(summary.warnings, abbreviate(line, 180), 6);
            }
        }
    }

    private void collectFilePaths(String content, Set<String> files, int limit) {
        Matcher matcher = FILE_PATH_PATTERN.matcher(content);
        while (matcher.find() && files.size() < limit) {
            String path = matcher.group()
                    .replace("\\", "/")
                    .replaceAll("^[`'\"（(]+|[`'\"，,。；;：:)）]+$", "");
            if (!path.isBlank()) {
                files.add(path);
            }
        }
    }

    private String firstMeaningfulLine(String content) {
        for (String line : content.split("\\R")) {
            String cleaned = cleanSummaryText(line);
            if (!cleaned.isBlank()) {
                return cleaned;
            }
        }
        return cleanSummaryText(content);
    }

    private String cleanSummaryText(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replaceAll("^#+\\s*", "")
                .replaceAll("^[\\-*>\\s]+", "")
                .replace("|", " ")
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private boolean looksLikeCompletion(String line) {
        return containsAny(line, "已创建", "新建", "已新增", "新增", "已修改", "修改", "已删除", "删除文件",
                "已恢复", "恢复", "已提交", "生成", "任务完成", "完成总结", "变更摘要", "已成功");
    }

    private boolean looksLikeVerification(String line) {
        return containsAny(line, "验证", "测试", "mvn", "test", "PASSED", "FAILED", "通过", "失败", "超时", "编译");
    }

    private boolean looksLikeConstraint(String line) {
        return containsAny(line, "要求", "约束", "必须", "不要", "不能", "权限", "审批", "只读", "默认权限", "完全控制");
    }

    private boolean looksLikeWarning(String line) {
        return containsAny(line, "风险", "注意", "无法", "失败", "被拒绝", "超时", "冲突", "回滚", "撤销", "还原", "减少");
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private void addLimited(Set<String> target, String value, int limit) {
        if (target.size() >= limit || value == null || value.isBlank()) {
            return;
        }
        target.add(value);
    }

    private String bulletList(Set<String> values, String emptyText) {
        if (values.isEmpty()) {
            return "  - " + emptyText;
        }
        return values.stream()
                .map(value -> "  - " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("  - " + emptyText);
    }

    private static class ConversationSummary {
        private final Set<String> userGoals = new LinkedHashSet<>();
        private final Set<String> completedItems = new LinkedHashSet<>();
        private final Set<String> files = new LinkedHashSet<>();
        private final Set<String> verifications = new LinkedHashSet<>();
        private final Set<String> constraints = new LinkedHashSet<>();
        private final Set<String> warnings = new LinkedHashSet<>();
    }
}
