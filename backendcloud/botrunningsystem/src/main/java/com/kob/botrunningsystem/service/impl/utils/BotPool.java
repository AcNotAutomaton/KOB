package com.kob.botrunningsystem.service.impl.utils;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Bot 执行池：由 Spring 管理（@Component），不再手动 new。
 * 每个 Bot 的沙箱执行提交到固定线程池，单次失败或超时不影响其他任务。
 */
@Component
public class BotPool {
    private static final Logger log = LoggerFactory.getLogger(BotPool.class);
    private static final int POOL_SIZE = 4;

    private final ExecutorService executor = new ThreadPoolExecutor(POOL_SIZE, POOL_SIZE, 0, TimeUnit.MILLISECONDS,
            new ArrayBlockingQueue<>(64), runnable -> {
        Thread t = new Thread(runnable, "bot-runner");
        t.setDaemon(true);
        return t;
    });

    private final Consumer consumer;

    @Autowired
    public BotPool(Consumer consumer) {
        this.consumer = consumer;
    }

    public void addBot(Integer userId, String botCode, String input, Integer enemy, String gameId, Integer round) {
        Bot bot = new Bot(userId, botCode, input, enemy, gameId, round);
        executor.submit(() -> {
            try {
                consumer.consume(bot);
            } catch (Exception e) {
                log.error("Bot 任务执行异常，userId={}", userId, e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            executor.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
