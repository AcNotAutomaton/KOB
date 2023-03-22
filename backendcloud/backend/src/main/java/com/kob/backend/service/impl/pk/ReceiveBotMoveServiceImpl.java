package com.kob.backend.service.impl.pk;

import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.consumer.utils.Game;
import com.kob.backend.service.pk.ReceiveBotMoveService;
import org.springframework.stereotype.Service;

@Service
public class ReceiveBotMoveServiceImpl implements ReceiveBotMoveService {
    @Override
    public String receiveBotMove(Integer userId, Integer direction, Integer enemy) {
        System.out.println("receive bot move: " + userId + " " + direction + " ");
        System.out.println("ai不动了....................");
        System.out.println("对手id" + enemy);
        if (WebSocketServer.users.get(userId) != null||WebSocketServer.users.get(enemy) != null) {
            System.out.println("ai又动了....................");
            Game game = null;
            if(enemy.equals(4)) game = WebSocketServer.users.get(userId).game;
            else game = WebSocketServer.users.get(enemy).game;
            if (game != null) {
                if (game.getPlayerA().getId().equals(userId)) {
                    game.setNextStepA(direction);
                } else if (game.getPlayerB().getId().equals(userId)) {
                    game.setNextStepB(direction);
                }
            }
        }
        return "receive bot move success";
    }
}