package com.kob.backend.service.impl.pk;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kob.backend.consumer.utils.Player;
import com.kob.backend.mapper.BotMapper;
import com.kob.backend.mapper.GameBotMapper;
import com.kob.backend.mapper.RecordMapper;
import com.kob.backend.mapper.UserMapper;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.GameBot;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.TreeMap;

/** 裁判线程通过 Spring 代理调用，整局统计和战绩共用一个事务。 */
@Service
public class GameResultService {
    private final UserMapper users;
    private final BotMapper bots;
    private final RecordMapper records;
    private final GameBotMapper gameBots;

    public GameResultService(UserMapper users, BotMapper bots, RecordMapper records, GameBotMapper gameBots) {
        this.users = users;
        this.bots = bots;
        this.records = records;
        this.gameBots = gameBots;
    }

    @Transactional
    public void save(Player a, Player b, String map, String loser) {
        int deltaA = 0, deltaB = 0;
        if ("A".equals(loser)) {
            deltaA = -2;
            deltaB = 5;
        } else if ("B".equals(loser)) {
            deltaA = 5;
            deltaB = -2;
        }
        int botScoreA = "A".equals(loser) ? -5 : "B".equals(loser) ? 5 : 0;
        // 多局同时结算时按主键顺序加锁，避免 A/B 身份互换造成反向锁等待。
        TreeMap<Integer, Integer> botScores = new TreeMap<>();
        if (a.getBotId() != null && a.getBotId() > 0) botScores.put(a.getBotId(), botScoreA);
        if (b.getBotId() != null && b.getBotId() > 0) botScores.put(b.getBotId(), -botScoreA);
        botScores.forEach(this::updateBot);
        TreeMap<Integer, Integer> userDeltas = new TreeMap<>();
        userDeltas.put(a.getId(), deltaA);
        userDeltas.put(b.getId(), deltaB);
        userDeltas.forEach(this::updateUser);
        Record record = new Record(null, a.getId(), a.getSx(), a.getSy(), b.getId(), b.getSx(), b.getSy(),
                a.getStepsString(), b.getStepsString(), map, loser, new Date());
        records.insert(record);
        gameBots.insert(new GameBot(null, record.getId(), a.getBotId(), a.getId(), b.getBotId(), b.getId()));
    }

    private void updateUser(Integer id, int delta) {
        users.update(null, new LambdaUpdateWrapper<User>().eq(User::getId, id)
                .setSql("rating = COALESCE(rating, 1500) + " + delta).setSql("times = COALESCE(times, 0) + 1"));
    }

    private void updateBot(Integer id, int score) {
        if (id == null || id <= 0) return;
        bots.update(null, new LambdaUpdateWrapper<Bot>().eq(Bot::getId, id)
                .setSql("rating = COALESCE(rating, 1500) + " + score).setSql("count = COALESCE(count, 0) + 1"));
    }
}
