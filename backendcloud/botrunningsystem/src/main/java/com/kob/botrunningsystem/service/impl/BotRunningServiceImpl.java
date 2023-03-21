package com.kob.botrunningsystem.service.impl;

import com.kob.botrunningsystem.service.BotRunningService;
import com.kob.botrunningsystem.service.impl.utils.BotPool;
import org.springframework.stereotype.Service;

@Service
public class BotRunningServiceImpl implements BotRunningService {
    public final static BotPool botPool = new BotPool();

    @Override
    public String addBot(Integer userId, String botCode, String input, Integer enemy) {
        System.out.println("add bot: " + userId + " " + botCode + " " + input);
        botPool.addBot(userId, botCode, input, enemy);
        return "add bot success";
    }
}