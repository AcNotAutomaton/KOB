package com.kob.backend.consumer.utils;

import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.GameBot;
import com.kob.backend.pojo.Record;
import com.kob.backend.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 对战裁判逻辑。实现 Runnable，由 WebSocketServer 的游戏线程池调度（不再继承 Thread 裸起线程）。
 */
public class Game implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(Game.class);

    private final Integer rows;
    private final Integer cols;
    private final Integer inner_walls_count;
    private final int[][] g;
    private final static int[] dx = {-1, 0, 1, 0}, dy = {0, 1, 0, -1};
    private final Player playerA, playerB;
    private Integer nextStepA = null;
    private Integer nextStepB = null;
    private final ReentrantLock lock = new ReentrantLock();
    /** 两名玩家的下一步都就绪时唤醒裁判线程，替代原来的 50 轮 sleep 轮询 */
    private final Condition stepReady = lock.newCondition();
    private String status = "playing";  // playing -> finished
    private String loser = "";  // all: 平局，A: A输，B: B输
    private final static String addBotUrl = "http://127.0.0.1:3002/bot/add/";

    /** 等待双方输入的超时时间（与旧实现 50 次 * 100ms 轮询一致） */
    private static final long NEXT_STEP_TIMEOUT_MS = 5000;


    public Game(
            Integer rows,
            Integer cols,
            Integer inner_walls_count,
            Integer idA,
            Bot botA,
            Integer idB,
            Bot botB
    ) {
        this.rows = rows;
        this.cols = cols;
        this.inner_walls_count = inner_walls_count;
        this.g = new int[rows][cols];

        Integer botIdA = -1, botIdB = -1;
        String botCodeA = "", botCodeB = "";
        if (botA != null) {
            botIdA = botA.getId();
            botCodeA = botA.getContent();
        }
        if (botB != null) {
            botIdB = botB.getId();
            botCodeB = botB.getContent();
        }
        playerA = new Player(idA, botIdA, botCodeA, rows - 2, 1, new ArrayList<>());
        playerB = new Player(idB, botIdB, botCodeB, 1, cols - 2, new ArrayList<>());
    }

    public Player getPlayerA() {
        return playerA;
    }

    public Player getPlayerB() {
        return playerB;
    }

    public void setNextStepA(Integer nextStepA) {
        lock.lock();
        try {
            this.nextStepA = nextStepA;
            stepReady.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void setNextStepB(Integer nextStepB) {
        lock.lock();
        try {
            this.nextStepB = nextStepB;
            stepReady.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public int[][] getG() {
        return g;
    }

    private boolean check_connectivity(int sx, int sy, int tx, int ty) {
        if (sx == tx && sy == ty) return true;
        g[sx][sy] = 1;

        for (int i = 0; i < 4; i ++ ) {
            int x = sx + dx[i], y = sy + dy[i];
            if (x >= 0 && x < this.rows && y >= 0 && y < this.cols && g[x][y] == 0) {
                if (check_connectivity(x, y, tx, ty)) {
                    g[sx][sy] = 0;
                    return true;
                }
            }
        }

        g[sx][sy] = 0;
        return false;
    }

    private boolean draw() {  // 画地图
        for (int i = 0; i < this.rows; i ++ ) {
            for (int j = 0; j < this.cols; j ++ ) {
                g[i][j] = 0;
            }
        }

        for (int r = 0; r < this.rows; r ++ ) {
            g[r][0] = g[r][this.cols - 1] = 1;
        }
        for (int c = 0; c < this.cols; c ++ ) {
            g[0][c] = g[this.rows - 1][c] = 1;
        }

        Random random = new Random();
        for (int i = 0; i < this.inner_walls_count / 2; i ++ ) {
            for (int j = 0; j < 1000; j ++ ) {
                int r = random.nextInt(this.rows);
                int c = random.nextInt(this.cols);

                if (g[r][c] == 1 || g[this.rows - 1 - r][this.cols - 1 - c] == 1)
                    continue;
                if (r == this.rows - 2 && c == 1 || r == 1 && c == this.cols - 2)
                    continue;

                g[r][c] = g[this.rows - 1 - r][this.cols - 1 - c] = 1;
                break;
            }
        }

        return check_connectivity(this.rows - 2, 1, 1, this.cols - 2);
    }

    public void createMap() {
        for (int i = 0; i < 1000; i ++ ) {
            if (draw())
                break;
        }
    }

    private String getInput(Player player) {  // 将当前的局面信息，编码成字符串
        Player me, you;
        if (playerA.getId().equals(player.getId())) {
            me = playerA;
            you = playerB;
        } else {
            me = playerB;
            you = playerA;
        }

        return getMapString() + "#" +
                me.getSx() + "#" +
                me.getSy() + "#(" +
                me.getStepsString() + ")#" +
                you.getSx() + "#" +
                you.getSy() + "#(" +
                you.getStepsString() + ")";
    }

    private void sendBotCode(Player player, String enemy) {  // 将代码提交到 botrunningsystem
        if (player.getBotId().equals(-1)) return;  // 亲自出马，不需要执行代码
        MultiValueMap<String, String> data = new LinkedMultiValueMap<>();
        data.add("user_id", player.getId().toString());
        data.add("bot_code", player.getBotCode());
        data.add("enemy_id", enemy);
        data.add("input", getInput(player));
        try {
            WebSocketServer.restTemplate.postForObject(addBotUrl, data, String.class);
        } catch (Exception e) {
            // botrunningsystem 不可达时仅记录日志，该 Bot 本回合超时判负，不影响对局线程
            log.error("提交 Bot 代码失败，userId={}", player.getId(), e);
        }
    }

    private boolean nextStep() {  // 等待两名玩家的下一步操作
        try {
            Thread.sleep(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        sendBotCode(playerA, String.valueOf(playerB.getId()));
        sendBotCode(playerB, String.valueOf(playerA.getId()));

        long deadline = System.currentTimeMillis() + NEXT_STEP_TIMEOUT_MS;
        lock.lock();
        try {
            while (nextStepA == null || nextStepB == null) {
                long rest = deadline - System.currentTimeMillis();
                if (rest <= 0) return false;
                stepReady.await(rest, TimeUnit.MILLISECONDS);
            }
            playerA.getSteps().add(nextStepA);
            playerB.getSteps().add(nextStepB);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            lock.unlock();
        }
    }

    private boolean check_valid(List<Cell> cellsA, List<Cell> cellsB) {
        int n = cellsA.size();
        Cell cell = cellsA.get(n - 1);
        if (g[cell.x][cell.y] == 1) return false;

        for (int i = 0; i < n - 1; i ++ ) {
            if (cellsA.get(i).x == cell.x && cellsA.get(i).y == cell.y)
                return false;
        }

        for (int i = 0; i < n - 1; i ++ ) {
            if (cellsB.get(i).x == cell.x && cellsB.get(i).y == cell.y)
                return false;
        }

        return true;
    }

    private void judge() {  // 判断两名玩家下一步操作是否合法
        List<Cell> cellsA = playerA.getCells();
        List<Cell> cellsB = playerB.getCells();
        boolean validA = check_valid(cellsA, cellsB);
        boolean validB = check_valid(cellsB, cellsA);
        if (!validA || !validB) {
            status = "finished";
            if (!validA && !validB) {
                loser = "all";
            } else if (!validA) {
                loser = "A";
            } else {
                loser = "B";
            }
        }
    }

    private void sendAllMessage(String message) {
        if (WebSocketServer.users.get(playerA.getId()) != null)
            WebSocketServer.users.get(playerA.getId()).sendMessage(message);
        if (WebSocketServer.users.get(playerB.getId()) != null)
            WebSocketServer.users.get(playerB.getId()).sendMessage(message);
    }

    private void sendMove() {  // 向两个Client传递移动信息
        lock.lock();
        try {
            JSONObject resp = new JSONObject();
            resp.put("event", "move");
            resp.put("a_direction", nextStepA);
            resp.put("b_direction", nextStepB);
            sendAllMessage(resp.toJSONString());
            nextStepA = nextStepB = null;
        } finally {
            lock.unlock();
        }
    }

    private String getMapString() {
        StringBuilder res = new StringBuilder();
        for (int i = 0; i < rows; i ++ ) {
            for (int j = 0; j < cols; j ++ ) {
                res.append(g[i][j]);
            }
        }
        return res.toString();
    }

    /** 原子更新用户天梯分与对局次数（rating = rating + delta），避免并发对局互相覆盖 */
    private void updateUserRating(Integer userId, Integer ratingDelta) {
        LambdaUpdateWrapper<User> wrapper = new LambdaUpdateWrapper<User>()
                .eq(User::getId, userId)
                .setSql("rating = rating + " + ratingDelta)
                .setSql("times = times + 1");
        WebSocketServer.userMapper.update(null, wrapper);
    }

    /** 原子更新 Bot 积分与对局次数；botId <= 0 表示亲自出马，无需更新 */
    private void updateBotRating(Integer botId, Integer score) {
        if (botId == null || botId <= 0) return;
        LambdaUpdateWrapper<Bot> wrapper = new LambdaUpdateWrapper<Bot>()
                .eq(Bot::getId, botId)
                .setSql("rating = rating + " + score)
                .setSql("count = count + 1");
        WebSocketServer.botMapper.update(null, wrapper);
    }

    private void saveToDatabase() {
        Integer ratingDeltaA = 0, ratingDeltaB = 0;
        if ("A".equals(loser)) {
            ratingDeltaA = -2;
            ratingDeltaB = 5;
            updateBotRating(playerA.getBotId(), -5);
            updateBotRating(playerB.getBotId(), 5);
        } else if ("B".equals(loser)) {
            ratingDeltaA = 5;
            ratingDeltaB = -2;
            updateBotRating(playerA.getBotId(), 5);
            updateBotRating(playerB.getBotId(), -5);
        }

        updateUserRating(playerA.getId(), ratingDeltaA);
        updateUserRating(playerB.getId(), ratingDeltaB);

        // id 为 null，由数据库自增主键生成，插入后 MyBatis-Plus 会回填 record.getId()
        Record record = new Record(
                null,
                playerA.getId(),
                playerA.getSx(),
                playerA.getSy(),
                playerB.getId(),
                playerB.getSx(),
                playerB.getSy(),
                playerA.getStepsString(),
                playerB.getStepsString(),
                getMapString(),
                loser,
                new Date()
        );

        WebSocketServer.recordMapper.insert(record);

        GameBot gameBot = new GameBot(
                null,
                record.getId(),
                playerA.getBotId(),
                playerA.getId(),
                playerB.getBotId(),
                playerB.getId()
        );

        WebSocketServer.gameBotMapper.insert(gameBot);
    }

    private void sendResult() {  // 向两个Client公布结果
        JSONObject resp = new JSONObject();
        resp.put("event", "result");
        resp.put("loser", loser);
        try {
            saveToDatabase();
        } catch (Exception e) {
            // 落库失败不影响向玩家公布结果
            log.error("对局结果落库失败，A={} B={}", playerA.getId(), playerB.getId(), e);
        }
        sendAllMessage(resp.toJSONString());
    }

    @Override
    public void run() {
        for (int i = 0; i < 1000; i ++ ) {
            if (nextStep()) {  // 是否获取了两条蛇的下一步操作
                judge();
                if (status.equals("playing")) {
                    sendMove();
                } else {
                    sendResult();
                    break;
                }
            } else {
                status = "finished";
                lock.lock();
                try {
                    if (nextStepA == null && nextStepB == null) {
                        loser = "all";
                    } else if (nextStepA == null) {
                        loser = "A";
                    } else {
                        loser = "B";
                    }
                } finally {
                    lock.unlock();
                }
                sendResult();
                break;
            }
        }
    }
}
