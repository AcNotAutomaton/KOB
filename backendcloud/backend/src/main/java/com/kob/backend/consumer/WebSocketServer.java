package com.kob.backend.consumer;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.consumer.utils.Game;
import com.kob.backend.consumer.utils.JwtAuthentication;
import com.kob.backend.mapper.*;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.User;
import com.kob.backend.service.impl.pk.GameResultService;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Component
@ServerEndpoint("/websocket/{token}")
public class WebSocketServer {
    private static final Logger log = LoggerFactory.getLogger(WebSocketServer.class);
    public static final ConcurrentHashMap<Integer, WebSocketServer> users = new ConcurrentHashMap<>();
    public static final ConcurrentHashMap<String, Game> games = new ConcurrentHashMap<>();
    // 只登记真实参与者；共享 AI 账号可同时作为多局的机器人对手。
    public static final ConcurrentHashMap<Integer, Game> activeGames = new ConcurrentHashMap<>();
    // 仅保护连接、匹配预约和开局提交；持锁时不调用其他 HTTP 服务。
    private static final Object MATCH_LOCK = new Object();
    private static final int AI_USER_ID = 4;
    private static final int AI_BOT_ID = 3;
    private User user;
    private Session session;
    private String matchingId;
    private Integer matchingBotId;
    private String matchingMode;
    public volatile Game game;

    public static UserMapper userMapper;
    public static RecordMapper recordMapper;
    public static BotMapper botMapper;
    public static GameBotMapper gameBotMapper;
    public static RestTemplate restTemplate;
    public static GameResultService gameResultService;
    private static final ExecutorService GAME_EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "game-judge");
        t.setDaemon(true);
        return t;
    });
    private static final String ADD_PLAYER_URL = "http://127.0.0.1:3001/player/add/";
    private static final String REMOVE_PLAYER_URL = "http://127.0.0.1:3001/player/remove/";

    @Autowired public void setUserMapper(UserMapper mapper) { userMapper = mapper; }
    @Autowired public void setRecordMapper(RecordMapper mapper) { recordMapper = mapper; }
    @Autowired public void setBotMapper(BotMapper mapper) { botMapper = mapper; }
    @Autowired public void setGameBotMapper(GameBotMapper mapper) { gameBotMapper = mapper; }
    @Autowired public void setRestTemplate(RestTemplate template) { restTemplate = template; }
    @Autowired public void setGameResultService(GameResultService service) { gameResultService = service; }

    @OnOpen
    public void onOpen(Session session, @PathParam("token") String token) throws IOException {
        this.session = session;
        try {
            user = userMapper.selectById(JwtAuthentication.getUserId(token));
        } catch (Exception e) {
            session.close();
            return;
        }
        if (user == null) {
            session.close();
            return;
        }
        WebSocketServer old;
        synchronized (MATCH_LOCK) {
            old = users.put(user.getId(), this);
            Game active = activeGames.get(user.getId());
            game = active != null && active.isPlaying() ? active : null;
        }
        if (old != null && old != this) {
            old.stopMatching();
            old.closeSession();
        }
        Game active = game;
        if (active != null) {
            Integer opponentId = active.getPlayerA().getId().equals(user.getId())
                    ? active.getPlayerB().getId() : active.getPlayerA().getId();
            User opponent = userMapper.selectById(opponentId);
            if (opponent != null) active.sendStateTo(this, opponent);
        }
    }

    @OnClose
    public void onClose() {
        boolean removed;
        synchronized (MATCH_LOCK) {
            removed = user != null && users.remove(user.getId(), this);
        }
        if (removed) stopMatching();
    }

    private static Bot ownedBot(Integer userId, Integer botId) {
        if (Integer.valueOf(-1).equals(botId)) return null;
        if (botId == null || botId <= 0) throw new IllegalArgumentException("无效的 Bot");
        Bot bot = botMapper.selectById(botId);
        if (bot == null || !userId.equals(bot.getUserId())) {
            throw new IllegalArgumentException("Bot 不存在或不属于当前用户");
        }
        return bot;
    }

    private boolean reserved(String id, Integer botId, String mode) {
        return id != null && id.equals(matchingId) && Objects.equals(botId, matchingBotId)
                && mode.equals(matchingMode) && session != null && session.isOpen();
    }

    public static boolean startGame(Integer aId, Integer aBotId, Integer bId, Integer bBotId,
                                    String aMatchId, String bMatchId) {
        Game newGame = null;
        WebSocketServer wsA;
        WebSocketServer wsB;
        User a = userMapper.selectById(aId), b = userMapper.selectById(bId);
        Bot botA = null, botB = null;
        boolean validBots = true;
        try {
            botA = ownedBot(aId, aBotId);
            botB = ownedBot(bId, bBotId);
        } catch (IllegalArgumentException e) {
            validBots = false;
        }
        synchronized (MATCH_LOCK) {
            wsA = users.get(aId);
            wsB = users.get(bId);
            boolean ai = wsA != null && "ai".equals(wsA.matchingMode);
            boolean availableA = wsA != null && !hasActiveGame(aId) && (wsA.game == null || !wsA.game.isPlaying());
            boolean availableB = wsB != null && !hasActiveGame(bId) && (wsB.game == null || !wsB.game.isPlaying());
            boolean pair = ai
                    ? Integer.valueOf(AI_USER_ID).equals(bId) && Integer.valueOf(AI_BOT_ID).equals(bBotId)
                        && bMatchId == null
                    : availableB && wsB.reserved(bMatchId, bBotId, "pvp");
            if (validBots && a != null && b != null && !aId.equals(bId) && availableA
                    && wsA.reserved(aMatchId, aBotId, ai ? "ai" : "pvp") && pair) {
                newGame = new Game(13, 14, 20, aId, botA, bId, botB);
                newGame.createMap();
                wsA.matchingId = null;
                wsA.game = newGame;
                activeGames.put(aId, newGame);
                if (!ai) {
                    wsB.matchingId = null;
                    wsB.game = newGame;
                    activeGames.put(bId, newGame);
                }
                games.put(newGame.getGameId(), newGame);
            }
        }
        if (newGame == null) {
            cancelReservation(aId, aMatchId);
            cancelReservation(bId, bMatchId);
            return false;
        }
        newGame.sendStateTo(wsA, b);
        if (wsB != null && wsB.game == newGame) newGame.sendStateTo(wsB, a);
        GAME_EXECUTOR.execute(newGame);
        return true;
    }

    private static boolean hasActiveGame(Integer userId) {
        Game active = activeGames.get(userId);
        return active != null && active.isPlaying();
    }

    private static void cancelReservation(Integer userId, String id) {
        WebSocketServer ws;
        synchronized (MATCH_LOCK) {
            ws = userId == null ? null : users.get(userId);
            if (ws == null || id == null || !id.equals(ws.matchingId)) return;
            ws.matchingId = null;
        }
        ws.matchError("匹配已取消，请重新匹配");
    }

    public static void finishGame(Game finished) {
        games.remove(finished.getGameId(), finished);
        synchronized (MATCH_LOCK) {
            for (Integer id : new Integer[]{finished.getPlayerA().getId(), finished.getPlayerB().getId()}) {
                activeGames.remove(id, finished);
                WebSocketServer ws = users.get(id);
                if (ws != null && ws.game == finished) ws.game = null;
            }
        }
    }

    private void startMatching(Integer botId, String mode) {
        if (!"pvp".equals(mode) && !"ai".equals(mode)) throw new IllegalArgumentException("无效的匹配模式");
        ownedBot(user.getId(), botId);
        if (botId > 0 || "ai".equals(mode)) {
            try {
                if (!Boolean.TRUE.equals(restTemplate.getForObject("http://127.0.0.1:3002/bot/health/", Boolean.class))) {
                    throw new IllegalArgumentException("Bot 执行服务暂不可用，请联系管理员");
                }
            } catch (IllegalArgumentException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalArgumentException("Bot 执行服务暂不可用，请联系管理员");
            }
        }
        String id = UUID.randomUUID().toString();
        synchronized (MATCH_LOCK) {
            if (users.get(user.getId()) != this || !session.isOpen()) return;
            if (matchingId != null || hasActiveGame(user.getId()) || game != null && game.isPlaying()) {
                throw new IllegalArgumentException("正在匹配或对局中");
            }
            matchingId = id;
            matchingBotId = botId;
            matchingMode = mode;
        }
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", user.getId().toString());
        data.add("rating", userMapper.selectById(user.getId()).getRating().toString());
        data.add("bot_id", botId.toString());
        data.add("mode", mode);
        data.add("match_id", id);
        try {
            restTemplate.postForObject(ADD_PLAYER_URL, data, String.class);
        } catch (Exception e) {
            cancelReservation(user.getId(), id);
            removeFromPool(id);
            return;
        }
        // 取消/断线可能先于 add 请求到达匹配服务；按预约编号删除不会影响后续匹配。
        synchronized (MATCH_LOCK) {
            if (id.equals(matchingId)) return;
        }
        removeFromPool(id);
    }

    private void stopMatching() {
        String id;
        synchronized (MATCH_LOCK) {
            id = matchingId;
            matchingId = null;
        }
        if (id != null) removeFromPool(id);
    }

    private void removeFromPool(String id) {
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", user.getId().toString());
        data.add("match_id", id);
        try {
            restTemplate.postForObject(REMOVE_PLAYER_URL, data, String.class);
        } catch (Exception e) {
            log.warn("取消匹配失败，userId={} matchId={}", user.getId(), id, e);
        }
    }

    @OnMessage
    public void onMessage(String message, Session ignored) {
        if (user == null || users.get(user.getId()) != this) return;
        try {
            JSONObject data = JSONObject.parseObject(message);
            switch (data.getString("event")) {
                case "start-matching" -> startMatching(data.getInteger("bot_id"), data.getString("mode"));
                case "stop-matching" -> stopMatching();
                case "move" -> {
                    Game current = game;
                    if (current != null) current.receivePlayerMove(user.getId(), data.getString("game_id"),
                            data.getInteger("round"), data.getInteger("direction"));
                }
                default -> { }
            }
        } catch (IllegalArgumentException e) {
            matchError(e.getMessage());
        } catch (Exception e) {
            log.warn("处理 WebSocket 消息失败，userId={}", user.getId(), e);
            matchError("请求失败，请重试");
        }
    }

    private void matchError(String error) {
        JSONObject resp = new JSONObject();
        resp.put("event", "match-error");
        resp.put("error_message", error);
        sendMessage(resp.toJSONString());
    }

    @OnError
    public void onError(Session ignored, Throwable error) {
        log.error("WebSocket 异常，userId={}", user != null ? user.getId() : null, error);
    }

    public void sendMessage(String message) {
        if (session == null) return;
        synchronized (session) {
            if (!session.isOpen()) return;
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException | IllegalStateException e) {
                log.warn("WebSocket 发送失败", e);
            }
        }
    }

    private void closeSession() {
        try {
            session.close();
        } catch (IOException e) {
            log.warn("关闭旧会话失败", e);
        }
    }
}
