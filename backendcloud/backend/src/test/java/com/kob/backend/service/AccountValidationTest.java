package com.kob.backend.service;

import com.kob.backend.mapper.UserMapper;
import com.kob.backend.service.impl.user.account.RegisterServiceImpl;
import com.kob.backend.service.impl.user.account.LoginServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountValidationTest {
    private RegisterServiceImpl register(UserMapper mapper) {
        RegisterServiceImpl service = new RegisterServiceImpl();
        ReflectionTestUtils.setField(service, "userMapper", mapper);
        ReflectionTestUtils.setField(service, "passwordEncoder", new BCryptPasswordEncoder(4));
        return service;
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "chinese", "emoji"})
    void rejectsPasswordsExceeding72BytesBeforeQueryOrEncoding(String kind) {
        String password = switch (kind) {
            case "ascii" -> "a".repeat(73);
            case "chinese" -> "密".repeat(25);
            default -> "😀".repeat(19);
        };
        UserMapper mapper = mock(UserMapper.class);
        assertTrue(register(mapper).register("user", password, password).get("error_message").contains("72字节"));
        verifyNoInteractions(mapper);
        LoginServiceImpl login = new LoginServiceImpl();
        AuthenticationManager authentication = mock(AuthenticationManager.class);
        ReflectionTestUtils.setField(login, "authenticationManager", authentication);
        assertThrows(BadCredentialsException.class, () -> login.getToken("user", password));
        verifyNoInteractions(authentication);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ascii", "chinese", "emoji"})
    void acceptsExactly72Bytes(String kind) {
        String password = switch (kind) {
            case "ascii" -> "a".repeat(72);
            case "chinese" -> "密".repeat(24);
            default -> "😀".repeat(18);
        };
        assertEquals("success", register(mock(UserMapper.class)).register("user", password, password).get("error_message"));
    }

    @Test
    void duplicateKeyFromConcurrentRegistrationReturnsReadableError() {
        UserMapper mapper = mock(UserMapper.class);
        when(mapper.insert(any(com.kob.backend.pojo.User.class))).thenThrow(new DuplicateKeyException("duplicate"));
        assertEquals("用户名已存在", register(mapper).register("same", "password", "password").get("error_message"));
    }
}
