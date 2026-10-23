package com.kob.botrunningsystem.service.impl.utils;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

/** 用户代码只交给容器执行；此服务 JVM 不编译、不加载用户代码。 */
@Component
public class Consumer {
    private static final String CALLBACK_URL = "http://127.0.0.1:3000/pk/receive/bot/move/";
    private final RestTemplate restTemplate;
    private final ContainerSandbox sandbox;

    @Autowired
    public Consumer(RestTemplate restTemplate, ContainerSandbox sandbox) {
        this.restTemplate = restTemplate;
        this.sandbox = sandbox;
    }

    public void consume(Bot bot) {
        Integer direction = sandbox.execute(bot.getBotCode(), bot.getInput());
        if (direction == null) return;
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", bot.getUserId().toString());
        data.add("enemy_id", bot.getEnemy().toString());
        data.add("direction", direction.toString());
        data.add("game_id", bot.getGameId());
        data.add("round", bot.getRound().toString());
        restTemplate.postForObject(CALLBACK_URL, data, String.class);
    }
}