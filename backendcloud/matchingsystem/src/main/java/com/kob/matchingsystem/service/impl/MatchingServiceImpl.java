package com.kob.matchingsystem.service.impl;

import com.kob.matchingsystem.service.MatchingService;
import com.kob.matchingsystem.service.impl.utils.MatchingPool;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class MatchingServiceImpl implements MatchingService {
    @Autowired
    private MatchingPool matchingPool;

    @Override
    public String addPlayer(Integer userId, Integer rating, Integer botId, String mode, String matchId) {
        matchingPool.addPlayer(userId, rating, botId, mode, matchId);
        return "add player success";
    }

    @Override
    public String removePlayer(Integer userId, String matchId) {
        matchingPool.removePlayer(userId, matchId);
        return "remove player success";
    }
}
