package cn.noname.coder.agent.api.dto;

public record DiffFileDTO(
        String path,
        String changeType,
        int addedLines,
        int deletedLines,
        String patchSnippet,
        Boolean reversible,
        String irreversibleReason
) {
    public DiffFileDTO(String path, String changeType, int addedLines, int deletedLines, String patchSnippet) {
        this(path, changeType, addedLines, deletedLines, patchSnippet, true, null);
    }
}
