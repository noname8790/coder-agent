package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.*;
import cn.noname.coder.agent.domain.tool.model.entity.ToolCall;

import java.util.Collection;
import java.util.List;

/**
 * 执行记录仓储集中处理步骤、模型调用、工具调用、审计事件和工件索引。
 */
public interface IAgentRecordRepository {

    void saveStep(AgentStep step);

    void saveModelCall(ModelCall modelCall);

    void saveToolCall(ToolCall toolCall);

    void saveAuditEvent(AuditEvent event);

    void saveArtifact(RunArtifact artifact);

    List<AuditEvent> listAuditEvents(String runId);

    List<RunArtifact> listArtifacts(String runId);

    default void deleteByRunIds(Collection<String> runIds) {
    }
}
