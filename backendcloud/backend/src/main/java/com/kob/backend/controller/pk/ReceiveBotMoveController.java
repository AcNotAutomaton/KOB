package com.kob.backend.controller.pk;

import com.kob.backend.service.pk.ReceiveBotMoveService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class ReceiveBotMoveController {
    @Autowired
    private ReceiveBotMoveService receiveBotMoveService;

    @PostMapping("/pk/receive/bot/move/")
    public String receiveBotMove(@RequestParam MultiValueMap<String, String> data) {
        System.out.println("移动吧");
        Integer userId = Integer.parseInt(Objects.requireNonNull(data.getFirst("user_id")));
        Integer direction = Integer.parseInt(Objects.requireNonNull(data.getFirst("direction")));
        Integer enemy = Integer.parseInt(Objects.requireNonNull(data.getFirst("enemy_id")));

        return receiveBotMoveService.receiveBotMove(userId, direction, enemy);
    }
}