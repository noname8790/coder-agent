package cn.noname.coder.agent.types.common;

/**
 * REST 接口统一响应结构。
 */
public record Response<T>(String code, String message, T data) {

    public static <T> Response<T> ok(T data) {
        return new Response<>("0000", "success", data);
    }

    public static <T> Response<T> fail(String code, String message) {
        return new Response<>(code, message, null);
    }
}
