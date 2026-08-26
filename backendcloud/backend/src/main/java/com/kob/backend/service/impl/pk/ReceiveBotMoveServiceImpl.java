package com.kob.backend.service.impl.pk;

import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.consumer.utils.Game;
import com.kob.backend.service.pk.ReceiveBotMoveService;
import org.springframework.stereotype.Service;

@Service
public class ReceiveBotMoveServiceImpl implements ReceiveBotMoveService {
    @Override
    public String receiveBotMove(Integer userId, Integer direction, Integer enemy) {
        // Bot 回传操作时本人可能已掉线（或对手是 AI 无连接），从任一在线一方的连接取对局
        Game game = null;
        WebSocketServer ws = WebSocketServer.users.get(userId);
        if (ws != null) {
            game = ws.game;
        }
        if (game == null && enemy != null) {
            ws = WebSocketServer.users.get(enemy);
            if (ws != null) {
                game = ws.game;
            }
        }

        if (game != null) {
            if (game.getPlayerA().getId().equals(userId)) {
                game.setNextStepA(direction);
            } else if (game.getPlayerB().getId().equals(userId)) {
                game.setNextStepB(direction);
            }
        }
        return "receive bot move success";
    }
}
