package cn.noname.coder.agent;

import cn.noname.coder.agent.types.config.AgentRuntimeProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class AgentRuntimePropertiesBindingTest {

    @Autowired
    private AgentRuntimeProperties properties;

    @Test
    void shouldBindConfiguredModelKeysGivenApplicationYaml() {
        assertTrue(properties.getModel().getModels().containsKey("qwen3.6-plus"));
        assertTrue(properties.getModel().getModels().containsKey("glm-5"));
        assertTrue(properties.getModel().getModels().containsKey("deepseek-v4-flash"));
    }
}
