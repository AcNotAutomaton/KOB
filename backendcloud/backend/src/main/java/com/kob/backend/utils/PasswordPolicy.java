package com.kob.backend.utils;

import java.nio.charset.StandardCharsets;

public final class PasswordPolicy {
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() { }

    public static boolean tooLong(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES;
    }
}
