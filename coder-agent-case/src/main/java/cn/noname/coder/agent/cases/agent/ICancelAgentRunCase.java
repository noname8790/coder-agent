package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.CancelAgentRunResponseDTO;

/**
 * 请求取消运行，后台执行循环在安全检查点停止。
 */
public interface ICancelAgentRunCase {

    CancelAgentRunResponseDTO cancel(String runId);
}
