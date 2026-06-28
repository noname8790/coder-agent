package cn.noname.coder.agent.domain.tool.model.valobj;

/**
 * 测试/构建命令执行报告。
 */
public record TestCommandReport(
        String command,
        Integer exitCode,
        Long durationMs,
        String status,
        String summary
) {
}
