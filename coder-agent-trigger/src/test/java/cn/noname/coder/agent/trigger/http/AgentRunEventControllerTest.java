package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.domain.agent.adapter.port.IAgentRunEventPublisher;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.entity.AgentRun;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEvent;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.exception.AppException;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AgentRunEventControllerTest {

    @Test
    void shouldCreateSseEmitterGivenExistingRun() {
        // Given 已完成运行和历史事件
        IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
        IAgentRunEventPublisher publisher = mock(IAgentRunEventPublisher.class);
        when(runRepository.findByRunId("run_1")).thenReturn(Optional.of(AgentRun.builder()
                .runId("run_1")
                .status(AgentRunStatus.SUCCEEDED)
                .build()));
        when(publisher.subscribe("run_1")).thenReturn(new LinkedBlockingQueue<>());
        when(publisher.history("run_1")).thenReturn(List.of(new AgentRunEvent(
                "evt_1", "run_1", "run_finished", LocalDateTime.now(), Map.of("status", "SUCCEEDED"))));

        // When 订阅 SSE / Then 返回 emitter
        SseEmitter emitter = new AgentRunEventController(runRepository, publisher).events("run_1");
        assertNotNull(emitter);
    }

    @Test
    void shouldRejectSseGivenUnknownRunId() {
        // Given runId 不存在
        IAgentRunRepository runRepository = mock(IAgentRunRepository.class);
        IAgentRunEventPublisher publisher = mock(IAgentRunEventPublisher.class);
        when(runRepository.findByRunId("missing")).thenReturn(Optional.empty());

        // When 订阅 SSE / Then 抛出业务异常
        AppException ex = assertThrows(AppException.class,
                () -> new AgentRunEventController(runRepository, publisher).events("missing"));
        assertEquals("RUN_NOT_FOUND", ex.getCode());
    }
}
