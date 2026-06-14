package cn.noname.coder.agent.domain.agent.model.valobj;

public record ContextCandidate(
        String candidateId,
        ContextLayer layer,
        String title,
        String content,
        int estimatedTokens,
        int priority,
        String sourceType,
        String sourceId,
        boolean required,
        ContextCutReason cutReason
) {
    public ContextCandidate(String candidateId,
                            ContextLayer layer,
                            String title,
                            String content,
                            int estimatedTokens,
                            int priority,
                            String sourceType,
                            String sourceId) {
        this(candidateId, layer, title, content, estimatedTokens, priority, sourceType, sourceId, false, ContextCutReason.NONE);
    }

    public ContextCandidate withCutReason(ContextCutReason reason) {
        return new ContextCandidate(candidateId, layer, title, content, estimatedTokens, priority, sourceType, sourceId, required, reason);
    }
}
