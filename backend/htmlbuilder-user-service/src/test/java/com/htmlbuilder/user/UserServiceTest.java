package com.htmlbuilder.user;

import com.htmlbuilder.user.entity.User;
import com.htmlbuilder.user.service.UserService;
import com.htmlbuilder.user.dto.RegisterDTO;
import com.htmlbuilder.user.dto.LoginDTO;
import com.htmlbuilder.user.vo.UserVO;
import com.htmlbuilder.user.vo.TokenVO;
import com.htmlbuilder.common.util.JwtUtil;
import com.htmlbuilder.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.cloud.nacos.discovery.enabled=false",
    "spring.autoconfigure.exclude=com.baomidou.mybatisplus.autoconfigure.MybatisPlusAutoConfiguration,org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
})
@ActiveProfiles("test")
public class UserServiceTest {

    private static final String TEST_SECRET = "htmlbuilder-default-secret-key-for-jwt-hs256";

    @Autowired
    private UserService userService;

    @MockBean
    private com.htmlbuilder.user.mapper.UserMapper userMapper;

    @Test
    void register_shouldReturnUserVO() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("newuser");
        dto.setPassword("Abc12345");

        when(userMapper.selectByUsername("newuser")).thenReturn(null);
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(1L);
            return 1;
        });

        UserVO result = userService.register(dto);
        assertNotNull(result);
        assertEquals("newuser", result.getUsername());
        assertEquals(1L, result.getId());
    }

    @Test
    void register_duplicateUsername_shouldThrowBusinessException() {
        RegisterDTO dto = new RegisterDTO();
        dto.setUsername("existing");
        dto.setPassword("Abc12345");

        when(userMapper.selectByUsername("existing")).thenReturn(new User());

        assertThrows(BusinessException.class, () -> userService.register(dto));
    }

    @Test
    void login_shouldReturnTokenVO() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("Abc12345");

        String expectedHash = DigestUtils.md5DigestAsHex("Abc12345".getBytes(StandardCharsets.UTF_8));

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash(expectedHash);

        when(userMapper.selectByUsername("testuser")).thenReturn(user);

        TokenVO result = userService.login(dto);
        assertNotNull(result);
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        assertTrue(JwtUtil.isTokenValid(result.getAccessToken(), TEST_SECRET));
        assertTrue(JwtUtil.isTokenValid(result.getRefreshToken(), TEST_SECRET));
    }

    @Test
    void login_wrongPassword_shouldThrowBusinessException() {
        LoginDTO dto = new LoginDTO();
        dto.setUsername("testuser");
        dto.setPassword("WrongPass1");

        User user = new User();
        user.setId(1L);
        user.setUsername("testuser");
        user.setPasswordHash("a1b2c3d4e5f6_not_matching");

        when(userMapper.selectByUsername("testuser")).thenReturn(user);

        assertThrows(BusinessException.class, () -> userService.login(dto));
    }

    @Test
    void refreshToken_shouldReturnNewTokenVO() {
        String refreshToken = JwtUtil.generateRefreshToken(1L, "testuser", TEST_SECRET);
        TokenVO result = userService.refreshToken(refreshToken);
        assertNotNull(result);
        assertNotNull(result.getAccessToken());
        assertNotNull(result.getRefreshToken());
        assertTrue(JwtUtil.isTokenValid(result.getAccessToken(), TEST_SECRET));
    }
}