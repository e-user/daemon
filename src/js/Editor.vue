<template>
  <textarea></textarea>
</template>

<script>
  import CodeMirror from 'codemirror'

  export default {
    props: ['buffer', 'readOnly'],
    mounted () {
      const daemon = this.$daemon
      
      this.cm = CodeMirror.fromTextArea(this.$el, {
        theme: 'cyberpunk',
        cursorBlinkRate: 0,
        tabSize: 8,
        readOnly: this.readOnly
      })

      function onKeyDown (cm, event) {
        daemon.send('input', {
          "ctrl?": event.ctrlKey,
          "alt?": event.altKey,
          "shift?": event.shiftKey,
          "meta?": event.metaKey,
          code: event.keyCode,
          name: event.code
        })
        event.preventDefault()
      }

      function onKeyPress (cm, event) {
        event.preventDefault()
      }

      this.cm.on('keydown', onKeyDown)
      this.cm.on('keypress', onKeyPress)

      // TODO send when ready
      daemon.send('read-buffer', { id: this.buffer }).then(({ buffer }) => {
        this.cm.setValue(buffer)
      })
    },
  }
</script>

<style scoped>

</style>
