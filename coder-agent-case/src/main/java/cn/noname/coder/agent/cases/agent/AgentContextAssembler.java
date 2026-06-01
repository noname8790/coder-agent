package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;

import java.util.ArrayList;
import java.util.List;

/**
 * 组装首版基础上下文，控制进入模型的内容规模。
 */
public class AgentContextAssembler {

    public List<String> initialMessages(AgentRun run, WorkspaceDescriptor workspace) {
        List<String> messages = new ArrayList<>();
        messages.add("""
                你是 coder-agent，本次任务只能围绕当前 workspace 工作。
                工具列表已经按本次运行模式和 workspace 能力过滤；不要尝试调用未提供的工具。
                第二版禁止 git commit、push、reset、branch、删除文件和自动创建 PR；最终回答必须给出清晰结论、变更摘要和后续建议。
                """);
        messages.add("workspaceKey=" + workspace.workspaceKey() + ", workspaceRoot=" + workspace.rootPath());
        messages.add("runMode=" + (run.getMode() == null ? "READ_ONLY" : run.getMode().name())
                + ", workspaceCapabilities=" + workspace.capabilities());
        if (run.getMode() != null && "EDIT".equals(run.getMode().name())) {
            messages.add("""
                    EDIT 模式说明：
                    - apply_patch 只能通过 search/replace 修改已有文本文件。
                    - write_file 只能新建文件，不能覆盖已有文件。
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
