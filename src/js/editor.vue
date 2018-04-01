<template>
  <textarea>
  {{ text }}
  </textarea>
</template>

<script>
  import CodeMirror from 'codemirror'

  export default {
    props: ['text'],
    mounted () {
      this.cm = CodeMirror.fromTextArea(this.$el, {
        theme: 'cyberpunk',
        cursorBlinkRate: 0,
        tabSize: 8
      })
    }
  }
</script>

<style scoped>

</style>
