package com.htmlbuilder.common.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

public class JwtUtil {

    private static final long ACCESS_EXPIRATION = 2 * 60 * 60 * 1000L; // 2h
    private static final long REFRESH_EXPIRATION = 7 * 24 * 60 * 60 * 1000L; // 7d

    private static SecretKey getKey(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public static String generateAccessToken(Long userId, String username, String secret) {
        return buildToken(userId, username, secret, ACCESS_EXPIRATION);
    }

    public static String generateRefreshToken(Long userId, String username, String secret) {
        return buildToken(userId, username, secret, REFRESH_EXPIRATION);
    }

    private static String buildToken(Long userId, String username, String secret, long expiration) {
        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(getKey(secret))
                .compact();
    }

    public static Claims parseToken(String token, String secret) {
        return Jwts.parser()
                .verifyWith(getKey(secret))
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public static boolean isTokenValid(String token, String secret) {
        try {
            parseToken(token, secret);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Long getUserIdFromToken(String token, String secret) {
        return Long.valueOf(parseToken(token, secret).get("userId").toString());
    }

    public static String getUsernameFromToken(String token, String secret) {
        return parseToken(token, secret).getSubject();
    }
}