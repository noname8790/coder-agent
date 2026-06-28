package cn.noname.coder.agent.cases.context;

import cn.noname.coder.agent.domain.context.adapter.port.ITokenEstimator;
import org.springframework.stereotype.Component;

/**
 * 轻量 token 估算器。后续可通过 ITokenEstimator 端口替换为 LangChain4j tokenizer。
 */
@Component
public class SimpleTokenEstimator implements ITokenEstimator {

    @Override
    public int estimate(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        int ascii = 0;
        int nonAscii = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) <= 127) {
                ascii++;
            } else {
                nonAscii++;
            }
        }
        return Math.max(1, (int) Math.ceil(ascii / 4.0 + nonAscii));
    }
}
