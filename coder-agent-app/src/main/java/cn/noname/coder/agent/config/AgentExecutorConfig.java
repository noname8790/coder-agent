package cn.noname.coder.agent.config;

import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Agent 后台执行线程池。首版并发数与 max_concurrent_runs 保持一致。
 */
@Configuration
public class AgentExecutorConfig {

    @Bean("agentRunTaskExecutor")
    public ThreadPoolTaskExecutor agentRunTaskExecutor(AgentRuntimeProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getBudget().getMaxConcurrentRuns());
        executor.setMaxPoolSize(properties.getBudget().getMaxConcurrentRuns());
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("coder-agent-run-");
        executor.initialize();
        return executor;
    }
}
