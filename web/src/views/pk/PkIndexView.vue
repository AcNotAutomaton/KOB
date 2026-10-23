<template>
    <PlayGround v-if="$store.state.pk.status === 'playing'" />
    <MatchGround v-if="$store.state.pk.status === 'matching'" />
    <ResultBoard v-if="$store.state.pk.loser != 'none'" />
    <div class="user-color"
        v-if="$store.state.pk.status === 'playing' && parseInt($store.state.user.id) === parseInt($store.state.pk.a_id)">
        左下角
    </div>
    <div class="user-color"
        v-if="$store.state.pk.status === 'playing' && parseInt($store.state.user.id) === parseInt($store.state.pk.b_id)">
        右上角
    </div>
</template>

<script setup>
import PlayGround from '../../components/PlayGround.vue'
import MatchGround from '../../components/MatchGround.vue'
import ResultBoard from '../../components/ResultBoard.vue'
import { onMounted, onUnmounted, nextTick } from 'vue'
import { useStore } from 'vuex'
import { websocketUrl } from '../../utils/requests'

const store = useStore();
const socketUrl = websocketUrl();

store.commit("updateLoser", "none");
store.commit("updateIsRecord", false);
store.commit("updateMatching", false);
store.commit("updateMatchError", "");

let socket = null;
let disposed = false;
let messages = Promise.resolve();

onMounted(() => {
    store.commit("updateOpponent", {
        username: "我的对手",
        photo: "https://cdn.acwing.com/media/article/image/2022/08/09/1_1db2488f17-anonymous.png",
    })
    socket = new WebSocket(socketUrl);

    socket.onopen = () => {
        if (disposed || !store.state.user.is_login) {
            socket.close();
            return;
        }
        console.log("connected!");
        store.commit("updateSocket", socket);
    }

    const handleMessage = async msg => {
        if (disposed || !store.state.user.is_login) return;
        const data = JSON.parse(msg.data);
        if (data.event === "start-matching") {
            store.commit("updateOpponent", {
                username: data.opponent_username,
                photo: data.opponent_photo,
            });
            store.commit("updateGame", data.game);
            store.commit("updateMatching", false);
            store.commit("updateStatus", "playing");
            await nextTick();
        } else if (data.event === "match-error") {
            store.commit("updateMatching", false);
            store.commit("updateMatchError", data.error_message);
        } else if (data.event === "move") {
            if (data.game_id !== store.state.pk.game_id) return;
            const game = store.state.pk.gameObject;
            if (!game) return;
            if (game.enqueue_move(data)) store.commit("updateRound", data.round);
        } else if (data.event === "result") {
            if (data.game_id !== store.state.pk.game_id) return;
            const game = store.state.pk.gameObject;
            if (!game) return;
            game.enqueue_result(data.loser);
        }
    }

    socket.onmessage = msg => {
        // 等棋盘完成挂载后再处理后续消息，连续到达的回合交给棋盘排队播放。
        messages = messages.then(() => handleMessage(msg)).catch(error => console.error("处理对战消息失败", error));
        return messages;
    }

    socket.onclose = () => {
        if (disposed || !store.state.user.is_login) return;
        store.commit("updateSocket", null);
        store.commit("updateMatching", false);
        store.commit("updateLoser", "none");
        store.commit("updateStatus", "matching");
        store.commit("updateMatchError", "连接已断开，请重新进入对战页面");
    }
});

onUnmounted(() => {
    disposed = true;
    if (socket) {
        socket.onmessage = null;
        socket.onclose = null;
        socket.onopen = null;
        socket.close();
    }
    if (store.state.pk.socket === socket) {
        store.commit("updateSocket", null);
        store.commit("updateMatching", false);
        store.commit("updateStatus", "matching");
    }
})
</script>

<style scoped>
div.user-color {
    text-align: center;
    color: white;
    font-size: 30px;
    font-weight: 600;
}
</style>
