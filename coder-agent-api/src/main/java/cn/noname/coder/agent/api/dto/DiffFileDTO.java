package cn.noname.coder.agent.api.dto;

public record DiffFileDTO(
        String path,
        String changeType,
        int addedLines,
        int deletedLines,
        String patchSnippet
) {
}
