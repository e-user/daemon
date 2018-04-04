<template>
  <div id="app" class="d-flex flex-column">
    <editor></editor>
    <editor read-only="nocursor" buffer="Messages"></editor>
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
