package cn.noname.coder.agent.domain.agent.model.valobj;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 面向用户暴露的 Agent 权限等级。
 */
public enum AgentPermissionLevel {
    READ_ONLY(
            "只读",
            "读取仓库、搜索文本、查看 Git 状态并生成分析结论，不修改本地文件。",
            List.of("读取文件", "搜索文本", "列出目录", "Git 只读命令", "生成分析结论"),
            List.of("新增文件", "修改文件", "覆盖文件", "删除文件", "运行测试/构建", "本地提交", "PR 草稿"),
            "低风险：不会修改本地仓库。",
            "book-open",
            false,
            Set.of("list_files", "read_file", "search_text", "run_shell", "git_status", "git_diff", "git_log")
    ),
    DEFAULT(
            "默认",
            "允许常规仓库任务；删除、覆盖、Git 写入、PR 草稿和本地提交等高风险动作需要审批。",
            List.of("只读全部能力", "新增文件", "修改文件", "覆盖文件", "删除文件", "运行测试/构建", "Git 本地操作", "PR 草稿"),
            List.of("git push", "git clean", "git reset --hard", "远程 PR 创建"),
            "中风险：可修改仓库；检测到高风险动作时会请求审批。",
            "shield-check",
            true,
            Set.of("list_files", "read_file", "search_text", "run_shell", "write_file", "apply_patch",
                    "overwrite_file", "delete_file", "generate_pr_draft", "git_status", "git_diff", "git_log",
                    "git_add", "git_commit")
    ),
    FULL_ACCESS(
            "完全控制",
            "解锁 workspace 内全部本地仓库操作，高风险动作不再请求审批。",
            List.of("默认全部能力", "无需审批的删除/覆盖", "无需审批的 Git 写入", "无需审批的本地提交", "PR 草稿"),
            List.of("workspace 外路径", "受保护路径", "危险命令", "远程 push/API"),
            "高风险：不会为本地高风险操作请求审批，请确认你信任当前任务。",
            "shield-alert",
            true,
            Set.of("list_files", "read_file", "search_text", "run_shell", "write_file", "apply_patch",
                    "overwrite_file", "delete_file", "generate_pr_draft", "git_status", "git_diff", "git_log",
                    "git_add", "git_commit")
    );

    private final String displayName;
    private final String description;
    private final List<String> allowedFeatures;
    private final List<String> forbiddenFeatures;
    private final String riskNotice;
    private final String icon;
    private final boolean dangerous;
    private final Set<String> advertisedTools;

    AgentPermissionLevel(String displayName,
                         String description,
                         List<String> allowedFeatures,
                         List<String> forbiddenFeatures,
                         String riskNotice,
                         String icon,
                         boolean dangerous,
                         Set<String> advertisedTools) {
        this.displayName = displayName;
        this.description = description;
        this.allowedFeatures = allowedFeatures;
        this.forbiddenFeatures = forbiddenFeatures;
        this.riskNotice = riskNotice;
        this.icon = icon;
        this.dangerous = dangerous;
        this.advertisedTools = advertisedTools;
    }

    public String displayName() {
        return displayName;
    }

    public String description() {
        return description;
    }

    public List<String> allowedFeatures() {
        return allowedFeatures;
    }

    public List<String> forbiddenFeatures() {
        return forbiddenFeatures;
    }

    public String riskNotice() {
        return riskNotice;
    }

    public String icon() {
        return icon;
    }

    public boolean dangerous() {
        return dangerous;
    }

    public boolean canAdvertise(String toolName) {
        return advertisedTools.contains(toolName);
    }

    public boolean atLeast(AgentPermissionLevel other) {
        return ordinal() >= other.ordinal();
    }

    public boolean requiresApprovalForHighRiskTool() {
        return this == DEFAULT;
    }

    public boolean bypassesApproval() {
        return this == FULL_ACCESS;
    }

    public static AgentPermissionLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        return AgentPermissionLevel.valueOf(normalized);
    }
}
