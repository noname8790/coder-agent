package cn.noname.coder.agent.domain.agent.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.Workspace;

import java.util.List;
import java.util.Optional;

/**
 * workspace 仓储端口。
 */
public interface IWorkspaceRepository {

    void save(Workspace workspace);

    void update(Workspace workspace);

    Optional<Workspace> findActiveByWorkspaceKey(String workspaceKey);

    Optional<Workspace> findByWorkspaceKey(String workspaceKey);

    List<Workspace> listActive();

    boolean existsByWorkspaceKey(String workspaceKey);
}
