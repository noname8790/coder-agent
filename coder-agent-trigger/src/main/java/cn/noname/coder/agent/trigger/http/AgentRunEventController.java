package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.domain.agent.adapter.port.IAgentRunEventPublisher;
import cn.noname.coder.agent.domain.agent.adapter.repository.IAgentRunRepository;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEvent;
import cn.noname.coder.agent.types.enums.AgentRunStatus;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.BlockingQueue;

/**
 * Agent Run SSE 事件流。
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/agent-runs")
public class AgentRunEventController {

    private final IAgentRunRepository runRepository;
    private final IAgentRunEventPublisher eventPublisher;

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable("runId") String runId) {
        var run = runRepository.findByRunId(runId)
                .orElseThrow(() -> new AppException("RUN_NOT_FOUND", "运行不存在：" + runId));
        SseEmitter emitter = new SseEmitter(0L);
        BlockingQueue<AgentRunEvent> queue = eventPublisher.subscribe(runId);
        log.info("SSE 订阅运行事件 runId={}", runId);
        Thread.ofVirtual().name("agent-run-sse-" + runId).start(() -> {
            try {
                for (AgentRunEvent event : eventPublisher.history(runId)) {
                    send(emitter, event);
                }
                if (run.getStatus().isTerminal()) {
                    emitter.complete();
                    return;
                }
                while (true) {
                    AgentRunEvent event = queue.take();
                    send(emitter, event);
                    if ("run_finished".equals(event.type())) {
                        emitter.complete();
                        return;
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                emitter.complete();
            } catch (Exception e) {
                log.warn("SSE 事件流结束 runId={} reason={}", runId, e.getMessage());
                emitter.completeWithError(e);
            } finally {
                eventPublisher.unsubscribe(runId, queue);
            }
        });
        return emitter;
    }

    private void send(SseEmitter emitter, AgentRunEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(event.eventId())
                .name(event.type())
                .data(event));
    }
}
