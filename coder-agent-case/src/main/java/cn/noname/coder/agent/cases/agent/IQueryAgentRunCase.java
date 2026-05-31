package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.AgentRunResponseDTO;

/**
 * 查询 Agent 运行状态和最终结果摘要。
 */
public interface IQueryAgentRunCase {

    AgentRunResponseDTO query(String runId);
}
