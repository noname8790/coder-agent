package cn.noname.coder.agent.cases.agent;

import cn.noname.coder.agent.api.dto.RunChangeActionResponseDTO;

public interface IRunChangeCase {

    RunChangeActionResponseDTO revert(String runId);

    RunChangeActionResponseDTO restore(String runId);
}
