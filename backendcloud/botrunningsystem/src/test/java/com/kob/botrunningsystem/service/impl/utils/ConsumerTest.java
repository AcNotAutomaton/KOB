package com.kob.botrunningsystem.service.impl.utils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConsumerTest {
    @Test
    void callbackRetainsGameAndRoundIdentity() {
        RestTemplate rest = mock(RestTemplate.class);
        ContainerSandbox sandbox = mock(ContainerSandbox.class);
        when(sandbox.execute("code", "input")).thenReturn(2);
        new Consumer(rest, sandbox).consume(new Bot(1, "code", "input", 2, "game-a", 8));
        ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(rest).postForObject(anyString(), data.capture(), eq(String.class));
        assertEquals("game-a", data.getValue().getFirst("game_id"));
        assertEquals("8", data.getValue().getFirst("round"));
        assertEquals("2", data.getValue().getFirst("direction"));
    }

    @Test
    void failedSandboxDoesNotInventAMove() {
        RestTemplate rest = mock(RestTemplate.class);
        ContainerSandbox sandbox = mock(ContainerSandbox.class);
        when(sandbox.execute("code", "input")).thenReturn(null);
        new Consumer(rest, sandbox).consume(new Bot(1, "code", "input", 2, "game-a", 8));
        verifyNoInteractions(rest);
    }
}
