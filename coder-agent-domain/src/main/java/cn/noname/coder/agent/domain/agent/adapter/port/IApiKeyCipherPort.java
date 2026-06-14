package cn.noname.coder.agent.domain.agent.adapter.port;

public interface IApiKeyCipherPort {

    String encrypt(String plainText);

    String decrypt(String cipherText);

    String mask(String plainText);
}
