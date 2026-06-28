package cn.noname.coder.agent.domain.tool.adapter.repository;

import cn.noname.coder.agent.domain.tool.model.entity.ToolApprovalRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IToolApprovalRepository {

    void save(ToolApprovalRequest request);

    void update(ToolApprovalRequest request);

    Optional<ToolApprovalRequest> findByApprovalId(String approvalId);

    List<ToolApprovalRequest> listPending(String runId);

    default Optional<ToolApprovalRequest> findPending(String runId, String toolName, String argumentsJson) {
        return Optional.empty();
    }

    Optional<ToolApprovalRequest> findApproved(String runId, String toolName, String argumentsJson);

    default List<ToolApprovalRequest> listApproved(String runId) {
        return List.of();
    }

    default List<ToolApprovalRequest> listApprovedPendingExecution(String runId) {
        return List.of();
    }

    default void markExecuted(String approvalId) {
    }

    List<ToolApprovalRequest> listRejectedPendingReturn(String runId);

    void markReturned(String approvalId);

    default void deleteByRunIds(Collection<String> runIds) {
    }
}
