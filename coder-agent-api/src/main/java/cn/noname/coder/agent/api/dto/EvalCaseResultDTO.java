package cn.noname.coder.agent.api.dto;

public record EvalCaseResultDTO(
        String benchmarkId,
        String runId,
        String modelKey,
        String status,
        Boolean passed,
        Integer attempts,
        Integer modelCalls,
        Integer toolCalls,
        Integer toolSteps,
        Long durationMs,
        String failureCategory,
        Double contextCompressionRatio,
        Integer memoryHitCount,
        Double retainedAnchorRate,
        Double memoryRecallPrecision,
        Double memoryRecallAtK,
        Double staleBlockRate,
        Integer repeatedReadCount,
        Long tokenCost,
        String resultPath
) {
}
