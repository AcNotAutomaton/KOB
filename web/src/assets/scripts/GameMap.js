import { AcGameObject } from "./AcGameObject";
import { Wall } from "./Wall";
import { Snake } from './Snake';
import { Cell } from './Cell';

export class GameMap extends AcGameObject {
    constructor(ctx, parent, store) {
        super();
        
        this.ctx = ctx;
        this.parent = parent;
        this.store = store;
        this.L = 0;

        this.rows = 13;
        this.cols = 14;

        this.inner_walls_count = 20;
        this.walls = [];
        this.interval_id = null;
        this.keydown_handler = null;
        this.moves = [];
        this.received_round = store.state.pk.round || 0;
        this.pending_result = null;
        this.finished = false;

        this.snakes = [
            new Snake({ id: 0, color: "#4876EC", r: this.rows - 2, c: 1, steps: store.state.pk.a_steps }, this),
            new Snake({ id: 1, color: "#F94848", r: 1, c: this.cols - 2, steps: store.state.pk.b_steps }, this),
        ];
    }

    create_walls() {
        const g = this.store.state.pk.gamemap;

        for (let r = 0; r < this.rows; r++) {
            for (let c = 0; c < this.cols; c++) {
                if (g[r][c]) {
                    this.walls.push(new Wall(r, c, this));
                }
            }
        }
    }

    add_listening_events() {
        if (this.store.state.record.is_record) {
            let k = 0;
            const a_steps = this.store.state.record.a_steps;
            const b_steps = this.store.state.record.b_steps;
            const loser = this.store.state.record.record_loser;
            const [snake0, snake1] = this.snakes;
            const length = Math.min(a_steps.length, b_steps.length);
            const finish = () => {
                if (loser === "all" || loser === "A") snake0.status = "die";
                if (loser === "all" || loser === "B") snake1.status = "die";
                clearInterval(this.interval_id);
                this.interval_id = null;
                this.store.commit("updateProgress", 100);
            };
            this.interval_id = setInterval(() => {
                // 后台标签页或慢帧下，必须等已下发的动画完成，不能覆盖尚未播放的方向。
                if (this.snakes.some(snake => snake.status === "move" || snake.direction !== -1)) return;
                if (k >= length) {
                    finish();
                    return;
                }
                const directions = [Number(a_steps[k]), Number(b_steps[k])];
                if (directions.some(d => !Number.isInteger(d) || d < 0 || d > 3)) {
                    finish();
                    return;
                }
                const targets = this.snakes.map((snake, i) => new Cell(
                    snake.cells[0].r + snake.dr[directions[i]], snake.cells[0].c + snake.dc[directions[i]]));
                // 撞墙/身体的最后一步不播放；超时或非法输入结束时，所有已保存的合法步都应播放。
                if (targets.some(cell => !this.check_valid(cell, k + 1))
                    || targets[0].r === targets[1].r && targets[0].c === targets[1].c) {
                    finish();
                    return;
                }
                snake0.set_direction(directions[0]);
                snake1.set_direction(directions[1]);
                k++;
                this.store.commit("updateProgress", k / length * 100);
            }, 300);
        } else {
            this.ctx.canvas.focus();

            this.keydown_handler = e => {
                let d = -1;
                if (e.key === 'w') d = 0;
                else if (e.key === 'd') d = 1;
                else if (e.key === 's') d = 2;
                else if (e.key === 'a') d = 3;

                const pk = this.store.state.pk;
                if (d >= 0 && !this.finished && this.pending_result === null
                    && pk.loser === "none" && pk.socket?.readyState === 1) {
                    pk.socket.send(JSON.stringify({
                        event: "move",
                        direction: d,
                        game_id: pk.game_id,
                        round: pk.round,
                    }));
                }
            };
            this.ctx.canvas.addEventListener("keydown", this.keydown_handler);
        }
    }

    start() {
        this.create_walls();

        this.add_listening_events();
    }

    enqueue_move(move) {
        if (this.destroyed || this.finished || this.pending_result !== null
            || move.game_id !== this.store.state.pk.game_id
            || move.round !== this.received_round + 1
            || [move.a_direction, move.b_direction].some(d => !Number.isInteger(d) || d < 0 || d > 3)) return false;
        this.moves.push(move);
        this.received_round = move.round;
        return true;
    }

    enqueue_result(loser) {
        if (!this.destroyed && !this.finished && ["A", "B", "all"].includes(loser)) {
            this.pending_result = loser;
        }
    }

    update_live() {
        if (this.finished || this.snakes.some(snake => snake.status !== "idle" || snake.direction !== -1)) return;
        if (this.moves.length) {
            // 网络积压时加快播放，仍逐回合移动，避免跳过转弯或持续落后。
            const speed = 5 * Math.min(4, this.moves.length);
            const move = this.moves.shift();
            this.snakes[0].set_direction(move.a_direction);
            this.snakes[1].set_direction(move.b_direction);
            for (const snake of this.snakes) snake.speed = speed;
        } else if (this.pending_result !== null) {
            this.finished = true;
            if (this.pending_result === "all" || this.pending_result === "A") this.snakes[0].status = "die";
            if (this.pending_result === "all" || this.pending_result === "B") this.snakes[1].status = "die";
            this.store.commit("updateLoser", this.pending_result);
        }
    }

    on_destroy() {
        this.moves.length = 0;
        this.finished = true;
        if (this.interval_id !== null) clearInterval(this.interval_id);
        this.interval_id = null;
        if (this.keydown_handler) this.ctx.canvas.removeEventListener("keydown", this.keydown_handler);
        this.keydown_handler = null;
        for (const object of [...this.snakes, ...this.walls]) object.destroy();
    }

    update_size() {
        this.L = parseInt(Math.min(this.parent.clientWidth / this.cols, this.parent.clientHeight / this.rows));
        this.ctx.canvas.width = this.L * this.cols;
        this.ctx.canvas.height = this.L * this.rows;
    }

    check_ready() {  // 判断两条蛇是否都准备好下一回合了
        for (const snake of this.snakes) {
            if (snake.status !== "idle") return false;
            if (snake.direction === -1) return false;
        }
        return true;
    }

    next_step() {  // 让两条蛇进入下一回合
        for (const snake of this.snakes) {
            snake.next_step();
        }
    }

    check_valid(cell, step) {  // 检测目标位置是否合法：没有撞到两条蛇的身体和障碍物
        if (cell.r < 0 || cell.r >= this.rows || cell.c < 0 || cell.c >= this.cols) return false;
        for (const wall of this.walls) {
            if (wall.r === cell.r && wall.c === cell.c)
                return false;
        }

        for (const snake of this.snakes) {
            let k = snake.cells.length;
            if (!snake.check_tail_increasing(step)) {  // 当蛇尾会前进的时候，蛇尾不要判断
                k--;
            }
            for (let i = 0; i < k; i++) {
                if (snake.cells[i].r === cell.r && snake.cells[i].c === cell.c)
                    return false;
            }
        }

        return true;
    }

    update() {
        this.update_size();
        if (!this.store.state.record.is_record) this.update_live();
        if (this.check_ready()) {
            this.next_step();
        }
        this.render();
    }

    render() {
        const color_even = "#AAD751", color_odd = "#A2D149";
        for (let r = 0; r < this.rows; r++) {
            for (let c = 0; c < this.cols; c++) {
                if ((r + c) % 2 == 0) {
                    this.ctx.fillStyle = color_even;
                } else {
                    this.ctx.fillStyle = color_odd;
                }
                this.ctx.fillRect(c * this.L, r * this.L, this.L, this.L);
            }
        }
    }
}
