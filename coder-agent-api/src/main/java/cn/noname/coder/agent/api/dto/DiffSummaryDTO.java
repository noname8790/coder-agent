package cn.noname.coder.agent.api.dto;

import java.util.List;

public record DiffSummaryDTO(
        int totalFiles,
        int totalAddedLines,
        int totalDeletedLines,
        List<DiffFileDTO> files
) {
    public static DiffSummaryDTO empty() {
        return new DiffSummaryDTO(0, 0, 0, List.of());
    }
}
