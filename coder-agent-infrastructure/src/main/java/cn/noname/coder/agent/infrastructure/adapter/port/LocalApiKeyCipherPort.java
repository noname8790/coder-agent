package cn.noname.coder.agent.infrastructure.adapter.port;

import cn.noname.coder.agent.domain.agent.adapter.port.IApiKeyCipherPort;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class LocalApiKeyCipherPort implements IApiKeyCipherPort {

    private static final String PREFIX = "local:v1:";

    @Override
    public String encrypt(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        return PREFIX + Base64.getEncoder().encodeToString(plainText.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String decrypt(String cipherText) {
        if (!StringUtils.hasText(cipherText)) {
            return "";
        }
        if (!cipherText.startsWith(PREFIX)) {
            return cipherText;
        }
        return new String(Base64.getDecoder().decode(cipherText.substring(PREFIX.length())), StandardCharsets.UTF_8);
    }

    @Override
    public String mask(String plainText) {
        if (!StringUtils.hasText(plainText)) {
            return "";
        }
        String value = decrypt(plainText);
        int visible = Math.min(4, value.length());
        return "****" + value.substring(value.length() - visible);
    }
}
