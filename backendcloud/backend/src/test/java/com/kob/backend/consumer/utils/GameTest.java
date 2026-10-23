package com.kob.backend.consumer.utils;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.mapper.*;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.Record;
import com.kob.backend.service.impl.pk.ReceiveBotMoveServiceImpl;
import com.kob.backend.service.impl.pk.GameResultService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GameTest {
    @BeforeEach
    void setup() {
        WebSocketServer.users.clear();
        WebSocketServer.games.clear();
        WebSocketServer.activeGames.clear();
        WebSocketServer.userMapper = mock(UserMapper.class);
        WebSocketServer.botMapper = mock(BotMapper.class);
        WebSocketServer.recordMapper = mock(RecordMapper.class);
        WebSocketServer.gameBotMapper = mock(GameBotMapper.class);
        WebSocketServer.gameResultService = new GameResultService(WebSocketServer.userMapper, WebSocketServer.botMapper,
                WebSocketServer.recordMapper, WebSocketServer.gameBotMapper);
        WebSocketServer.restTemplate = mock(org.springframework.web.client.RestTemplate.class);
    }

    @AfterEach
    void cleanup() {
        WebSocketServer.users.clear();
        WebSocketServer.games.clear();
        WebSocketServer.activeGames.clear();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {-1, 4, Integer.MAX_VALUE, Integer.MIN_VALUE})
    void illegalDirectionLosesAndPersistsWithoutCrashing(Integer direction) {
        Game game = new Game(13, 14, 20, 1, null, 2, null);
        WebSocketServer ws = mock(WebSocketServer.class);
        ws.game = game;
        WebSocketServer.users.put(1, ws);
        WebSocketServer.games.put(game.getGameId(), game);
        assertTrue(game.receivePlayerMove(1, game.getGameId(), 0, direction));
        assertTrue(game.receivePlayerMove(2, game.getGameId(), 0, 0));
        assertTimeoutPreemptively(Duration.ofSeconds(2), game::run);
        ArgumentCaptor<Record> record = ArgumentCaptor.forClass(Record.class);
        verify(WebSocketServer.recordMapper).insert(record.capture());
        assertEquals("A", record.getValue().getLoser());
        assertEquals("", record.getValue().getASteps());
        verify(ws).sendMessage(contains("\"loser\":\"A\""));
        assertFalse(game.isPlaying());
        assertNull(ws.game);
        assertFalse(WebSocketServer.games.containsKey(game.getGameId()));
    }

    @Test
    void rejectsStaleRoundsOpponentsGamesAndDuplicateBotMoves() {
        Bot bot = new Bot(77, 1, "test", "", "code", 1500, 0, new Date(), new Date());
        Game game = new Game(13, 14, 20, 1, bot, 2, null);
        WebSocketServer ws = mock(WebSocketServer.class);
        ws.game = game;
        WebSocketServer.users.put(1, ws);
        WebSocketServer.games.put(game.getGameId(), game);
        ReceiveBotMoveServiceImpl service = new ReceiveBotMoveServiceImpl();
        assertEquals("stale or invalid bot move", service.receiveBotMove(1, 0, 2, "old-game", 0));
        assertFalse(game.receiveBotMove(1, 3, game.getGameId(), 0, 0));
        assertFalse(game.receiveBotMove(1, 2, game.getGameId(), 1, 0));
        assertFalse(game.receivePlayerMove(1, game.getGameId(), 0, 0));
        assertTrue(game.receiveBotMove(1, 2, game.getGameId(), 0, 0));
        assertFalse(game.receiveBotMove(1, 2, game.getGameId(), 0, 1));
        assertTrue(game.receivePlayerMove(2, game.getGameId(), 0, 2));
        AtomicBoolean nextRound = new AtomicBoolean();
        doAnswer(invocation -> {
            JSONObject event = JSONObject.parseObject(invocation.getArgument(0));
            if ("move".equals(event.getString("event"))) {
                assertEquals(1, event.getInteger("round"));
                assertFalse(game.receiveBotMove(1, 2, game.getGameId(), 0, 1));
                assertTrue(game.receiveBotMove(1, 2, game.getGameId(), 1, 4));
                assertTrue(game.receivePlayerMove(2, game.getGameId(), 1, 2));
                nextRound.set(true);
            }
            return null;
        }).when(ws).sendMessage(anyString());
        assertTimeoutPreemptively(Duration.ofSeconds(2), game::run);
        assertTrue(nextRound.get());
        assertEquals("0", game.getPlayerA().getStepsString());
        assertEquals("2", game.getPlayerB().getStepsString());
        assertFalse(game.receiveBotMove(1, 2, game.getGameId(), 1, 0));
    }

    @Test
    void callbackUsesGameRegistryWhenBothPlayersHaveNoConnection() {
        Bot bot = new Bot(77, 1, "test", "", "code", 1500, 0, new Date(), new Date());
        Game game = new Game(13, 14, 20, 1, bot, 2, null);
        WebSocketServer.games.put(game.getGameId(), game);
        assertEquals("receive bot move success",
                new ReceiveBotMoveServiceImpl().receiveBotMove(1, 0, 2, game.getGameId(), 0));
        assertFalse(game.receiveBotMove(1, 2, game.getGameId(), 0, 1));
    }
}
