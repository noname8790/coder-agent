package cn.noname.coder.agent.domain.agent.model.valobj;

/**
 * 单个文件变更摘要。
 */
public record ChangedFile(
        String relativePath,
        String changeType,
        String beforeHash,
        String afterHash,
        Integer toolCallNo,
        String beforeContent,
        String afterContent
) {
}
