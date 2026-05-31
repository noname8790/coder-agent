package cn.noname.coder.agent.trigger.http;

import cn.noname.coder.agent.types.common.Response;
import cn.noname.coder.agent.types.exception.AppException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * REST 异常处理，业务异常返回明确错误码，系统异常返回通用错误。
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public Response<Void> handleAppException(AppException e) {
        return Response.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public Response<Void> handleException(Exception e) {
        log.error("接口处理异常", e);
        return Response.fail("SYSTEM_ERROR", e.getMessage());
    }
}
