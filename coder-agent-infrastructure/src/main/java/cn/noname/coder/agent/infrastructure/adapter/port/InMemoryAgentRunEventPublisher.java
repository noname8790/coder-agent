package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IAgentRunEventPublisher;
import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * 单机内存事件发布器。历史事件用于 SSE 重连补偿，最终审计仍以 trace.jsonl 和数据库为准。
 */
@Slf4j
@Component
public class InMemoryAgentRunEventPublisher implements IAgentRunEventPublisher {

    private static final int MAX_HISTORY = 500;

    private final Map<String, List<AgentRunEvent>> history = new ConcurrentHashMap<>();
    private final Map<String, List<BlockingQueue<AgentRunEvent>>> subscribers = new ConcurrentHashMap<>();

    @Override
    public void publish(AgentRunEvent event) {
        history.computeIfAbsent(event.runId(), key -> new CopyOnWriteArrayList<>()).add(event);
        List<AgentRunEvent> events = history.get(event.runId());
        if (events.size() > MAX_HISTORY) {
            events.remove(0);
        }
        for (BlockingQueue<AgentRunEvent> queue : subscribers.getOrDefault(event.runId(), List.of())) {
            queue.offer(event);
        }
        log.debug("发布运行事件 runId={} type={} eventId={}", event.runId(), event.type(), event.eventId());
    }

    @Override
    public List<AgentRunEvent> history(String runId) {
        return new ArrayList<>(history.getOrDefault(runId, List.of()));
    }

    @Override
    public BlockingQueue<AgentRunEvent> subscribe(String runId) {
        BlockingQueue<AgentRunEvent> queue = new LinkedBlockingQueue<>();
        subscribers.computeIfAbsent(runId, key -> new CopyOnWriteArrayList<>()).add(queue);
        return queue;
    }

    @Override
    public void unsubscribe(String runId, BlockingQueue<AgentRunEvent> queue) {
        List<BlockingQueue<AgentRunEvent>> queues = subscribers.get(runId);
        if (queues != null) {
            queues.remove(queue);
        }
    }
}
