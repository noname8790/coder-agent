package cn.noname.coder.agent.api.dto;

import java.util.List;

public record DiffSummaryDTO(
        int totalFiles,
        int totalAddedLines,
        int totalDeletedLines,
        List<DiffFileDTO> files,
        String changeSetStatus,
        Boolean reversible
) {
    public static DiffSummaryDTO empty() {
        return new DiffSummaryDTO(0, 0, 0, List.of(), null, false);
    }

    public DiffSummaryDTO(int totalFiles, int totalAddedLines, int totalDeletedLines, List<DiffFileDTO> files) {
        this(totalFiles, totalAddedLines, totalDeletedLines, files, null, files != null && !files.isEmpty());
    }
}
