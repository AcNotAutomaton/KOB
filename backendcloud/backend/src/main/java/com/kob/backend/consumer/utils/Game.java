package com.kob.backend.consumer.utils;

import com.alibaba.fastjson.JSONObject;
import com.kob.backend.consumer.WebSocketServer;
import com.kob.backend.pojo.Bot;
import com.kob.backend.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.ArrayDeque;
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
    private final String gameId = UUID.randomUUID().toString();
    private volatile int round = 0;
    private boolean acceptingMoves = true;
    private Integer nextStepA = null;
    private Integer nextStepB = null;
    private final ReentrantLock lock = new ReentrantLock();
    /** 两名玩家的下一步都就绪时唤醒裁判线程，替代原来的 50 轮 sleep 轮询 */
    private final Condition stepReady = lock.newCondition();
    private volatile String status = "playing";  // playing -> finished
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

    public String getGameId() { return gameId; }
    public int getRound() { return round; }
    public boolean isPlaying() { return "playing".equals(status); }

    /** 与 move 广播共用锁，确保恢复快照先于后续回合消息发出。 */
    public void sendStateTo(WebSocketServer connection, User opponent) {
        lock.lock();
        try {
            if (!isPlaying() || connection.game != this) return;
            JSONObject board = new JSONObject();
            board.put("game_id", gameId);
            board.put("round", round);
            board.put("a_id", playerA.getId());
            board.put("a_sx", playerA.getSx());
            board.put("a_sy", playerA.getSy());
            board.put("b_id", playerB.getId());
            board.put("b_sx", playerB.getSx());
            board.put("b_sy", playerB.getSy());
            board.put("map", g);
            // 已收到但尚未裁决/广播的方向不能进入恢复快照。
            board.put("a_steps", playerA.getStepsString().substring(0, round));
            board.put("b_steps", playerB.getStepsString().substring(0, round));
            JSONObject resp = new JSONObject();
            resp.put("event", "start-matching");
            resp.put("opponent_username", opponent.getUsername());
            resp.put("opponent_photo", opponent.getPhoto());
            resp.put("game", board);
            connection.sendMessage(resp.toJSONString());
        } finally {
            lock.unlock();
        }
    }

    public boolean receivePlayerMove(Integer userId, String id, Integer moveRound, Integer direction) {
        return receiveMove(userId, null, id, moveRound, direction, false);
    }

    public boolean receiveBotMove(Integer userId, Integer enemy, String id, Integer moveRound, Integer direction) {
        return receiveMove(userId, enemy, id, moveRound, direction, true);
    }

    private boolean receiveMove(Integer userId, Integer enemy, String id, Integer moveRound,
                                Integer direction, boolean bot) {
        lock.lock();
        try {
            if (!isPlaying() || !acceptingMoves || !gameId.equals(id)
                    || moveRound == null || moveRound != round) return false;
            boolean a = playerA.getId().equals(userId);
            Player player = a ? playerA : playerB;
            Player opponent = a ? playerB : playerA;
            if (!player.getId().equals(userId) || (player.getBotId() > 0) != bot
                    || bot && !opponent.getId().equals(enemy)) return false;
            if (a ? nextStepA != null : nextStepB != null) return false;
            // 非法操作记录为哨兵，由裁判判负，绝不用于数组下标。
            int step = validDirection(direction) ? direction : -1;
            if (a) nextStepA = step;
            else nextStepB = step;
            stepReady.signalAll();
            return true;
        } finally {
            lock.unlock();
        }
    }

    private static boolean validDirection(Integer direction) {
        return direction != null && direction >= 0 && direction < 4;
    }

    public int[][] getG() {
        return g;
    }

    private boolean check_connectivity(int sx, int sy, int tx, int ty) {
        boolean[][] visited = new boolean[rows][cols];
        ArrayDeque<Cell> queue = new ArrayDeque<>();
        queue.add(new Cell(sx, sy));
        visited[sx][sy] = true;
        while (!queue.isEmpty()) {
            Cell cell = queue.removeFirst();
            if (cell.x == tx && cell.y == ty) return true;
            for (int d = 0; d < 4; d++) {
                int x = cell.x + dx[d], y = cell.y + dy[d];
                if (x >= 0 && x < rows && y >= 0 && y < cols && g[x][y] == 0 && !visited[x][y]) {
                    visited[x][y] = true;
                    queue.addLast(new Cell(x, y));
                }
            }
        }
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
        data.add("game_id", gameId);
        data.add("round", String.valueOf(round));
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
            acceptingMoves = false;
            if (validDirection(nextStepA) && validDirection(nextStepB)) {
                playerA.getSteps().add(nextStepA);
                playerB.getSteps().add(nextStepB);
            }
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
        if (cell.x < 0 || cell.x >= rows || cell.y < 0 || cell.y >= cols || g[cell.x][cell.y] == 1) return false;

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
        if (!validDirection(nextStepA) || !validDirection(nextStepB)) {
            finishForMissingOrInvalidMoves();
            return;
        }
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
        for (Integer id : new Integer[]{playerA.getId(), playerB.getId()}) {
            WebSocketServer ws = WebSocketServer.users.get(id);
            if (ws != null && ws.game == this) ws.sendMessage(message);
        }
    }

    private void sendMove() {  // 向两个Client传递移动信息
        lock.lock();
        try {
            JSONObject resp = new JSONObject();
            resp.put("event", "move");
            resp.put("a_direction", nextStepA);
            resp.put("b_direction", nextStepB);
            nextStepA = nextStepB = null;
            round++;
            acceptingMoves = true;
            resp.put("game_id", gameId);
            resp.put("round", round);
            sendAllMessage(resp.toJSONString());
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

    private void sendResult() {  // 向两个Client公布结果
        JSONObject resp = new JSONObject();
        resp.put("event", "result");
        resp.put("loser", loser);
        resp.put("game_id", gameId);
        try {
            WebSocketServer.gameResultService.save(playerA, playerB, getMapString(), loser);
        } catch (Exception e) {
            // 落库失败不影响向玩家公布结果
            log.error("对局结果落库失败，A={} B={}", playerA.getId(), playerB.getId(), e);
        }
        sendAllMessage(resp.toJSONString());
    }

    @Override
    public void run() {
        try {
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
                    lock.lock();
                    try {
                        finishForMissingOrInvalidMoves();
                    } finally {
                        lock.unlock();
                    }
                    sendResult();
                    break;
                }
            }
            if (isPlaying()) {
                status = "finished";
                loser = "all";
                sendResult();
            }
        } catch (Exception e) {
            log.error("裁判异常，gameId={}", gameId, e);
            status = "finished";
            loser = "all";
            sendResult();
        } finally {
            WebSocketServer.finishGame(this);
        }
    }

    private void finishForMissingOrInvalidMoves() {
        status = "finished";
        boolean invalidA = !validDirection(nextStepA), invalidB = !validDirection(nextStepB);
        loser = invalidA && invalidB ? "all" : invalidA ? "A" : "B";
    }
}
