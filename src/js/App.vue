<template>
  <div id="app" class="d-flex flex-column">
    <editor v-bind:text="message"></editor>
    <editor read-only="nocursor"></editor>
  </div>
</template>

<script>
import Editor from './Editor.vue'

export default {
  data () {
    return {
      message: "Hello World!"
    }
  },
  components: {
    Editor
  }
}
</script>

<style scoped>
#app {
  height: 100%;
}
</style>
