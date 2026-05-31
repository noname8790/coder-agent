package cn.noname.coder.agent.types.exception;

/**
 * 业务异常用于向 REST 层传递可控错误，不直接暴露底层异常细节。
 */
public class AppException extends RuntimeException {

    private final String code;

    public AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
