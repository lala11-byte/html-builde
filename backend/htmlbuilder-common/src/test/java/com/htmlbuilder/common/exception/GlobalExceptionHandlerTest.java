package com.htmlbuilder.common.exception;

import com.htmlbuilder.common.result.Result;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测试全局异常处理器：BusinessException 被 @RestControllerAdvice 捕获并转换为 Result。
 * 此处仅测试类存在与基本结构，实际 HTTP 级别的集成测试留在各服务 MockMvc 测试中。
 */
public class GlobalExceptionHandlerTest {

    @Test
    void globalExceptionHandler_class_shouldExist() {
        assertDoesNotThrow(() -> {
            Class.forName("com.htmlbuilder.common.exception.GlobalExceptionHandler");
        });
    }

    @Test
    void servletNoResourceFound_shouldReturn404Not500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        org.springframework.web.servlet.resource.NoResourceFoundException e =
                new org.springframework.web.servlet.resource.NoResourceFoundException(HttpMethod.GET, "/foobarbaz");
        Result<?> result = handler.handleException(e);
        assertEquals(404, result.getCode());
        assertEquals("请求路径不存在", result.getMessage());
    }

    @Test
    void reactiveNoResourceFound_shouldReturn404Not500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        org.springframework.web.reactive.resource.NoResourceFoundException e =
                new org.springframework.web.reactive.resource.NoResourceFoundException("/");
        Result<?> result = handler.handleException(e);
        assertEquals(404, result.getCode());
        assertEquals("请求路径不存在", result.getMessage());
    }

    @Test
    void unknownException_shouldReturn500() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        Result<?> result = handler.handleException(new IllegalStateException("boom"));
        assertEquals(500, result.getCode());
        assertEquals("服务器内部异常", result.getMessage());
    }
}