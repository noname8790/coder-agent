package cn.noname.coder.agent.cases.agent.impl;

import cn.noname.coder.agent.api.dto.CancelAgentRunResponseDTO;
import cn.noname.coder.agent.cases.agent.ICancelAgentRunCase;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.service.AgentRunDomainService;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 取消运行用例，已终态运行保持原状态。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CancelAgentRunCaseImpl implements ICancelAgentRunCase {

    private final IAgentRunRepository runRepository;
    private final AgentRunDomainService domainService = new AgentRunDomainService();

    @Override
    public CancelAgentRunResponseDTO cancel(String runId) {
        log.info("收到取消 Agent 运行请求 runId={}", runId);
        AgentRun run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        if (!run.getStatus().isTerminal()) {
            domainService.cancel(run);
            runRepository.update(run);
            log.info("Agent 运行已标记取消 runId={}", runId);
        } else {
            log.info("Agent 运行已是终态，忽略取消 runId={} status={}", runId, run.getStatus());
        }
        return new CancelAgentRunResponseDTO(run.getRunId(), run.getStatus().name());
    }
}
