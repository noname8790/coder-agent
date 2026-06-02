package cn.noname.coder.agent.domain.agent.adapter.port;

import cn.noname.coder.agent.domain.agent.model.valobj.AgentRunEvent;

import java.util.List;
import java.util.concurrent.BlockingQueue;

/**
 * 运行事件发布和订阅端口。
 */
public interface IAgentRunEventPublisher {

    void publish(AgentRunEvent event);

    List<AgentRunEvent> history(String runId);

    BlockingQueue<AgentRunEvent> subscribe(String runId);

    void unsubscribe(String runId, BlockingQueue<AgentRunEvent> queue);
}
