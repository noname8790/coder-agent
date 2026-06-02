package cn.noname.coder.agent.infrastructure.adapter.repository;

import cn.noname.coder.agent.domain.agent.model.entity.Workspace;
import cn.noname.coder.agent.domain.agent.model.valobj.WorkspaceStatus;
import cn.noname.coder.agent.infrastructure.dao.IWorkspaceDao;
import cn.noname.coder.agent.infrastructure.dao.po.WorkspacePO;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class WorkspaceRepositoryTest {

    @Test
    void shouldClearDeletedAtGivenReactivatedWorkspace() {
        // Given 一个被重新激活的 workspace，deletedAt 已被置空
        IWorkspaceDao workspaceDao = mock(IWorkspaceDao.class);
        WorkspaceRepository repository = new WorkspaceRepository(workspaceDao);
        Workspace workspace = Workspace.builder()
                .id(1L)
                .workspaceKey("demo")
                .rootPath(Path.of("E:/demo"))
                .status(WorkspaceStatus.ACTIVE)
                .createdAt(LocalDateTime.now().minusDays(1))
                .updatedAt(LocalDateTime.now())
                .deletedAt(null)
                .build();

        // When 更新 workspace
        repository.update(workspace);

        // Then SQL 更新必须显式包含 deleted_at，确保数据库中旧值被清空
        @SuppressWarnings("unchecked")
        ArgumentCaptor<UpdateWrapper<WorkspacePO>> wrapperCaptor = ArgumentCaptor.forClass(UpdateWrapper.class);
        verify(workspaceDao).update(any(WorkspacePO.class), wrapperCaptor.capture());
        assertTrue(wrapperCaptor.getValue().getSqlSet().contains("deleted_at"));
    }
}
