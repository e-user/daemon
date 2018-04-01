<template>
  <div id="app">
    <editor v-bind:text="message"></editor>
  </div>
</template>

<script>
  import editor from './editor.vue'

  export default {
    data () {
      return {
        message: "Hello World!"
      }
    },
    components: {
      editor
    }
  }
</script>

<style scoped>
#app {
  height: 100%;
}
</style>
