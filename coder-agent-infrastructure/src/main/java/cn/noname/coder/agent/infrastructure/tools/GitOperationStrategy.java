package cn.noname.coder.agent.infrastructure.tools;

import cn.noname.coder.agent.domain.tool.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolApprovalPolicy;
import cn.noname.coder.agent.domain.tool.model.valobj.ToolRiskLevel;

import java.util.Arrays;
import java.util.Optional;

/**
 * Git 命令策略，集中维护风险、审批和权限判定。
 */
public enum GitOperationStrategy {
    STATUS("status", ToolRiskLevel.READ_ONLY, AgentPermissionLevel.READ_ONLY, ToolApprovalPolicy.NEVER, false),
    DIFF("diff", ToolRiskLevel.READ_ONLY, AgentPermissionLevel.READ_ONLY, ToolApprovalPolicy.NEVER, false),
    LOG("log", ToolRiskLevel.READ_ONLY, AgentPermissionLevel.READ_ONLY, ToolApprovalPolicy.NEVER, false),
    INIT("init", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    ADD("add", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    COMMIT("commit", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    RESET("reset", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    RM("rm", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    CLEAN("clean", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    RESTORE("restore", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    CHECKOUT_BRANCH("checkout -b", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true),
    PR_DRAFT("pr draft", ToolRiskLevel.HIGH_RISK, AgentPermissionLevel.DEFAULT, ToolApprovalPolicy.HIGH_RISK_ONLY, true);

    private final String commandPrefix;
    private final ToolRiskLevel riskLevel;
    private final AgentPermissionLevel requiredPermission;
    private final ToolApprovalPolicy approvalPolicy;
    private final boolean writesRepository;

    GitOperationStrategy(String commandPrefix,
                         ToolRiskLevel riskLevel,
                         AgentPermissionLevel requiredPermission,
                         ToolApprovalPolicy approvalPolicy,
                         boolean writesRepository) {
        this.commandPrefix = commandPrefix;
        this.riskLevel = riskLevel;
        this.requiredPermission = requiredPermission;
        this.approvalPolicy = approvalPolicy;
        this.writesRepository = writesRepository;
    }

    public ToolRiskLevel riskLevel() {
        return riskLevel;
    }

    public AgentPermissionLevel requiredPermission() {
        return requiredPermission;
    }

    public ToolApprovalPolicy approvalPolicy() {
        return approvalPolicy;
    }

    public boolean writesRepository() {
        return writesRepository;
    }

    public static Optional<GitOperationStrategy> fromShellCommand(String command) {
        String normalized = normalize(command);
        if (!normalized.equals("git") && !normalized.startsWith("git ")) {
            return Optional.empty();
        }
        String operation = normalized.substring("git".length()).trim();
        return Arrays.stream(values())
                .filter(strategy -> operation.equals(strategy.commandPrefix)
                        || operation.startsWith(strategy.commandPrefix + " "))
                .findFirst();
    }

    public static boolean isGitWriteCommand(String command) {
        return fromShellCommand(command).map(GitOperationStrategy::writesRepository).orElse(false);
    }

    public static Optional<GitOperationStrategy> fromToolName(String toolName) {
        return switch (toolName == null ? "" : toolName) {
            case "git_status" -> Optional.of(STATUS);
            case "git_diff" -> Optional.of(DIFF);
            case "git_log" -> Optional.of(LOG);
            case "git_add" -> Optional.of(ADD);
            case "git_commit" -> Optional.of(COMMIT);
            case "generate_pr_draft" -> Optional.of(PR_DRAFT);
            default -> Optional.empty();
        };
    }

    private static String normalize(String command) {
        return command == null ? "" : command.trim().toLowerCase();
    }
}
