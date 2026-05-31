package cn.noname.coder.agent.types.enums;

/**
 * 运行工件类型，对应 workspace 下 .coder/runs/{runId} 中的文件。
 */
public enum ArtifactType {
    RUN_META,
    TRACE,
    CONTEXT_SNAPSHOT,
    TOOL_OUTPUT,
    FINAL_RESULT
}
