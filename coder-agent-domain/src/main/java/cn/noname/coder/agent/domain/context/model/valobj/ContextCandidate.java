package cn.noname.coder.agent.domain.context.model.valobj;

import java.util.List;

public record ContextCandidate(
        String candidateId,
        ContextLayer layer,
        String title,
        String content,
        int estimatedTokens,
        int priority,
        String sourceType,
        String sourceId,
        String scope,
        String freshnessStatus,
        double trustScore,
        boolean compressible,
        List<String> evidenceRefs,
        boolean required,
        ContextCutReason cutReason
) {
    public ContextCandidate {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        freshnessStatus = freshnessStatus == null ? "UNKNOWN" : freshnessStatus;
        scope = scope == null ? "" : scope;
        cutReason = cutReason == null ? ContextCutReason.NONE : cutReason;
    }

    public ContextCandidate(String candidateId,
                            ContextLayer layer,
                            String title,
                            String content,
                            int estimatedTokens,
                            int priority,
                            String sourceType,
                            String sourceId) {
        this(candidateId, layer, title, content, estimatedTokens, priority, sourceType, sourceId,
                "", "UNKNOWN", 1.0, true, List.of(), false, ContextCutReason.NONE);
    }

    public ContextCandidate(String candidateId,
                            ContextLayer layer,
                            String title,
                            String content,
                            int estimatedTokens,
                            int priority,
                            String sourceType,
                            String sourceId,
                            boolean required,
                            ContextCutReason cutReason) {
        this(candidateId, layer, title, content, estimatedTokens, priority, sourceType, sourceId,
                "", "UNKNOWN", 1.0, true, List.of(), required, cutReason);
    }

    public ContextCandidate withCutReason(ContextCutReason reason) {
        return new ContextCandidate(candidateId, layer, title, content, estimatedTokens, priority, sourceType, sourceId,
                scope, freshnessStatus, trustScore, compressible, evidenceRefs, required, reason);
    }
}
