package com.htmlbuilder.common.result;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class ResultTest {

    @Test
    void success_shouldReturnCode200() {
        Result<String> result = Result.success("hello");
        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
    }

    @Test
    void success_withNullData_shouldReturnCode200() {
        Result<?> result = Result.success(null);
        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void fail_shouldReturnCustomCodeAndMessage() {
        Result<?> result = Result.fail(1001, "用户名已存在");
        assertEquals(1001, result.getCode());
        assertEquals("用户名已存在", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void fail_shouldReturnNullData() {
        Result<String> result = Result.fail(400, "参数校验失败");
        assertEquals(400, result.getCode());
        assertNull(result.getData());
    }
}