package cn.noname.coder.agent.cases.memory;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.model.adapter.port.IModelGateway;
import cn.noname.coder.agent.domain.model.model.valobj.ModelRequest;
import cn.noname.coder.agent.domain.model.model.valobj.ModelResponse;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolInvocation;
import cn.noname.coder.agent.domain.workspace.model.valobj.ChangedFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 生成不面向用户展示的内部摘要，用于上下文压缩和结构化记忆。
 */
@Slf4j
@Service
public class MemorySummaryService {

    private static final int MODEL_INPUT_LIMIT = 12_000;
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
            "\\b(class|interface|record|enum|public\\s+[\\w<>\\[\\]]+|private\\s+[\\w<>\\[\\]]+|protected\\s+[\\w<>\\[\\]]+)\\s+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern FILE_PATTERN = Pattern.compile(
            "([A-Za-z]:)?[\\\\/\\w.\\-]+\\.(java|xml|md|yml|yaml|json|sql|properties|ts|tsx|js|jsx|vue|py|txt)",
            Pattern.CASE_INSENSITIVE);

    private final ObjectProvider<IModelGateway> modelGatewayProvider;
    private final ObjectMapper objectMapper;

    public MemorySummaryService(ObjectProvider<IModelGateway> modelGatewayProvider) {
        this(modelGatewayProvider, new ObjectMapper());
    }
    
    @Autowired
    MemorySummaryService(ObjectProvider<IModelGateway> modelGatewayProvider, ObjectMapper objectMapper) {
        this.modelGatewayProvider = modelGatewayProvider;
        this.objectMapper = objectMapper;
    }

    public String summarizeRun(AgentRun run) {
        if (run == null) {
            return "";
        }
        String fallback = fallbackRunSummary(run);
        return summarizeWithModel(run,
                "run-summary",
                runSummaryPrompt(run),
                fallback);
    }

    public String summarizeFile(AgentRun run, String filePath, String content) {
        String fallback = fallbackFileSummary(filePath, content);
        return summarizeWithModel(run,
                "file-summary",
                fileSummaryPrompt(filePath, content),
                fallback);
    }

    public String summarizeEdit(AgentRun run, ToolInvocation invocation, ChangedFile changedFile, String content) {
        String fallback = fallbackEditSummary(run, invocation, changedFile);
        return summarizeWithModel(run,
                "edit-summary",
                editSummaryPrompt(run, invocation, changedFile, content),
                fallback);
    }

    private String summarizeWithModel(AgentRun run, String purpose, String prompt, String fallback) {
        IModelGateway gateway = modelGatewayProvider == null ? null : modelGatewayProvider.getIfAvailable();
        if (gateway == null || run == null || !StringUtils.hasText(run.getModel())) {
            return fallback;
        }
        try {
            ModelResponse response = gateway.call(new ModelRequest(
                    run.getRunId() + ":internal-summary:" + purpose,
                    run.getModel(),
                    List.of(prompt),
                    List.of()));
            String text = response == null ? "" : response.finalAnswer();
            String normalized = normalizeModelSummary(text);
            return StringUtils.hasText(normalized) ? normalized : fallback;
        } catch (Exception e) {
            log.warn("内部摘要模型调用降级 runId={} purpose={} reason={}",
                    run.getRunId(), purpose, e.getMessage());
            return fallback;
        }
    }

    private String runSummaryPrompt(AgentRun run) {
        return """
                你是 coder-agent 的内部摘要器。请把本次任务结果压缩成结构化记忆，供后续上下文压缩和召回使用。
                不要复述完整回答，不要输出 Markdown 解释，不要面向用户说话。
                只输出 JSON，对象字段为：
                task_goal: string,
                status: string,
                completed: string[],
                changed_files: string[],
                verification: string[],
                followups: string[],
                risks: string[]

                任务：%s
                状态：%s
                模型：%s
                可见最终回复或失败原因：
                %s
                """.formatted(
                nullToEmpty(run.getTask()),
                run.getStatus(),
                nullToEmpty(run.getModel()),
                abbreviate(nullToEmpty(StringUtils.hasText(run.getFinalAnswer()) ? run.getFinalAnswer() : run.getFailureReason()), MODEL_INPUT_LIMIT));
    }

    private String fileSummaryPrompt(String filePath, String content) {
        return """
                你是 coder-agent 的内部文件摘要器。请把文件内容压缩成结构化文件记忆。
                不要复制文件全文，不要输出 Markdown 解释。
                只输出 JSON，对象字段为：
                file: string,
                language: string,
                purpose: string,
                symbols: string[],
                behavior_summary: string,
                risks: string[]

                文件：%s
                当前文件内容：
                %s
                """.formatted(nullToEmpty(filePath), abbreviate(nullToEmpty(content), MODEL_INPUT_LIMIT));
    }

    private String editSummaryPrompt(AgentRun run, ToolInvocation invocation, ChangedFile changedFile, String content) {
        return """
                你是 coder-agent 的内部变更摘要器。请把本次文件变更压缩成结构化任务记忆。
                不要复制变更后文件全文，不要输出 Markdown 解释。
                只输出 JSON，对象字段为：
                task_goal: string,
                tool: string,
                change_type: string,
                file: string,
                summary: string,
                symbols: string[],
                verification: string[],
                risks: string[]

                用户任务：%s
                工具：%s
                文件：%s
                变更类型：%s
                beforeHash：%s
                afterHash：%s
                相关内容快照：
                %s
                """.formatted(
                run == null ? "" : nullToEmpty(run.getTask()),
                invocation == null ? "" : nullToEmpty(invocation.name()),
                changedFile == null ? "" : nullToEmpty(changedFile.relativePath()),
                changedFile == null ? "" : nullToEmpty(changedFile.changeType()),
                changedFile == null ? "" : nullToEmpty(changedFile.beforeHash()),
                changedFile == null ? "" : nullToEmpty(changedFile.afterHash()),
                abbreviate(nullToEmpty(content), MODEL_INPUT_LIMIT));
    }

    private String normalizeModelSummary(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String json = stripCodeFence(text.strip());
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.has("task_goal") || root.has("file") || root.has("change_type")) {
                return renderJsonSummary(root);
            }
        } catch (Exception ignored) {
            // 模型没有严格输出 JSON 时，使用短文本作为弱摘要。
        }
        return abbreviate(cleanLine(text), 1200);
    }

    private String renderJsonSummary(JsonNode root) {
        List<String> lines = new ArrayList<>();
        appendField(lines, "用户目标", root, "task_goal");
        appendField(lines, "状态", root, "status");
        appendField(lines, "文件", root, "file");
        appendField(lines, "语言", root, "language");
        appendField(lines, "工具", root, "tool");
        appendField(lines, "变更类型", root, "change_type");
        appendField(lines, "用途", root, "purpose");
        appendField(lines, "行为摘要", root, "behavior_summary");
        appendField(lines, "变更摘要", root, "summary");
        appendArray(lines, "完成事项", root, "completed");
        appendArray(lines, "涉及文件", root, "changed_files");
        appendArray(lines, "主要符号", root, "symbols");
        appendArray(lines, "验证结果", root, "verification");
        appendArray(lines, "后续事项", root, "followups");
        appendArray(lines, "风险", root, "risks");
        return String.join("\n", lines);
    }

    private String fallbackRunSummary(AgentRun run) {
        String visibleResult = nullToEmpty(StringUtils.hasText(run.getFinalAnswer()) ? run.getFinalAnswer() : run.getFailureReason());
        return """
                用户目标：%s
                状态：%s
                模型：%s
                完成事项：
                %s
                涉及文件：
                %s
                验证结果：
                %s
                后续事项：
                %s
                风险：
                %s
                """.formatted(
                abbreviate(cleanLine(run.getTask()), 220),
                run.getStatus(),
                nullToEmpty(run.getModel()),
                bullet(extractCompletionLines(visibleResult), "未从最终回复中识别到明确完成事项"),
                bullet(extractFiles(visibleResult, 12), "未识别到明确文件"),
                bullet(extractLines(visibleResult, List.of("测试", "验证", "mvn", "compile", "通过", "失败", "超时"), 6), "未记录验证结果"),
                bullet(extractLines(visibleResult, List.of("后续", "建议", "TODO", "待"), 5), "无"),
                bullet(extractLines(visibleResult, List.of("风险", "注意", "失败", "无法", "超时"), 5), "无"));
    }

    private String fallbackFileSummary(String filePath, String content) {
        return """
                文件：%s
                语言：%s
                用途：%s
                主要符号：
                %s
                行为摘要：%s
                风险：
                - 自动摘要；后续如文件 hash 变化会删除旧记忆并重新生成。
                """.formatted(
                nullToEmpty(filePath),
                detectLanguage(filePath),
                inferFilePurpose(filePath, content),
                bullet(extractSymbols(content, 12), "未识别"),
                inferBehaviorSummary(filePath, content));
    }

    private String fallbackEditSummary(AgentRun run, ToolInvocation invocation, ChangedFile changedFile) {
        String changeType = changedFile == null ? "UNKNOWN" : nullToEmpty(changedFile.changeType());
        return """
                用户目标：%s
                工具：%s
                变更类型：%s
                文件：%s
                变更摘要：%s
                beforeHash=%s
                afterHash=%s
                主要符号：
                - 未展开源码；必要时重新读取当前文件。
                验证结果：
                - 未记录验证结果。
                风险：
                - 文件内容可能已在后续任务中变化，使用前需要 freshness 校验。
                """.formatted(
                run == null ? "" : abbreviate(cleanLine(run.getTask()), 220),
                invocation == null ? "" : nullToEmpty(invocation.name()),
                normalizeChangeType(changeType),
                changedFile == null ? "" : nullToEmpty(changedFile.relativePath()),
                editActionText(changeType),
                changedFile == null ? "" : nullToEmpty(changedFile.beforeHash()),
                changedFile == null ? "" : nullToEmpty(changedFile.afterHash()));
    }

    private void appendField(List<String> lines, String label, JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node != null && node.isValueNode() && StringUtils.hasText(node.asText())) {
            lines.add(label + "：" + cleanLine(node.asText()));
        }
    }

    private void appendArray(List<String> lines, String label, JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isArray() || node.isEmpty()) {
            return;
        }
        lines.add(label + "：");
        for (JsonNode item : node) {
            if (item != null && StringUtils.hasText(item.asText())) {
                lines.add("- " + cleanLine(item.asText()));
            }
        }
    }

    private List<String> extractCompletionLines(String text) {
        List<String> lines = extractLines(text, List.of("完成", "新增", "创建", "修改", "删除", "生成", "提交"), 8);
        if (!lines.isEmpty()) {
            return lines;
        }
        String first = firstMeaningfulLine(text);
        return StringUtils.hasText(first) ? List.of(abbreviate(first, 220)) : List.of();
    }

    private List<String> extractLines(String text, List<String> keywords, int limit) {
        List<String> result = new ArrayList<>();
        for (String line : nullToEmpty(text).split("\\R")) {
            String clean = cleanLine(line);
            if (!StringUtils.hasText(clean)) {
                continue;
            }
            boolean hit = keywords.stream().anyMatch(clean::contains);
            if (hit) {
                result.add(abbreviate(clean, 220));
            }
            if (result.size() >= limit) {
                break;
            }
        }
        return result;
    }

    private List<String> extractFiles(String text, int limit) {
        Set<String> files = new LinkedHashSet<>();
        Matcher matcher = FILE_PATTERN.matcher(nullToEmpty(text));
        while (matcher.find() && files.size() < limit) {
            files.add(matcher.group().replace("\\", "/"));
        }
        return new ArrayList<>(files);
    }

    private List<String> extractSymbols(String content, int limit) {
        Set<String> symbols = new LinkedHashSet<>();
        Matcher matcher = SYMBOL_PATTERN.matcher(nullToEmpty(content));
        while (matcher.find() && symbols.size() < limit) {
            symbols.add(matcher.group(2));
        }
        return new ArrayList<>(symbols);
    }

    private String inferFilePurpose(String filePath, String content) {
        String lower = nullToEmpty(filePath).toLowerCase();
        if (lower.contains("/test/") || lower.endsWith("test.java") || lower.endsWith(".spec.ts")) {
            return "测试文件，记录验证逻辑和断言场景。";
        }
        if (lower.endsWith("controller.java")) {
            return "接口入口或控制器文件。";
        }
        if (lower.endsWith("service.java")) {
            return "业务服务文件。";
        }
        if (lower.endsWith(".java")) {
            return "Java 源码文件。";
        }
        if (lower.endsWith(".md")) {
            return "项目文档。";
        }
        return StringUtils.hasText(content) ? "项目文本文件。" : "用途未识别。";
    }

    private String inferBehaviorSummary(String filePath, String content) {
        List<String> symbols = extractSymbols(content, 5);
        if (!symbols.isEmpty()) {
            return "定义或维护 " + String.join(", ", symbols) + " 等符号的行为。";
        }
        String first = firstMeaningfulLine(content);
        return StringUtils.hasText(first) ? abbreviate(first, 220) : "未识别明确行为。";
    }

    private String detectLanguage(String filePath) {
        String lower = nullToEmpty(filePath).toLowerCase();
        if (lower.endsWith(".java")) return "Java";
        if (lower.endsWith(".ts") || lower.endsWith(".tsx")) return "TypeScript";
        if (lower.endsWith(".js") || lower.endsWith(".jsx")) return "JavaScript";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) return "YAML";
        if (lower.endsWith(".sql")) return "SQL";
        if (lower.endsWith(".xml")) return "XML";
        return "Text";
    }

    private String normalizeChangeType(String changeType) {
        return switch (nullToEmpty(changeType).toUpperCase()) {
            case "ADDED", "CREATE", "CREATED" -> "新增文件";
            case "MODIFIED", "UPDATE", "UPDATED" -> "修改文件";
            case "DELETED", "DELETE", "REMOVED" -> "删除文件";
            default -> "文件变更";
        };
    }

    private String editActionText(String changeType) {
        return switch (nullToEmpty(changeType).toUpperCase()) {
            case "ADDED", "CREATE", "CREATED" -> "新增了该文件。";
            case "MODIFIED", "UPDATE", "UPDATED" -> "修改了该文件。";
            case "DELETED", "DELETE", "REMOVED" -> "删除了该文件。";
            default -> "该文件发生变更。";
        };
    }

    private String bullet(List<String> values, String emptyText) {
        if (values == null || values.isEmpty()) {
            return "- " + emptyText;
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(value -> "- " + value)
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- " + emptyText);
    }

    private String firstMeaningfulLine(String text) {
        for (String line : nullToEmpty(text).split("\\R")) {
            String clean = cleanLine(line);
            if (StringUtils.hasText(clean)) {
                return clean;
            }
        }
        return "";
    }

    private String cleanLine(String text) {
        return nullToEmpty(text)
                .replaceAll("^#+\\s*", "")
                .replaceAll("^[\\-*>\\s]+", "")
                .replace("|", " ")
                .replace("`", "")
                .replaceAll("\\s+", " ")
                .strip();
    }

    private String stripCodeFence(String text) {
        String result = nullToEmpty(text);
        if (result.startsWith("```")) {
            result = result.replaceFirst("^```(?:json)?\\s*", "");
            result = result.replaceFirst("\\s*```$", "");
        }
        return result.strip();
    }

    private String abbreviate(String text, int maxChars) {
        String safe = nullToEmpty(text);
        if (safe.length() <= maxChars) {
            return safe;
        }
        return safe.substring(0, Math.max(0, maxChars));
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
