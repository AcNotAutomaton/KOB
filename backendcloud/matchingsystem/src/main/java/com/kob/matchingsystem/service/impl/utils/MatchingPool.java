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

    // ===== 前端与匹配系统的 botId 编码约定 =====
    /** -1：亲自出马；-2：本人 VS AI；botId*1000+666（如 10666）：指定 Bot VS AI */
    public static final int PLAY_MYSELF = -1;
    public static final int PLAY_VS_AI = -2;
    public static final int BOT_VS_AI_CODE = 666;

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

    public void addPlayer(Integer userId, Integer rating, Integer botId) {
        if (botId != null && botId > BOT_VS_AI_CODE) {
            // 指定 Bot VS AI：前端编码为 botId*1000+666，还原真实 botId
            int realBotId = botId / 1000;
            sendResult(new Player(userId, rating, realBotId, 10),
                    new Player(AI_USER_ID, AI_RATING, AI_BOT_ID, 10));
            return;
        }
        if (botId != null && botId == PLAY_VS_AI) {
            // 本人 VS AI
            sendResult(new Player(userId, rating, PLAY_VS_AI, 10),
                    new Player(AI_USER_ID, AI_RATING, AI_BOT_ID, 10));
            return;
        }

        lock.lock();
        try {
            players.removeIf(player -> player.getUserId().equals(userId));
            players.add(new Player(userId, rating, botId, 0));
        } finally {
            lock.unlock();
        }
    }

    public void removePlayer(Integer userId) {
        lock.lock();
        try {
            players.removeIf(player -> player.getUserId().equals(userId));
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
        restTemplate.postForObject(startGameUrl, data, String.class);
    }

    private void matchPlayers() {  // 尝试匹配所有玩家
        boolean[] used = new boolean[players.size()];
        for (int i = 0; i < players.size(); i++) {
            if (used[i]) continue;
            for (int j = i + 1; j < players.size(); j++) {
                if (used[j]) continue;
                Player a = players.get(i), b = players.get(j);
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
