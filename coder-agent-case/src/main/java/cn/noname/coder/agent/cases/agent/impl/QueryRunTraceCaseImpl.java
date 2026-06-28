package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.TraceEventDTO;
import cn.noname.coder.agent.api.dto.TraceQueryResponseDTO;
import cn.noname.coder.agent.cases.agent.IQueryRunTraceCase;
import cn.noname.coder.agent.domain.agent.adapter.port.IArtifactPort;
import cn.noname.coder.agent.domain.workspace.adapter.port.IWorkspacePort;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceDescriptor;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * trace 查询用例，从 workspace 的 trace.jsonl 回放事件。
 */
@Service
@RequiredArgsConstructor
public class QueryRunTraceCaseImpl implements IQueryRunTraceCase {

    private final IAgentRunRepository runRepository;
    private final IWorkspacePort workspacePort;
    private final IArtifactPort artifactPort;

    @Override
    public TraceQueryResponseDTO queryTrace(String runId) {
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        WorkspaceDescriptor workspace = workspacePort.resolve(run.getWorkspaceKey())
                .orElseThrow(() -> new AppException("WORKSPACE_NOT_FOUND", "workspaceKey 未配置：" + run.getWorkspaceKey()));
        return new TraceQueryResponseDTO(runId, artifactPort.readTrace(workspace, runId).stream().map(TraceEventDTO::new).toList());
    }
}
