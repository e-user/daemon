<template>
  <div id="app" class="d-flex flex-column">
    <editor buffer="*scratch*" active="true"></editor>
    <editor buffer="*Messages*" tail-mode="true"></editor>
  </div>
</template>

<script>
import Editor from './Editor.vue'

export default {
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
