<template>
    <div class="matchground">
        <div class="row">
            <div class="col-4">
                <div class="user-photo">
                    <img :src="$store.state.user.photo" alt="">
                </div>
                <div class="user-username">
                    {{ $store.state.user.username }}
                </div>
            </div>
            <div class="col-4">
                <div class="user-select-bot">
                    <select v-model="select_bot" :disabled="$store.state.pk.matching" class="form-select" aria-label="选择对战方式">
                        <option value="pvp:-1">亲自出马</option>
                        <option v-for="bot in bots" :key="'pvp:' + bot.id" :value="'pvp:' + bot.id">
                            {{ bot.title }}
                        </option>
                        <option disabled>下面是bot和AI玩</option>
                        <option value="ai:-1">本人 VS AI</option>
                        <option v-for="bot in bots" :key="'ai:' + bot.id" :value="'ai:' + bot.id">
                            {{ bot.title }} VS AI
                        </option>
                    </select>
                </div>
            </div>
            <div class="col-4">
                <div class="user-photo">
                    <img :src="$store.state.pk.opponent_photo" alt="">
                </div>
                <div class="user-username">
                    {{ $store.state.pk.opponent_username }}
                </div>
            </div>
            <div class="col-12" style="text-align: center; padding-top: 15vh;">
                <button @click="click_match_btn" :disabled="$store.state.pk.socket?.readyState !== 1" type="button" class="btn btn-warning btn-lg">{{ $store.state.pk.matching ? '取消' : '开始匹配' }}</button>
                <p v-if="$store.state.pk.match_error" class="text-warning mt-3">{{ $store.state.pk.match_error }}</p>
            </div>
        </div>
    </div>
</template>

<script setup>
import { ref } from 'vue'
import { useStore } from 'vuex';
import { ajax } from '../utils/requests'

const store = useStore();
let bots = ref([]);
let select_bot = ref("pvp:-1");

const click_match_btn = () => {
    const socket = store.state.pk.socket;
    if (socket?.readyState !== 1) return;
    store.commit("updateMatchError", "");
    if (!store.state.pk.matching) {
        const [mode, botId] = select_bot.value.split(":");
        store.commit("updateMatching", true);
        socket.send(JSON.stringify({
            event: "start-matching",
            bot_id: Number(botId),
            mode,
        }));
    } else {
        store.commit("updateMatching", false);
        socket.send(JSON.stringify({
            event: "stop-matching",
        }));
    }
}

const refresh_bots = () => {
    ajax({
        url: "/api/user/bot/getlist/",
        type: "get",
        success(resp) {
            bots.value = resp;
        }
    })
}

refresh_bots();  // 从云端动态获取bots
</script>

<style scoped>
div.matchground {
    width: 60vw;
    height: 70vh;
    margin: 40px auto;
    background-color: rgba(50, 50, 50, 0.5);
}

div.user-photo {
    text-align: center;
    padding-top: 10vh;
}

div.user-photo>img {
    border-radius: 50%;
    width: 20vh;
}

div.user-username {
    text-align: center;
    font-size: 24px;
    font-weight: 600;
    color: white;
    padding-top: 2vh;
}

div.user-select-bot {
    padding-top: 20vh;
}

div.user-select-bot>select {
    width: 60%;
    margin: 0 auto;
}
</style>
