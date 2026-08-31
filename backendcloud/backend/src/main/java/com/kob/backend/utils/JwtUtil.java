package com.kob.backend.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Component
public class JwtUtil {
    private static final Logger log = LoggerFactory.getLogger(JwtUtil.class);

    public static final long JWT_TTL = 60 * 60 * 1000L * 24 * 14;  // 有效期14天

    /**
     * 签名密钥：优先读环境变量 KOB_JWT_KEY（生产环境必须配置），
     * 未配置时退回开发默认值并打印警告。
     * 密钥一旦变更，所有已签发的 token 立即失效，用户需要重新登录。
     */
    private static final String DEFAULT_DEV_KEY =
            "kob-dev-only-jwt-secret-please-set-KOB_JWT_KEY-env-var-0123456789";
    public static final String JWT_KEY = System.getenv().getOrDefault("KOB_JWT_KEY", DEFAULT_DEV_KEY);

    static {
        if (DEFAULT_DEV_KEY.equals(JWT_KEY)) {
            log.warn("未设置环境变量 KOB_JWT_KEY，正在使用开发默认密钥；生产环境必须通过环境变量注入独立密钥");
        }
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
        // 统一按 UTF-8 原始字节解释密钥，长度需 >= 256 bit（HS256 的要求）
        return Keys.hmacShaKeyFor(JWT_KEY.getBytes(StandardCharsets.UTF_8));
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
