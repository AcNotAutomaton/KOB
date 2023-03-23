<template>
    <div class="playground">
        <GameMap />
        <!-- <div v-if="$store.state.record.is_record" class="progress w-75" role="progressbar" aria-label="Animated striped example" aria-valuenow="10" aria-valuemin="0" aria-valuemax="100">
            <div class="progress-bar progress-bar-striped progress-bar-animated" :style="{  width: $store.state.record.progress_bar + '%' }" ></div>
        </div> -->
    </div>
</template>

<script>
import GameMap from './GameMap.vue'

export default {
    components: {
        GameMap,
    }
}
</script>

<style scoped>
div.playground {
    width: 60vw;
    height: 70vh;
    margin: 40px auto;
}
</style>
