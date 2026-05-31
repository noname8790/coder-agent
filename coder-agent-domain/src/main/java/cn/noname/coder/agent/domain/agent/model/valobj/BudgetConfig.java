package cn.noname.coder.agent.domain.agent.model.valobj;

/**
 * 单次运行预算配置，首版采用保守默认值防止长链路失控。
 */
public record BudgetConfig(int maxSteps, int maxModelCalls, int maxToolCalls, int timeoutSeconds) {

    public static BudgetConfig defaults() {
        return new BudgetConfig(20, 8, 16, 300);
    }
}
