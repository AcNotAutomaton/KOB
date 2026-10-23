package com.kob.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;
import java.util.Arrays;

@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    public static final long JWT_TTL = 60 * 60 * 1000L * 24 * 14;  // 有效期14天

    private static final String RETIRED_DEV_KEY =
            "kob-dev-only-jwt-secret-please-set-KOB_JWT_KEY-env-var-0123456789";
    private static final SecretKey DEV_KEY = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private static volatile SecretKey signingKey = DEV_KEY;

    public JwtUtil(Environment environment) {
        signingKey = configuredKey(environment);
    }

    static SecretKey configuredKey(Environment environment) {
        String value = environment.getProperty("kob.jwt.key", environment.getProperty("KOB_JWT_KEY"));
        if (value == null || value.isBlank()) {
            String[] profiles = environment.getActiveProfiles();
            boolean development = profiles.length > 0
                    && Arrays.stream(profiles).allMatch(p -> "dev".equals(p) || "test".equals(p));
            if (!development) throw new IllegalStateException("必须配置 KOB_JWT_KEY，生产环境不允许自动生成 JWT 密钥");
            log.warn("未配置 KOB_JWT_KEY，开发环境使用本次进程的随机密钥；重启后需重新登录");
            return DEV_KEY;
        }
        if (RETIRED_DEV_KEY.equals(value) || value.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("KOB_JWT_KEY 必须是独立的至少32字节密钥，不能使用旧开发默认值");
        }
        return Keys.hmacShaKeyFor(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String getUUID() {
        return UUID.randomUUID().toString().replaceAll("-", "");
    }

    public static String createJWT(String subject) {
        JwtBuilder builder = getJwtBuilder(subject, getUUID());
        return builder.compact();
    }

    private static JwtBuilder getJwtBuilder(String subject, String uuid) {
        SecretKey secretKey = generalKey();
        long nowMillis = System.currentTimeMillis();
        Date now = new Date(nowMillis);
        long expMillis = nowMillis + JWT_TTL;
        Date expDate = new Date(expMillis);
        return Jwts.builder()
                .setId(uuid)
                .setSubject(subject)
                .setIssuer("sg")
                .setIssuedAt(now)
                .signWith(secretKey)
                .setExpiration(expDate);
    }

    public static SecretKey generalKey() {
        return signingKey;
    }

    public static Claims parseJWT(String jwt) {
        SecretKey secretKey = generalKey();
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(jwt)
                .getBody();
    }
}
