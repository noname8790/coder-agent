package cn.noname.coder.agent.domain.context.model.valobj;

public enum ContextCutReason {
    NONE,
    DEDUPLICATED,
    RULE_COMPRESSED,
    MODEL_COMPRESSED,
    LOW_TRUST,
    STALE,
    LAYER_BUDGET_EXCEEDED,
    INPUT_BUDGET_EXCEEDED
}
