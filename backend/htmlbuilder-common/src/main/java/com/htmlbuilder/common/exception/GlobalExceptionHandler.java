package com.htmlbuilder.common.exception;

import com.htmlbuilder.common.result.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage())
                .findFirst()
                .orElse("参数校验失败");
        return Result.fail(400, message);
    }

    /**
     * SSE 端点超时：不返回任何内容，避免 Result 对象无法序列化为 text/event-stream
     */
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    public void handleAsyncRequestTimeoutException(AsyncRequestTimeoutException e) {
        // SSE 连接超时，response 已提交，无需额外处理
    }

    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        // 路径不存在（404）：MVC（org.springframework.web.servlet.resource）与
        // WebFlux（org.springframework.web.reactive.resource）的 NoResourceFoundException
        // 同名不同包，common 同时被两类服务依赖，无法统一类型引用，故按类名匹配
        if ("NoResourceFoundException".equals(e.getClass().getSimpleName())) {
            return Result.fail(404, "请求路径不存在");
        }
        log.error("未捕获异常", e);
        return Result.fail(500, "服务器内部异常");
    }
}