package com.kob.matchingsystem.service.impl.utils;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MatchingPoolTest {
    @Test
    void largeBotIdsRemainPvpBotsIncludingIdsEndingWith666() {
        RestTemplate rest = mock(RestTemplate.class);
        MatchingPool pool = new MatchingPool(rest);
        pool.addPlayer(1, 1500, 667, "pvp", "match-a");
        pool.addPlayer(2, 1500, 1666, "pvp", "match-b");
        verifyNoInteractions(rest);
        ReflectionTestUtils.invokeMethod(pool, "matchPlayers");
        ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(rest).postForObject(anyString(), data.capture(), eq(String.class));
        assertEquals("667", data.getValue().getFirst("a_bot_id"));
        assertEquals("1666", data.getValue().getFirst("b_bot_id"));
        assertEquals("match-a", data.getValue().getFirst("a_match_id"));
    }

    @Test
    void aiModeUsesActualBotIdAndSupportsManualPlayer() {
        for (int bot : new int[]{-1, 667, 1666}) {
            RestTemplate rest = mock(RestTemplate.class);
            new MatchingPool(rest).addPlayer(1, 1500, bot, "ai", "match-a");
            ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
            verify(rest).postForObject(anyString(), data.capture(), eq(String.class));
            assertEquals(String.valueOf(bot), data.getValue().getFirst("a_bot_id"));
            assertEquals("3", data.getValue().getFirst("b_bot_id"));
            assertEquals("4", data.getValue().getFirst("b_id"));
        }
    }

    @Test
    void delayedCancellationCannotRemoveNewReservation() {
        RestTemplate rest = mock(RestTemplate.class);
        MatchingPool pool = new MatchingPool(rest);
        pool.addPlayer(1, 1500, -1, "pvp", "old");
        pool.addPlayer(1, 1500, -1, "pvp", "new");
        pool.removePlayer(1, "old");
        pool.addPlayer(2, 1500, -1, "pvp", "other");
        ReflectionTestUtils.invokeMethod(pool, "matchPlayers");
        ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(rest).postForObject(anyString(), data.capture(), eq(String.class));
        assertEquals("new", data.getValue().getFirst("a_match_id"));
    }

    @Test
    void cancelledPlayerIsNotMatched() {
        RestTemplate rest = mock(RestTemplate.class);
        MatchingPool pool = new MatchingPool(rest);
        pool.addPlayer(1, 1500, -1, "pvp", "cancelled");
        pool.removePlayer(1, "cancelled");
        pool.addPlayer(2, 1500, -1, "pvp", "other");
        ReflectionTestUtils.invokeMethod(pool, "matchPlayers");
        verifyNoInteractions(rest);
    }

    @Test
    void lateAddFromOldConnectionDoesNotReplaceNewReservation() {
        RestTemplate rest = mock(RestTemplate.class);
        MatchingPool pool = new MatchingPool(rest);
        pool.addPlayer(1, 1500, -1, "pvp", "new");
        pool.addPlayer(1, 1500, -1, "pvp", "old-late-add");
        pool.removePlayer(1, "old-late-add");
        pool.addPlayer(2, 1500, -1, "pvp", "other");
        ReflectionTestUtils.invokeMethod(pool, "matchPlayers");
        ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(rest).postForObject(anyString(), data.capture(), eq(String.class));
        assertEquals("new", data.getValue().getFirst("a_match_id"));
    }
}
