package com.kob.backend.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Claims;

public class JwtUtilTest {

    @Test
    public void createAndParseJwt_shouldReturnSameSubject() {
        String subject = "user:123";
        String token = JwtUtil.createJWT(subject);
        assertNotNull(token, "Token should not be null");

        Claims claims = JwtUtil.parseJWT(token);
        assertNotNull(claims, "Claims should not be null");
        assertEquals(subject, claims.getSubject(), "Subject should match");
    }

}

