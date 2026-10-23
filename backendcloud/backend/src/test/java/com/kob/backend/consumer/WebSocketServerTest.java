package com.kob.backend.consumer;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.mapper.*;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.User;
import com.kob.backend.utils.JwtUtil;
import com.kob.backend.consumer.utils.Game;
import com.kob.backend.service.impl.pk.GameResultService;
import org.springframework.test.util.ReflectionTestUtils;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import jakarta.websocket.RemoteEndpoint;
import jakarta.websocket.Session;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class WebSocketServerTest {
    private static final String ADD = "http://127.0.0.1:3001/player/add/";
    private static final String REMOVE = "http://127.0.0.1:3001/player/remove/";
    private RestTemplate rest;
    private record Client(WebSocketServer ws, Session session, List<JSONObject> messages) { }

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
        rest = mock(RestTemplate.class);
        WebSocketServer.restTemplate = rest;
        for (int id : new int[]{1, 2, 4}) {
            when(WebSocketServer.userMapper.selectById(id)).thenReturn(new User(id, "user" + id, "", "", 1500, 0));
        }
        when(rest.getForObject(anyString(), eq(Boolean.class))).thenReturn(true);
    }

    @AfterEach
    void cleanup() {
        WebSocketServer.users.clear();
        WebSocketServer.games.clear();
        WebSocketServer.activeGames.clear();
    }

    private Client connect(int id) throws Exception {
        Session session = mock(Session.class);
        RemoteEndpoint.Basic remote = mock(RemoteEndpoint.Basic.class);
        when(session.isOpen()).thenReturn(true);
        when(session.getBasicRemote()).thenReturn(remote);
        List<JSONObject> messages = new ArrayList<>();
        doAnswer(inv -> { synchronized (messages) { messages.add(JSONObject.parseObject(inv.getArgument(0))); } return null; })
                .when(remote).sendText(anyString());
        WebSocketServer ws = new WebSocketServer();
        ws.onOpen(session, JwtUtil.createJWT(String.valueOf(id)));
        return new Client(ws, session, messages);
    }

    private String match(Client client, int botId, String mode) {
        client.ws.onMessage("{\"event\":\"start-matching\",\"bot_id\":" + botId + ",\"mode\":\"" + mode + "\"}", client.session);
        ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(rest, atLeastOnce()).postForObject(eq(ADD), data.capture(), eq(String.class));
        return (String) data.getValue().getFirst("match_id");
    }

    private Bot bot(int id, int owner) {
        return new Bot(id, owner, "bot", "", "code", 1500, 0, new Date(), new Date());
    }

    @Test
    void refusesAnotherUsersBotBeforeItEntersTheMatchingPool() throws Exception {
        Client client = connect(1);
        when(WebSocketServer.botMapper.selectById(77)).thenReturn(bot(77, 2));
        client.ws.onMessage("{\"event\":\"start-matching\",\"bot_id\":77,\"mode\":\"ai\"}", client.session);
        verifyNoInteractions(rest);
        assertTrue(client.messages.get(0).getString("error_message").contains("不属于"));
    }

    @Test
    void largeBotIdIsForwardedUnchangedWithAnExplicitMode() throws Exception {
        Client client = connect(1);
        when(WebSocketServer.botMapper.selectById(1666)).thenReturn(bot(1666, 1));
        match(client, 1666, "pvp");
        ArgumentCaptor<MultiValueMap> data = ArgumentCaptor.forClass(MultiValueMap.class);
        verify(rest).postForObject(eq(ADD), data.capture(), eq(String.class));
        assertEquals("1666", data.getValue().getFirst("bot_id"));
        assertEquals("pvp", data.getValue().getFirst("mode"));
    }

    @Test
    void disconnectCancelsItsOwnReservation() throws Exception {
        Client client = connect(1);
        String id = match(client, -1, "pvp");
        client.ws.onClose();
        assertFalse(WebSocketServer.users.containsKey(1));
        verify(rest).postForObject(eq(REMOVE), argThat((MultiValueMap<String, String> m) -> id.equals(m.getFirst("match_id"))), eq(String.class));
    }

    @Test
    void disconnectBeforeAddReturnsIsCancelledAgain() throws Exception {
        Client client = connect(1);
        doAnswer(inv -> { client.ws.onClose(); return "ok"; })
                .when(rest).postForObject(eq(ADD), any(), eq(String.class));
        match(client, -1, "pvp");
        verify(rest, times(2)).postForObject(eq(REMOVE), any(), eq(String.class));
    }

    @Test
    void replacingConnectionAndClosingOldOneDoesNotCancelNewReservation() throws Exception {
        Client old = connect(1);
        String oldId = match(old, -1, "pvp");
        Client current = connect(1);
        String newId = match(current, -1, "pvp");
        old.ws.onClose();
        assertNotEquals(oldId, newId);
        assertSame(current.ws, WebSocketServer.users.get(1));
        verify(rest, times(1)).postForObject(eq(REMOVE), any(), eq(String.class));
    }

    @Test
    void offlinePlayerCannotStartGhostGameAndOnlineOpponentCanRetry() throws Exception {
        Client a = connect(1), b = connect(2);
        String aMatch = match(a, -1, "pvp"), bMatch = match(b, -1, "pvp");
        a.ws.onClose();
        assertFalse(WebSocketServer.startGame(1, -1, 2, -1, aMatch, bMatch));
        assertTrue(WebSocketServer.games.isEmpty());
        assertEquals("match-error", b.messages.get(0).getString("event"));
        assertNotEquals(bMatch, match(b, -1, "pvp"));
    }

    @Test
    void ownershipIsRecheckedWhenTheGameStarts() throws Exception {
        Client a = connect(1), b = connect(2);
        when(WebSocketServer.botMapper.selectById(77)).thenReturn(bot(77, 1));
        String aMatch = match(a, 77, "pvp"), bMatch = match(b, -1, "pvp");
        when(WebSocketServer.botMapper.selectById(77)).thenReturn(bot(77, 2));
        assertFalse(WebSocketServer.startGame(1, 77, 2, -1, aMatch, bMatch));
        assertTrue(WebSocketServer.games.isEmpty());
    }

    @Test
    void validMatchStartsAndIllegalMoveProducesAResultForBothPlayers() throws Exception {
        Client a = connect(1), b = connect(2);
        String aMatch = match(a, -1, "pvp"), bMatch = match(b, -1, "pvp");
        assertTrue(WebSocketServer.startGame(1, -1, 2, -1, aMatch, bMatch));
        var game = a.ws.game;
        assertNotNull(game);
        assertSame(game, b.ws.game);
        assertTrue(game.receivePlayerMove(1, game.getGameId(), 0, 4));
        assertTrue(game.receivePlayerMove(2, game.getGameId(), 0, 0));
        assertTimeoutPreemptively(Duration.ofSeconds(3), () -> {
            while (!WebSocketServer.games.isEmpty()) Thread.sleep(10);
        });
        assertEquals("start-matching", a.messages.get(0).getString("event"));
        assertEquals("A", a.messages.get(1).getString("loser"));
        assertEquals("A", b.messages.get(1).getString("loser"));
    }

    @Test
    void unavailableSandboxRejectsAiMatchingWithoutStartingAGame() throws Exception {
        Client client = connect(1);
        when(rest.getForObject(anyString(), eq(Boolean.class))).thenReturn(false);
        client.ws.onMessage("{\"event\":\"start-matching\",\"bot_id\":-1,\"mode\":\"ai\"}", client.session);
        verify(rest, never()).postForObject(anyString(), any(), eq(String.class));
        assertTrue(client.messages.get(0).getString("error_message").contains("不可用"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void reconnectRestoresActiveGameAndCannotEnterAnotherMatch(boolean closeFirst) throws Exception {
        Client old = connect(1);
        Game game = new Game(13, 14, 20, 1, null, 2, null);
        old.ws.game = game;
        WebSocketServer.games.put(game.getGameId(), game);
        WebSocketServer.activeGames.put(1, game);
        if (closeFirst) old.ws.onClose();
        Client replacement = connect(1);
        assertSame(game, replacement.ws.game);
        assertEquals(game.getGameId(), replacement.messages.get(0).getJSONObject("game").getString("game_id"));
        replacement.ws.onMessage("{\"event\":\"start-matching\",\"bot_id\":-1,\"mode\":\"pvp\"}", replacement.session);
        verify(rest, never()).postForObject(eq(ADD), any(), eq(String.class));
        assertTrue(replacement.messages.get(1).getString("error_message").contains("对局中"));
        WebSocketServer.finishGame(game);
        assertNull(replacement.ws.game);
        assertTrue(WebSocketServer.activeGames.isEmpty());
    }

    @Test
    void resumeContainsOnlyMovesAlreadyBroadcast() throws Exception {
        Game game = new Game(13, 14, 20, 1, null, 2, null);
        game.getPlayerA().getSteps().addAll(List.of(0, 1));
        game.getPlayerB().getSteps().addAll(List.of(2, 3));
        ReflectionTestUtils.setField(game, "round", 1);
        WebSocketServer.activeGames.put(1, game);
        WebSocketServer.games.put(game.getGameId(), game);
        JSONObject board = connect(1).messages.get(0).getJSONObject("game");
        assertEquals(1, board.getInteger("round"));
        assertEquals("0", board.getString("a_steps"));
        assertEquals("2", board.getString("b_steps"));
    }
}
