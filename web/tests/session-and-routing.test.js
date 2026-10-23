const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');
const { markRaw, ref, computed } = require('vue');
const { createStore } = require('vuex');
const { createRouter, createMemoryHistory } = require('vue-router');

const read = file => fs.readFileSync(path.join(__dirname, '../src', file), 'utf8');
const turn = () => new Promise(resolve => setImmediate(resolve));

function environment(token = null) {
    const storage = new Map(token ? [['jwt_token', token]] : []);
    const requests = [];
    let store, router;
    const context = vm.createContext({
        console, markRaw, ref, computed,
        ajax: options => requests.push(options),
        localStorage: { getItem: key => storage.get(key) || null,
            setItem: (key, value) => storage.set(key, value), removeItem: key => storage.delete(key) },
        useStore: () => store, useRouter: () => router, useRoute: () => router.currentRoute.value,
        createRouter, createWebHistory: createMemoryHistory,
    });
    for (const name of ['user', 'pk']) {
        vm.runInContext(read(`store/${name}.js`).replace(/^import .*\r?\n/gm, '')
            .replace('export default', `globalThis.${name}Module =`), context);
    }
    store = createStore({ modules: { user: context.userModule, pk: context.pkModule } });
    context.store = store;
    vm.runInContext(read('router/index.js').replace(/^import .*\r?\n/gm, '')
        .replace(/const (\w+) = \(\) => import\('[^']+'\)/g, 'const $1 = { render() {} }')
        .replace('export default router', 'globalThis.router = router'), context);
    router = context.router;
    return {
        store, router, requests, storage,
        view(file, bindings) {
            const script = read(file).split('<script setup>')[1].split('</script>')[0]
                .replace(/^import .*\r?\n/gm, '');
            vm.runInContext(`(() => {${script}\nglobalThis.view = {${bindings}};})()`, context);
            return context.view;
        },
    };
}

const userInfo = { error_message: 'success', id: '1', username: 'player', photo: '' };

test('refresh restores the session before entering the original protected URL', async () => {
    const env = environment('saved-token');
    const navigation = env.router.push('/pk/?mode=pvp#board');
    await turn();
    assert.equal(env.requests.length, 1);
    assert.equal(env.requests[0].url, '/api/user/account/info/');
    assert.equal(env.store.state.user.is_login, false);
    env.requests[0].success(userInfo);
    await navigation;
    assert.equal(env.store.state.user.is_login, true);
    assert.equal(env.store.state.user.pulling_info, false);
    assert.equal(env.router.currentRoute.value.fullPath, '/pk/?mode=pvp#board');
});

test('parallel session restoration shares one user information request', async () => {
    const env = environment('saved-token');
    const first = env.store.dispatch('restoreLogin');
    const second = env.store.dispatch('restoreLogin');
    assert.equal(env.requests.length, 1);
    env.requests[0].success(userInfo);
    assert.deepEqual(await Promise.all([first, second]), [true, true]);
});

test('manual login returns to the requested protected page with query and hash', async () => {
    const env = environment();
    await env.router.push('/pk/?mode=pvp#board');
    assert.equal(env.router.currentRoute.value.name, 'user_account_login');
    assert.equal(env.router.currentRoute.value.query.redirect, '/pk/?mode=pvp#board');
    const view = env.view('views/user/account/UserAccountLoginView.vue', 'login, username, password');
    view.username.value = 'player'; view.password.value = 'password';
    view.login();
    env.requests[0].success({ error_message: 'success', token: 'new-token' });
    env.requests[1].success(userInfo);
    await turn();
    assert.equal(env.router.currentRoute.value.fullPath, '/pk/?mode=pvp#board');
    assert.equal(env.storage.get('jwt_token'), 'new-token');
});

test('an invalid saved token returns to login without repeated restoration', async () => {
    const env = environment('expired-token');
    const navigation = env.router.push('/pk/');
    await turn();
    env.requests[0].error({});
    await navigation;
    assert.equal(env.router.currentRoute.value.name, 'user_account_login');
    assert.equal(env.storage.has('jwt_token'), false);
    assert.equal(env.store.state.user.is_login, false);
    await env.router.push('/ranklist/');
    assert.equal(env.requests.length, 1);
});

test('logout closes the socket, clears matching, and replaces the protected page', async () => {
    const env = environment('saved-token');
    env.store.commit('updateUser', { ...userInfo, is_login: true });
    env.store.commit('updateToken', 'saved-token');
    env.store.commit('updatePullingInfo', false);
    await env.router.push('/pk/');
    const socket = { readyState: 1, onopen() {}, onmessage() {}, onclose() {},
        close() { this.readyState = 3; this.closes = (this.closes || 0) + 1; } };
    env.store.commit('updateSocket', socket);
    env.store.commit('updateMatching', true);
    env.store.commit('updateStatus', 'playing');
    const nav = env.view('components/NavBar.vue', 'logout');
    await nav.logout();
    assert.equal(socket.readyState, 3);
    assert.equal(socket.closes, 1);
    assert.equal(socket.onmessage, null);
    assert.equal(socket.onopen, null);
    assert.equal(socket.onclose, null);
    assert.equal(env.store.state.pk.socket, null);
    assert.equal(env.store.state.pk.matching, false);
    assert.equal(env.store.state.pk.status, 'matching');
    assert.equal(env.store.state.user.is_login, false);
    assert.equal(env.storage.has('jwt_token'), false);
    assert.equal(env.router.currentRoute.value.name, 'user_account_login');
});

test('a user information response arriving after logout cannot restore the old account', async () => {
    const env = environment('saved-token');
    const pending = env.store.dispatch('restoreLogin');
    await env.store.dispatch('logout');
    env.requests[0].success(userInfo);
    assert.equal(await pending, false);
    assert.equal(env.store.state.user.is_login, false);
    assert.equal(env.store.state.user.token, '');
    assert.equal(env.storage.has('jwt_token'), false);
});

test('login ignores an external return URL', async () => {
    const env = environment();
    await env.router.push({ name: 'user_account_login', query: { redirect: '//example.com/pk/' } });
    const view = env.view('views/user/account/UserAccountLoginView.vue', 'login');
    view.login();
    env.requests[0].success({ error_message: 'success', token: 'new-token' });
    env.requests[1].success(userInfo);
    await turn();
    assert.equal(env.router.currentRoute.value.name, 'home_index');
});
