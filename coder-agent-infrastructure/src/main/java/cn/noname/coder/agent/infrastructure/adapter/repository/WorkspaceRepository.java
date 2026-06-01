package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.adapter.repository.IWorkspaceRepository;
import cn.noname.coder.agent.domain.agent.model.entity.Workspace;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceCapability;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceStatus;
import cn.noname.coder.agent.infrastructure.dao.IWorkspaceDao;
import cn.noname.coder.agent.infrastructure.dao.po.WorkspacePO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * workspace MySQL 仓储实现。
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class WorkspaceRepository implements IWorkspaceRepository {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };

    private final IWorkspaceDao workspaceDao;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    public void save(Workspace workspace) {
        WorkspacePO po = toPo(workspace);
        workspaceDao.insert(po);
        workspace.setId(po.getId());
        log.info("Workspace 已入库 workspaceKey={} status={}",
                workspace.getWorkspaceKey(), workspace.getStatus());
    }

    @Override
    public void update(Workspace workspace) {
        WorkspacePO po = toPo(workspace);
        workspaceDao.update(po, new UpdateWrapper<WorkspacePO>()
                .eq("id", workspace.getId())
                .set("deleted_at", workspace.getDeletedAt()));
        log.info("Workspace 已更新 workspaceKey={} status={}",
                workspace.getWorkspaceKey(), workspace.getStatus());
    }

    @Override
    public Optional<Workspace> findActiveByWorkspaceKey(String workspaceKey) {
        return Optional.ofNullable(workspaceDao.selectOne(new LambdaQueryWrapper<WorkspacePO>()
                .eq(WorkspacePO::getWorkspaceKey, workspaceKey)
                .eq(WorkspacePO::getStatus, WorkspaceStatus.ACTIVE.name())))
                .map(this::toEntity);
    }

    @Override
    public Optional<Workspace> findByWorkspaceKey(String workspaceKey) {
        return Optional.ofNullable(workspaceDao.selectOne(new LambdaQueryWrapper<WorkspacePO>()
                .eq(WorkspacePO::getWorkspaceKey, workspaceKey)))
                .map(this::toEntity);
    }

    @Override
    public List<Workspace> listActive() {
        return workspaceDao.selectList(new LambdaQueryWrapper<WorkspacePO>()
                        .eq(WorkspacePO::getStatus, WorkspaceStatus.ACTIVE.name())
                        .orderByDesc(WorkspacePO::getUpdatedAt))
                .stream().map(this::toEntity).toList();
    }

    @Override
    public boolean existsByWorkspaceKey(String workspaceKey) {
        return workspaceDao.selectCount(new LambdaQueryWrapper<WorkspacePO>()
                .eq(WorkspacePO::getWorkspaceKey, workspaceKey)) > 0;
    }

    @SneakyThrows
    private WorkspacePO toPo(Workspace workspace) {
        WorkspacePO po = new WorkspacePO();
        po.setId(workspace.getId());
        po.setWorkspaceKey(workspace.getWorkspaceKey());
        po.setRootPath(workspace.getRootPath().toString());
        po.setCapabilities(objectMapper.writeValueAsString(workspace.getCapabilities().stream().map(Enum::name).toList()));
        po.setStatus(workspace.getStatus().name());
        po.setCreatedAt(workspace.getCreatedAt());
        po.setUpdatedAt(workspace.getUpdatedAt());
        po.setDeletedAt(workspace.getDeletedAt());
        return po;
    }

    @SneakyThrows
    private Workspace toEntity(WorkspacePO po) {
        List<WorkspaceCapability> capabilities = objectMapper.readValue(po.getCapabilities(), STRING_LIST)
                .stream().map(WorkspaceCapability::valueOf).toList();
        return Workspace.builder()
                .id(po.getId())
                .workspaceKey(po.getWorkspaceKey())
                .rootPath(Path.of(po.getRootPath()).toAbsolutePath().normalize())
                .capabilities(capabilities)
                .status(WorkspaceStatus.valueOf(po.getStatus()))
                .createdAt(po.getCreatedAt())
                .updatedAt(po.getUpdatedAt())
                .deletedAt(po.getDeletedAt())
                .build();
    }
}
