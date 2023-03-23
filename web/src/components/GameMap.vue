<template>
    <div ref="parent" class="gamemap">
        <canvas ref="canvas" tabindex="0"></canvas>
        <div v-if="$store.state.record.is_record" class="btn-group-vertical group" role="group" aria-label="Basic example">
            <button type="button" @click="again" class="btn btn-primary">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-refresh-ccw"><polyline points="1 4 1 10 7 10"></polyline><polyline points="23 20 23 14 17 14"></polyline><path d="M20.49 9A9 9 0 0 0 5.64 5.64L1 10m22 4l-4.64 4.36A9 9 0 0 1 3.51 15"></path></svg>
            </button>
            <button type="button" @click="goback" style="margin-top:2px;" class="btn btn-primary">
                <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" class="feather feather-corner-down-left"><polyline points="9 10 4 15 9 20"></polyline><path d="M20 4v7a4 4 0 0 1-4 4H4"></path></svg>
            </button>
          </div>
    </div>
    <div v-if="$store.state.record.is_record" class="progress w-50" role="progressbar" aria-label="Animated striped example" aria-valuenow="10" aria-valuemin="0" aria-valuemax="100">
        <div class="progress-bar progress-bar-striped progress-bar-animated" :style="{  width: $store.state.record.progress_bar + '%' }" ></div>
    </div>

</template>

<script>
import { GameMap } from "../assets/scripts/GameMap";
import { ref, onMounted } from 'vue'
import { useStore } from "vuex";
import router from "@/router";

export default {
    setup() {
        const store = useStore();
        let parent = ref(null);
        let canvas = ref(null);

        const again = () => {
            store.commit("updateProgress",0)
            store.commit(
                "updateGameObject",
                new GameMap(canvas.value.getContext('2d'), parent.value, store)
            ); 
        }

        const goback = ()=>{
            router.push("/record/")
        }

        onMounted(() => {
            store.commit(
                "updateGameObject",
                new GameMap(canvas.value.getContext('2d'), parent.value, store)
            );
        });

        return {
            parent,
            canvas,
            again,goback
        }
    }
}
</script>

<style scoped>
div.gamemap {
    width: 100%;
    height: 100%;
    display: flex;
    position: relative;
    justify-content: center;
    align-items: center;
}
.progress{
    position: relative;
    transform: translate(50%);
    top: 0;
}
div.gamemap .group{
    position: relative;
    top: 0;
    left: 0;
}
</style>