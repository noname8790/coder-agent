package cn.noname.coder.agent.domain.agent.model.valobj;

import java.util.List;

/**
 * workspace 能力枚举，面向用户暴露为易懂的能力选项。
 */
public enum WorkspaceCapability {
    READ_REPOSITORY,
    GIT_READ,
    RUN_TEST,
    RUN_BUILD,
    ADD_FILE,
    MODIFY_FILE;

    public static List<WorkspaceCapability> conservativeDefaults() {
        return List.of(READ_REPOSITORY, GIT_READ, RUN_TEST);
    }
}
