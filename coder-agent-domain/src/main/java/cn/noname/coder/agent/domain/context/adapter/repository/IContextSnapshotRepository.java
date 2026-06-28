package cn.noname.coder.agent.domain.context.adapter.repository;

import cn.noname.coder.agent.domain.context.model.entity.ContextSnapshot;

import java.util.Collection;
import java.util.List;

public interface IContextSnapshotRepository {

    void save(ContextSnapshot snapshot);

    List<ContextSnapshot> listByRunId(String runId);

    default void deleteByRunIds(Collection<String> runIds) {
    }
}
