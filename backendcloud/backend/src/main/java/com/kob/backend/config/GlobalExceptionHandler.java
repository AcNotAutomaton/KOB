package com.kob.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 登录凭据错误等认证异常：返回 401 与可读信息，而不是 500 */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException e) {
        Map<String, String> map = new HashMap<>();
        map.put("error_message", "用户名或密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(map);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleException(Exception e) {
        log.error("未处理异常", e);
        Map<String, String> map = new HashMap<>();
        map.put("error_message", "服务器内部错误");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(map);
    }
}

