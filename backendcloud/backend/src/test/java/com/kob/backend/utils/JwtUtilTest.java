package com.kob.backend.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.mock.env.MockEnvironment;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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

    @ParameterizedTest
    @ValueSource(strings = {"prod", "production", "staging"})
    void deployedProfilesRequireExplicitSecret(String profile) {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles(profile);
        assertThrows(IllegalStateException.class, () -> new JwtUtil(env));
    }

    @Test
    void mixedProductionAndDevelopmentProfilesCannotUseRandomFallback() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "prod");
        assertThrows(IllegalStateException.class, () -> new JwtUtil(env));
    }

    @Test
    void rejectsRetiredPublicKeyAndShortSecrets() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kob.jwt.key", "short");
        assertThrows(IllegalStateException.class, () -> new JwtUtil(env));
        env.setProperty("kob.jwt.key", "kob-dev-only-jwt-secret-please-set-KOB_JWT_KEY-env-var-0123456789");
        assertThrows(IllegalStateException.class, () -> new JwtUtil(env));
    }

    @Test
    void developmentKeyIsNotTheRetiredPublicKey() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        new JwtUtil(env);
        String oldKey = "kob-dev-only-jwt-secret-please-set-KOB_JWT_KEY-env-var-0123456789";
        String forged = Jwts.builder().setSubject("1").setExpiration(new Date(System.currentTimeMillis() + 60000))
                .signWith(Keys.hmacShaKeyFor(oldKey.getBytes(StandardCharsets.UTF_8))).compact();
        assertThrows(JwtException.class, () -> JwtUtil.parseJWT(forged));
    }

    @Test
    void configuredKeyCanSignAndVerifyTokens() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        env.setProperty("kob.jwt.key", "unit-test-only-independent-secret-key-0123456789");
        try {
            new JwtUtil(env);
            assertEquals("1", JwtUtil.parseJWT(JwtUtil.createJWT("1")).getSubject());
        } finally {
            env.setActiveProfiles("test");
            env.setProperty("kob.jwt.key", "");
            new JwtUtil(env);
        }
    }

}

