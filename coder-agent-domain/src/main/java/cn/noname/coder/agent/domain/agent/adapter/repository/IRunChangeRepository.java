package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.RunChangeSet;
import cn.noname.coder.agent.domain.agent.model.entity.RunFileChange;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Run 级变更集仓储端口。
 */
public interface IRunChangeRepository {

    void saveChangeSet(RunChangeSet changeSet, List<RunFileChange> files);

    Optional<RunChangeSet> findChangeSet(String runId);

    List<RunFileChange> listFileChanges(String runId);

    List<RunChangeSet> listByConversationId(String conversationId);

    void updateChangeSet(RunChangeSet changeSet);

    default void deleteByRunIds(Collection<String> runIds) {
    }
}
