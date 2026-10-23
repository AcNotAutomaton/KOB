const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { markRaw } = require('vue');
const { createStore } = require('vuex');

function environment(record = true, realStore = false) {
    let mounts = [], unmounts = [], nextId = 0, animation;
    const sockets = [];
    const timers = new Map(), listeners = new Map(), sent = [];
    let store = {
        state: {
            user: { id: '1', is_login: true },
            pk: { gamemap: Array.from({ length: 13 }, () => Array(14).fill(0)), loser: 'none',
                game_id: 'game-test', round: 7, socket: { readyState: 1, send: data => sent.push(JSON.parse(data)) } },
            record: { is_record: record, a_steps: '012301', b_steps: '321032', record_loser: 'A' },
        },
        commit(name, value) {
            if (name === 'updateGameObject') this.state.pk.gameObject = value;
            if (name === 'updateProgress') this.state.record.progress_bar = value;
            if (name === 'updateLoser') this.state.pk.loser = value;
        },
    };
    const canvas = {
        focus() {},
        addEventListener(name, handler) {
            if (!listeners.has(name)) listeners.set(name, new Set());
            listeners.get(name).add(handler);
        },
        removeEventListener(name, handler) { listeners.get(name)?.delete(handler); },
    };
    const drawing = { canvas, fillRect() {}, beginPath() {}, arc() {}, fill() {} };
    canvas.getContext = () => drawing;
    const context = vm.createContext({
        console: { log() {} },
        websocketUrl: () => 'ws://test/', nextTick: () => Promise.resolve(),
        WebSocket: class {
            constructor() { this.readyState = 1; sockets.push(this); }
            send(data) { sent.push(JSON.parse(data)); }
            close() { this.readyState = 3; }
        },
        requestAnimationFrame(callback) { animation = callback; },
        setInterval(callback) { timers.set(++nextId, callback); return nextId; },
        clearInterval(id) { timers.delete(id); },
        useStore: () => store, ref: value => ({ value }),
        onMounted: callback => mounts.push(callback), onUnmounted: callback => unmounts.push(callback),
        router: { go() {} },
        markRaw,
    });
    for (const file of ['AcGameObject', 'Cell', 'Snake', 'Wall', 'GameMap']) {
        let source = fs.readFileSync(path.join(__dirname, '../src/assets/scripts', file + '.js'), 'utf8')
            .replace(/^import .*\r?\n/gm, '').replace('export class ', 'class ');
        if (file === 'AcGameObject') source += '\nglobalThis.objects = AC_GAME_OBJECTS;';
        vm.runInContext(source, context, { filename: file + '.js' });
    }
    const setup = fs.readFileSync(path.join(__dirname, '../src/components/GameMap.vue'), 'utf8')
        .split('<script setup>')[1].split('</script>')[0].replace(/^import .*\r?\n/gm, '');
    if (realStore) {
        const source = fs.readFileSync(path.join(__dirname, '../src/store/pk.js'), 'utf8')
            .replace(/^import .*\r?\n/gm, '').replace('export default', 'globalThis.pkModule =');
        vm.runInContext(source, context);
        Object.assign(context.pkModule.state, store.state.pk);
        const recordState = store.state.record;
        store = createStore({ modules: {
            user: { state: store.state.user },
            pk: context.pkModule,
            record: { state: recordState, mutations: {
                updateProgress(state, value) { state.progress_bar = value; },
                updateIsRecord(state, value) { state.is_record = value; },
            } },
        } });
    }
    return {
        store, timers, listeners, sent, context,
        frame: time => animation(time),
        tick: () => [...timers.values()].forEach(callback => callback()),
        mountPk(onNextTick = () => {}) {
            mounts = []; unmounts = [];
            context.nextTick = async () => onNextTick();
            const source = fs.readFileSync(path.join(__dirname, '../src/views/pk/PkIndexView.vue'), 'utf8')
                .split('<script setup>')[1].split('</script>')[0].replace(/^import .*\r?\n/gm, '');
            vm.runInContext('(() => {' + source + '})()', context);
            mounts.forEach(callback => callback());
            const cleanup = [...unmounts], socket = sockets[sockets.length - 1];
            socket.onopen();
            return {
                socket,
                emit: data => socket.onmessage({ data: JSON.stringify(data) }),
                unmount: () => cleanup.forEach(callback => callback()),
            };
        },
        mount() {
            mounts = []; unmounts = [];
            vm.runInContext('(() => {' + setup + '\nglobalThis.component = {again, parent, canvas}; })()', context);
            context.component.parent.value = { clientWidth: 140, clientHeight: 130 };
            context.component.canvas.value = canvas;
            mounts.forEach(callback => callback());
            const cleanup = [...unmounts];
            return { again: context.component.again, unmount: () => cleanup.forEach(callback => callback()) };
        },
    };
}

test('destroying one object preserves unrelated objects and is idempotent', () => {
    const env = environment();
    const first = vm.runInContext('new AcGameObject()', env.context);
    const middle = vm.runInContext('new AcGameObject()', env.context);
    const last = vm.runInContext('new AcGameObject()', env.context);
    middle.destroy();
    middle.destroy();
    assert.equal(env.context.objects.length, 2);
    assert.equal(env.context.objects[0], first);
    assert.equal(env.context.objects[1], last);
});

test('replaying repeatedly keeps exactly one map, two snakes and one timer in a real Vuex store', () => {
    const env = environment(true, true);
    const component = env.mount();
    env.frame(0);
    const original = env.store.state.pk.gameObject;
    for (let i = 1; i <= 5; i++) {
        component.again();
        env.frame(i * 16);
        assert.equal(env.context.objects.length, 3);
        assert.equal(env.timers.size, 1);
    }
    assert.equal(original.destroyed, true);
    assert.ok(original.snakes.every(snake => snake.destroyed));
    component.unmount();
    assert.equal(env.context.objects.length, 0);
    assert.equal(env.timers.size, 0);
    assert.equal(env.store.state.pk.gameObject, null);
    env.frame(100);
    assert.equal(env.context.objects.length, 0);
});

test('unmount removes keyboard events and remount sends one move with game identity', () => {
    const env = environment(false);
    const first = env.mount();
    env.frame(0);
    assert.equal(env.listeners.get('keydown').size, 1);
    first.unmount();
    assert.equal(env.listeners.get('keydown').size, 0);
    assert.equal(env.context.objects.length, 0);
    const second = env.mount();
    env.frame(16);
    env.listeners.get('keydown').forEach(handler => handler({ key: 'w' }));
    assert.equal(env.sent.length, 1);
    assert.deepEqual(env.sent[0], { event: 'move', direction: 0, game_id: 'game-test', round: 7 });
    second.unmount();
    assert.equal(env.listeners.get('keydown').size, 0);
});

test('destroying an old component does not clear a newer component game', () => {
    const env = environment();
    const old = env.mount();
    const current = env.mount();
    const currentGame = env.store.state.pk.gameObject;
    old.unmount();
    assert.equal(env.store.state.pk.gameObject, currentGame);
    assert.equal(currentGame.destroyed, false);
    assert.equal(env.context.objects.length, 3);
    current.unmount();
    assert.equal(env.context.objects.length, 0);
});

for (const fps of [24, 15, 7]) {
    test(`a move reaches its exact target at ${fps} FPS`, () => {
        const env = environment(false);
        const component = env.mount();
        env.frame(0);
        const game = env.store.state.pk.gameObject;
        game.snakes[0].set_direction(0);
        game.snakes[1].set_direction(2);
        for (let frame = 1; frame <= fps; frame++) env.frame(frame * 1000 / fps);
        assert.equal(game.snakes[0].status, 'idle');
        assert.equal(game.snakes[0].cells[0].r, 10);
        assert.equal(game.snakes[0].cells[0].y, 10.5);
        assert.equal(game.snakes[1].status, 'idle');
        assert.equal(game.snakes[1].cells[0].r, 2);
        component.unmount();
    });
}

test('a long animation frame never overshoots the target', () => {
    const env = environment(false);
    const component = env.mount();
    env.frame(0);
    const game = env.store.state.pk.gameObject;
    game.snakes[0].set_direction(0);
    game.snakes[1].set_direction(2);
    env.frame(225);
    assert.equal(game.snakes[0].status, 'idle');
    assert.equal(game.snakes[0].cells[0].y, 10.5);
    assert.equal(game.snakes[0].cells.length, 2);
    component.unmount();
});

test('resume restores body positions and the round without replaying old animation', () => {
    const env = environment(false, true);
    env.store.state.pk.a_steps = '01';
    env.store.state.pk.b_steps = '23';
    const component = env.mount();
    env.frame(0);
    const [a, b] = env.store.state.pk.gameObject.snakes;
    assert.deepEqual(Array.from(a.cells, c => [c.r, c.c]), [[10, 2], [10, 1], [11, 1]]);
    assert.deepEqual(Array.from(b.cells, c => [c.r, c.c]), [[2, 11], [2, 12], [1, 12]]);
    assert.equal(a.step, 2);
    assert.equal(a.status, 'idle');
    assert.equal(a.direction, -1);
    component.unmount();
});

test('resume preserves tail growth rules on the next round', () => {
    const env = environment(false);
    env.store.state.pk.a_steps = '0'.repeat(10);
    env.store.state.pk.b_steps = '2'.repeat(10);
    const component = env.mount();
    env.frame(0);
    const [a, b] = env.store.state.pk.gameObject.snakes;
    assert.equal(a.cells.length, 11);
    a.set_direction(1);
    b.set_direction(3);
    env.frame(200);
    assert.equal(a.step, 11);
    assert.equal(a.cells.length, 11);
    assert.equal(a.cells[0].c, 2);
    assert.equal(a.cells[a.cells.length - 1].r, 10);
    component.unmount();
});

test('timeout or invalid-input replay plays its last valid saved move before ending', () => {
    const env = environment();
    env.store.state.record.a_steps = '0';
    env.store.state.record.b_steps = '2';
    const component = env.mount();
    env.frame(0);
    const [a, b] = env.store.state.pk.gameObject.snakes;
    env.tick();
    assert.equal(a.direction, 0);
    assert.equal(a.status, 'idle');
    for (let time = 16; time <= 320; time += 16) env.frame(time);
    env.tick();
    assert.equal(a.cells[0].r, 10);
    assert.equal(b.cells[0].r, 2);
    assert.equal(a.status, 'die');
    assert.equal(env.timers.size, 0);
    assert.equal(env.store.state.record.progress_bar, 100);
    component.unmount();
});

test('collision replay stops before moving into a wall', () => {
    const env = environment();
    env.store.state.pk.gamemap[12][1] = 1;
    env.store.state.record.a_steps = '2';
    env.store.state.record.b_steps = '2';
    const component = env.mount();
    env.frame(0);
    env.tick();
    const a = env.store.state.pk.gameObject.snakes[0];
    assert.equal(a.cells[0].r, 11);
    assert.equal(a.step, 0);
    assert.equal(a.status, 'die');
    assert.equal(env.timers.size, 0);
    component.unmount();
});

test('replay waits for queued or slow animation instead of overwriting directions', () => {
    const env = environment();
    env.store.state.record.a_steps = '01';
    env.store.state.record.b_steps = '23';
    const component = env.mount();
    env.frame(0);
    const [a, b] = env.store.state.pk.gameObject.snakes;
    env.tick();
    env.tick();
    assert.equal(a.direction, 0);
    for (let time = 16; time <= 320; time += 16) env.frame(time);
    env.tick();
    assert.equal(a.direction, 1);
    env.frame(336);
    env.tick();
    assert.equal(env.timers.size, 1);
    for (let time = 352; time <= 640; time += 16) env.frame(time);
    env.tick();
    assert.equal(a.cells[0].r, 10);
    assert.equal(a.cells[0].c, 2);
    assert.equal(b.cells[0].r, 2);
    assert.equal(b.cells[0].c, 11);
    assert.equal(a.step, 2);
    assert.equal(env.timers.size, 0);
    component.unmount();
});

test('zero-move replay ends without reading an invalid direction', () => {
    const env = environment();
    env.store.state.record.a_steps = '';
    env.store.state.record.b_steps = '';
    const component = env.mount();
    env.frame(0);
    env.tick();
    assert.equal(env.store.state.pk.gameObject.snakes[0].status, 'die');
    assert.equal(env.timers.size, 0);
    component.unmount();
});

const liveStart = (round = 0, a_steps = '', b_steps = '') => ({
    event: 'start-matching', opponent_username: 'opponent', opponent_photo: '',
    game: { game_id: 'live-game', round, a_steps, b_steps,
        map: Array.from({ length: 13 }, () => Array(14).fill(0)), a_id: 1, b_id: 2 },
});
const liveMove = (round, a, b) => ({
    event: 'move', game_id: 'live-game', round, a_direction: a, b_direction: b,
});

test('consecutive socket events wait for mounting and play every round before the result', async () => {
    const env = environment(false, true);
    let component;
    const pk = env.mountPk(() => { component = env.mount(); });
    await Promise.all([
        pk.emit(liveStart()), pk.emit(liveMove(1, 0, 2)), pk.emit(liveMove(2, 1, 3)),
        pk.emit(liveMove(2, 1, 3)), // 重复回合不能重复播放。
        pk.emit({ ...liveMove(3, 0, 2), game_id: 'old-game' }),
        pk.emit({ event: 'result', game_id: 'live-game', loser: 'B' }),
    ]);
    const game = env.store.state.pk.gameObject;
    assert.equal(env.store.state.pk.round, 2);
    assert.equal(game.moves.length, 2);
    assert.equal(game.snakes[0].step, 0);
    assert.equal(env.store.state.pk.loser, 'none');
    env.frame(0);
    env.listeners.get('keydown').forEach(handler => handler({ key: 'w' }));
    assert.equal(env.sent.length, 0); // 收到结果后不能继续发送操作。
    for (let time = 16; time <= 800; time += 16) env.frame(time);
    const [a, b] = game.snakes;
    assert.deepEqual([a.cells[0].r, a.cells[0].c], [10, 2]);
    assert.deepEqual([b.cells[0].r, b.cells[0].c], [2, 11]);
    assert.equal(a.step, 2);
    assert.equal(b.step, 2);
    assert.equal(b.status, 'die');
    assert.equal(env.store.state.pk.loser, 'B');
    component.unmount(); pk.unmount();
    assert.equal(game.moves.length, 0);
});

test('moves received during a slow live animation preserve intermediate turns', async () => {
    const env = environment(false, true);
    let component;
    const pk = env.mountPk(() => { component = env.mount(); });
    await pk.emit(liveStart());
    await pk.emit(liveMove(1, 0, 2));
    env.frame(0); env.frame(16);
    assert.equal(env.store.state.pk.gameObject.snakes[0].status, 'move');
    await pk.emit(liveMove(2, 1, 3));
    await pk.emit(liveMove(3, 0, 2));
    for (let frame = 1; frame <= 8; frame++) env.frame(16 + frame * 1000 / 7);
    const [a, b] = env.store.state.pk.gameObject.snakes;
    assert.deepEqual(Array.from(a.cells, c => [c.r, c.c]), [[9, 2], [10, 2], [10, 1], [11, 1]]);
    assert.deepEqual(Array.from(b.cells, c => [c.r, c.c]), [[3, 11], [2, 11], [2, 12], [1, 12]]);
    assert.equal(a.step, 3);
    assert.equal(a.status, 'idle');
    component.unmount(); pk.unmount();
});

test('a resumed live game ignores old rounds and starts from its snapshot', async () => {
    const env = environment(false, true);
    let component;
    const pk = env.mountPk(() => { component = env.mount(); });
    await pk.emit(liveStart(2, '01', '23'));
    await pk.emit(liveMove(2, 1, 3));
    await pk.emit(liveMove(3, 0, 2));
    for (let time = 0; time <= 400; time += 16) env.frame(time);
    const game = env.store.state.pk.gameObject;
    assert.equal(game.snakes[0].step, 3);
    assert.deepEqual([game.snakes[0].cells[0].r, game.snakes[0].cells[0].c], [9, 2]);
    const pending = pk.emit(liveMove(4, 1, 3));
    env.store.state.user.is_login = false;
    await pending;
    assert.equal(game.moves.length, 0);
    component.unmount(); pk.unmount();
});
