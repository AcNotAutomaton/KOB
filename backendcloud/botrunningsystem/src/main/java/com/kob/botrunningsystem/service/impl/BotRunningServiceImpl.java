package com.kob.botrunningsystem.service.impl;

import com.kob.botrunningsystem.service.BotRunningService;
import com.kob.botrunningsystem.service.impl.utils.BotPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class BotRunningServiceImpl implements BotRunningService {
    @Autowired
    private BotPool botPool;

    @Override
    public String addBot(Integer userId, String botCode, String input, Integer enemy) {
        botPool.addBot(userId, botCode, input, enemy);
        return "add bot success";
    }
}
