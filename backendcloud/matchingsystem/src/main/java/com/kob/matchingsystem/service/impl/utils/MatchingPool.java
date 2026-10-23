package com.kob.matchingsystem.service.impl.utils;


import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 匹配池：由 Spring 管理（@Component），@PostConstruct 启动匹配线程，
 * 不再依赖 ServiceImpl 中的静态手动 new（旧实现存在双实例隐患）。
 */
@Component
public class MatchingPool extends Thread {

    private static final Logger log = LoggerFactory.getLogger(MatchingPool.class);

    /** -1：亲自出马；正整数始终为真实 Bot ID，匹配模式单独传递。 */
    public static final int PLAY_MYSELF = -1;

    // ===== AI 对手（人机对战）固定使用 user 4 / bot 3 =====
    private static final int AI_USER_ID = 4;
    private static final int AI_BOT_ID = 3;
    private static final int AI_RATING = 1500;

    private final List<Player> players = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    private final RestTemplate restTemplate;
    private final static String startGameUrl = "http://127.0.0.1:3000/pk/start/game/";

    @Autowired
    public MatchingPool(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
        this.setName("matching-pool");
        this.setDaemon(true);
    }

    @PostConstruct
    public void startMatching() {
        this.start();
    }

    public void addPlayer(Integer userId, Integer rating, Integer botId, String mode, String matchId) {
        if (botId == null || botId != PLAY_MYSELF && botId <= 0 || matchId == null || matchId.isBlank()
                || !"pvp".equals(mode) && !"ai".equals(mode)) {
            throw new IllegalArgumentException("Invalid matching request");
        }
        if ("ai".equals(mode)) {
            sendResult(new Player(userId, rating, botId, 10, matchId),
                    new Player(AI_USER_ID, AI_RATING, AI_BOT_ID, 10, null));
            return;
        }

        lock.lock();
        try {
            // 旧连接迟到的 add 不能替换新连接的预约；后端在开局时核对有效预约。
            players.removeIf(player -> player.getUserId().equals(userId) && player.getMatchId().equals(matchId));
            players.add(new Player(userId, rating, botId, 0, matchId));
        } finally {
            lock.unlock();
        }
    }

    public void removePlayer(Integer userId, String matchId) {
        lock.lock();
        try {
            players.removeIf(player -> player.getUserId().equals(userId) && player.getMatchId().equals(matchId));
        } finally {
            lock.unlock();
        }
    }

    private void increaseWaitingTime() {  // 将所有当前玩家的等待时间加1
        for (Player player : players) {
            player.setWaitingTime(player.getWaitingTime() + 1);
        }
    }

    private boolean checkMatched(Player a, Player b) {  // 判断两名玩家是否匹配
        int ratingDelta = Math.abs(a.getRating() - b.getRating());
        int waitingTime = Math.max(a.getWaitingTime(), b.getWaitingTime());
        return ratingDelta <= waitingTime * 10;
    }

    private void sendResult(Player a, Player b) {  // 返回匹配结果
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("a_id", a.getUserId().toString());
        data.add("a_bot_id", a.getBotId().toString());
        data.add("b_id", b.getUserId().toString());
        data.add("b_bot_id", b.getBotId().toString());
        data.add("a_match_id", a.getMatchId());
        if (b.getMatchId() != null) data.add("b_match_id", b.getMatchId());
        restTemplate.postForObject(startGameUrl, data, String.class);
    }

    private void matchPlayers() {  // 尝试匹配所有玩家
        boolean[] used = new boolean[players.size()];
        for (int i = 0; i < players.size(); i++) {
            if (used[i]) continue;
            for (int j = i + 1; j < players.size(); j++) {
                if (used[j]) continue;
                Player a = players.get(i), b = players.get(j);
                if (a.getUserId().equals(b.getUserId())) continue;
                if (checkMatched(a, b)) {
                    used[i] = used[j] = true;
                    sendResult(a, b);
                    break;
                }
            }
        }

        List<Player> newPlayers = new ArrayList<>();
        for (int i = 0; i < players.size(); i++) {
            if (!used[i]) {
                newPlayers.add(players.get(i));
            }
        }
        players.clear();
        players.addAll(newPlayers);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }

            try {
                lock.lock();
                try {
                    increaseWaitingTime();
                    matchPlayers();
                } finally {
                    lock.unlock();
                }
            } catch (Exception e) {
                // 单轮匹配异常（如 backend 暂时不可达）不应终止匹配线程
                log.error("匹配循环异常", e);
            }
        }
    }
}
