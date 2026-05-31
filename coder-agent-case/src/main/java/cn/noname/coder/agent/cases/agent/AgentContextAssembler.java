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
                你是 coder-agent，本次任务只能围绕服务端配置的本地 workspace 工作。
                首版只能使用 list_files、read_file、search_text、run_shell 四类受限工具。
                不要尝试编辑文件、提交 Git、push 或生成 PR；最终回答必须给出清晰结论和后续建议。
                """);
        messages.add("workspaceKey=" + workspace.workspaceKey() + ", workspaceRoot=" + workspace.rootPath());
        messages.add("用户任务：" + run.getTask());
        return messages;
    }

    public String budgetLine(AgentRun run) {
        return "预算使用：steps=" + run.getStepCount() + "/" + run.getMaxSteps()
                + ", model_calls=" + run.getModelCallCount() + "/" + run.getMaxModelCalls()
                + ", tool_calls=" + run.getToolCallCount() + "/" + run.getMaxToolCalls();
    }
}
