package com.kob.backend.service.impl.pk;

import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.consumer.utils.Game;
import com.kob.backend.service.pk.ReceiveBotMoveService;
import org.springframework.stereotype.Service;

@Service
public class ReceiveBotMoveServiceImpl implements ReceiveBotMoveService {
    @Override
    public String receiveBotMove(Integer userId, Integer direction, Integer enemy, String gameId, Integer round) {
        Game game = gameId == null ? null : WebSocketServer.games.get(gameId);
        return game != null && game.receiveBotMove(userId, enemy, gameId, round, direction)
                ? "receive bot move success" : "stale or invalid bot move";
    }
}
