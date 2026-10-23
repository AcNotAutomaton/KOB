package com.kob.matchingsystem.service;

public interface MatchingService {
    String addPlayer(Integer userId, Integer rating, Integer botId, String mode, String matchId);
    String removePlayer(Integer userId, String matchId);
}
