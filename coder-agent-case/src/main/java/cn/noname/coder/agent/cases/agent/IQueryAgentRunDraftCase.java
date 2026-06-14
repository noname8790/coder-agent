package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.AgentRunDraftResponseDTO;

public interface IQueryAgentRunDraftCase {

    AgentRunDraftResponseDTO queryDraft(String runId);
}
