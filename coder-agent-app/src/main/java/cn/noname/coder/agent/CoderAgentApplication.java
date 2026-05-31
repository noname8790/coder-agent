package cn.noname.coder.agent;

import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * coder-agent 服务端启动入口。
 */
@SpringBootApplication(scanBasePackages = "cn.noname.coder.agent")
@MapperScan("cn.noname.coder.agent.infrastructure.dao")
@EnableConfigurationProperties(AgentRuntimeProperties.class)
public class CoderAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(CoderAgentApplication.class, args);
    }
}
