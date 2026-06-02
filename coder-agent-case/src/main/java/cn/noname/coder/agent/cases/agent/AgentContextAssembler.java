package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentPermissionLevel;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装进入模型的基础上下文。
 */
public class AgentContextAssembler {

    public List<String> initialMessages(AgentRun run, WorkspaceDescriptor workspace) {
        List<String> messages = new ArrayList<>();
        messages.add("""
                你是 coder-agent，本次任务只能围绕当前 workspace 工作。
                工具列表已经按本次权限等级过滤；不要尝试调用未提供的工具。
                第三版仍禁止 git push、git clean、git reset --hard 和远程 PR 创建；最终回答必须给出清晰结论、变更摘要和后续建议。
                """);
        messages.add("workspaceKey=" + workspace.workspaceKey() + ", workspaceRoot=" + workspace.rootPath());
        messages.add("permissionLevel=" + (run.getPermissionLevel() == null ? "L1_READ_ONLY" : run.getPermissionLevel().name()));
        if (run.getPermissionLevel() != null) {
            messages.add("权限等级说明：" + run.getPermissionLevel().displayName()
                    + "；开放功能=" + run.getPermissionLevel().allowedFeatures()
                    + "；禁止功能=" + run.getPermissionLevel().forbiddenFeatures()
                    + "；风险提示=" + run.getPermissionLevel().riskNotice());
        }
        if (run.getPermissionLevel() != null && run.getPermissionLevel().atLeast(AgentPermissionLevel.L2_SAFE_EDIT)) {
            messages.add("""
                    L2/L3 权限说明：
                    - apply_patch 只能通过 search/replace 修改已有文本文件。
                    - write_file 只能新建文件，不能覆盖已有文件。
                    - overwrite_file 和 delete_file 只在 L3_REPO_WRITE 中允许。
                    - 本地 git add/commit/checkout -b 只在 L3_REPO_WRITE 中允许。
                    - 修改后优先运行可用测试/构建命令，并根据结果继续修复或总结。
                    """);
        }
        messages.add("用户任务：" + run.getTask());
        return messages;
    }

    public String budgetLine(AgentRun run) {
        return "预算使用：steps=" + run.getStepCount() + "/" + run.getMaxSteps()
                + ", model_calls=" + run.getModelCallCount() + "/" + run.getMaxModelCalls()
                + ", tool_calls=" + run.getToolCallCount() + "/" + run.getMaxToolCalls();
    }
}
