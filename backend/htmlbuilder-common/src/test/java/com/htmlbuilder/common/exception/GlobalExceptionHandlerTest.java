package com.htmlbuilder.common.exception;

import com.htmlbuilder.common.result.Result;
import org.junit.jupiter.api.Test;
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
}