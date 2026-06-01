package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.entity.RunArtifact;
import cn.noname.coder.agent.domain.agent.model.valobj.ChangedFile;
import cn.noname.coder.agent.domain.agent.model.valobj.TestCommandReport;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceDescriptor;

import java.util.List;
import java.util.Map;

/**
 * 工件端口负责 .coder/runs/{runId} 文件创建和读取。
 */
public interface IArtifactPort {

    List<RunArtifact> initializeRun(WorkspaceDescriptor workspace, AgentRun run);

    RunArtifact appendTrace(WorkspaceDescriptor workspace, String runId, Map<String, Object> event);

    RunArtifact writeContextSnapshot(WorkspaceDescriptor workspace, String runId, int callNo, Map<String, Object> snapshot);

    RunArtifact writeToolOutput(WorkspaceDescriptor workspace, String runId, int callNo, String output);

    RunArtifact writeFinalResult(WorkspaceDescriptor workspace, AgentRun run, Map<String, Object> result);

    default List<RunArtifact> writeReviewArtifacts(WorkspaceDescriptor workspace,
                                                   AgentRun run,
                                                   List<ChangedFile> changedFiles,
                                                   List<TestCommandReport> testReports) {
        return List.of();
    }

    List<Map<String, Object>> readTrace(WorkspaceDescriptor workspace, String runId);
}
