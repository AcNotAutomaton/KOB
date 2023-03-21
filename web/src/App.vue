<template>
  <NavBar></NavBar>
  <router-view></router-view>
</template>

<script>
import NavBar from './components/NavBar.vue'
import "bootstrap/dist/css/bootstrap.min.css"
import "bootstrap/dist/js/bootstrap"

export default {
  components: {
    NavBar
  },
  setup() {

  }
}
</script>

<style>
body {
  background-image: url("./assets/images/1.jpeg");
  background-size: cover;
}
</style>
