package cn.noname.coder.agent.domain.workspace.model.entity;

import cn.noname.coder.agent.domain.workspace.model.valobj.WorkspaceStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.nio.file.Path;
import java.time.LocalDateTime;

/**
 * 用户注册的本地项目 workspace。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Workspace {

    private Long id;
    private String workspaceKey;
    private Path rootPath;
    private WorkspaceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
