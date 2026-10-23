package com.kob.backend.service.impl.pk;

import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.service.pk.StartGameService;
import org.springframework.stereotype.Service;

@Service
public class StartGameServiceImpl implements StartGameService {
    @Override
    public String startGame(Integer aId, Integer aBotId, Integer bId, Integer bBotid, String aMatchId, String bMatchId) {
        return WebSocketServer.startGame(aId, aBotId, bId, bBotid, aMatchId, bMatchId)
                ? "start game success" : "game cancelled";
    }
}
