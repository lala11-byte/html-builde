package com.htmlbuilder.common.exception;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class BusinessExceptionTest {

    @Test
    void businessException_shouldHaveCodeAndMessage() {
        BusinessException ex = new BusinessException(1001, "用户名已存在");
        assertEquals(1001, ex.getCode());
        assertEquals("用户名已存在", ex.getMessage());
    }

    @Test
    void businessException_shouldExtendRuntimeException() {
        BusinessException ex = new BusinessException(1001, "test");
        assertTrue(ex instanceof RuntimeException);
    }
}