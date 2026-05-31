package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.CreateAgentRunRequestDTO;
import cn.noname.coder.agent.api.dto.CreateAgentRunResponseDTO;

/**
 * 创建并异步启动 Agent 运行。
 */
public interface ICreateAgentRunCase {

    CreateAgentRunResponseDTO create(CreateAgentRunRequestDTO request);
}
