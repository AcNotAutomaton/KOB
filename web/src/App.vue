<template>
  <NavBar></NavBar>
  <router-view></router-view>
</template>

<script setup>
import NavBar from './components/NavBar.vue'
import "bootstrap/dist/css/bootstrap.min.css"
import "bootstrap/dist/js/bootstrap"
import { useStore } from 'vuex'

// 使用 Vuex store 并触发 showMarkdown action
const store = useStore()
store.dispatch("showMarkdown")
</script>

<style>
body {
  background-image: url("@/assets/images/1.jpeg");
  background-size: cover;
}
</style>
