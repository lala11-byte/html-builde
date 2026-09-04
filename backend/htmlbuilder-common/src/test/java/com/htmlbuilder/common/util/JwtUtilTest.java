package com.htmlbuilder.common.util;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class JwtUtilTest {

    private final String secret = "test-secret-key-for-jwt-hs256-algorithm-that-is-long-enough";

    @Test
    void generateAccessToken_shouldReturnToken() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void parseToken_shouldExtractUserId() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        Claims claims = JwtUtil.parseToken(token, secret);
        assertEquals(1L, Long.valueOf(claims.get("userId").toString()));
    }

    @Test
    void parseToken_shouldExtractUsername() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        Claims claims = JwtUtil.parseToken(token, secret);
        assertEquals("testuser", claims.getSubject());
    }

    @Test
    void isTokenValid_withValidToken_shouldReturnTrue() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        assertTrue(JwtUtil.isTokenValid(token, secret));
    }

    @Test
    void isTokenValid_withWrongSecret_shouldReturnFalse() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        assertFalse(JwtUtil.isTokenValid(token, "wrong-secret-key-that-is-also-long-enough-wow"));
    }

    @Test
    void generateRefreshToken_shouldReturnToken() {
        String token = JwtUtil.generateRefreshToken(1L, "testuser", secret);
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void getUserIdFromToken_shouldExtractCorrectly() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        assertEquals(1L, JwtUtil.getUserIdFromToken(token, secret));
    }

    @Test
    void getUsernameFromToken_shouldExtractCorrectly() {
        String token = JwtUtil.generateAccessToken(1L, "testuser", secret);
        assertEquals("testuser", JwtUtil.getUsernameFromToken(token, secret));
    }
}