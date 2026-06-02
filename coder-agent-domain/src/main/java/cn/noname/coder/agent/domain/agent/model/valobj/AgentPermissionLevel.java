package cn.noname.coder.agent.domain.agent.model.valobj;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 第三版运行权限等级。它替代用户侧 capability 勾选，作为工具可见性和执行边界。
 */
public enum AgentPermissionLevel {
    L1_READ_ONLY(
            "只读分析",
            "只能读取、搜索、查看 Git 状态并生成结论。",
            List.of("读取文件", "搜索文本", "列出目录", "Git 只读命令"),
            List.of("新增文件", "修改文件", "覆盖文件", "删除文件", "运行测试/构建", "本地提交", "PR 草稿"),
            "低风险：不会修改本地仓库。",
            Set.of("list_files", "read_file", "search_text", "run_shell")
    ),
    L2_SAFE_EDIT(
            "安全编辑",
            "允许新增文件、基于 search/replace 修改文本文件，并运行测试或构建。",
            List.of("L1 全部能力", "新增文件", "安全修改文件", "运行测试", "运行构建"),
            List.of("覆盖文件", "删除文件", "本地提交", "PR 草稿", "git push"),
            "中风险：会修改工作区文件，但禁止删除、覆盖和提交。",
            Set.of("list_files", "read_file", "search_text", "run_shell", "write_file", "apply_patch")
    ),
    L3_REPO_WRITE(
            "仓库写入",
            "允许覆盖/删除文件、本地分支、本地 add/commit，并生成 PR 草稿和回滚材料。",
            List.of("L2 全部能力", "覆盖文件", "删除文件", "本地分支", "git add", "git commit", "PR 草稿"),
            List.of("git push", "git clean", "git reset --hard", "远程 PR 创建"),
            "高风险：会删除或覆盖文件，并可能创建本地提交；仍禁止远程推送和强清理。",
            Set.of("list_files", "read_file", "search_text", "run_shell", "write_file", "apply_patch", "overwrite_file", "delete_file", "generate_pr_draft")
    ),
    L4_DANGEROUS_LOCAL(
            "高危本地操作",
            "预留等级，第三版不默认开放。",
            List.of("预留"),
            List.of("第三版默认全部禁用"),
            "高危预留：当前版本不应被普通运行使用。",
            Set.of()
    );

    private final String displayName;
    private final String description;
    private final List<String> allowedFeatures;
    private final List<String> forbiddenFeatures;
    private final String riskNotice;
    private final Set<String> advertisedTools;

    AgentPermissionLevel(String displayName,
                         String description,
                         List<String> allowedFeatures,
                         List<String> forbiddenFeatures,
                         String riskNotice,
                         Set<String> advertisedTools) {
        this.displayName = displayName;
        this.description = description;
        this.allowedFeatures = allowedFeatures;
        this.forbiddenFeatures = forbiddenFeatures;
        this.riskNotice = riskNotice;
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

    public boolean canAdvertise(String toolName) {
        return advertisedTools.contains(toolName);
    }

    public boolean atLeast(AgentPermissionLevel other) {
        return ordinal() >= other.ordinal();
    }

    public static AgentPermissionLevel parse(String value) {
        if (value == null || value.isBlank()) {
            return L1_READ_ONLY;
        }
        return AgentPermissionLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
    }
}
