<template>
  <textarea v-bind:autofocus="active"></textarea>
</template>

<script>
import CodeMirror from 'codemirror'
import keycodes from './daemon/keycodes'

export default {
  props: ['buffer', 'active', 'readOnly', 'tailMode'],
  mounted () {
    const daemon = this.$daemon

    this.cm = CodeMirror.fromTextArea(this.$el, {
      theme: 'cyberpunk',
      cursorBlinkRate: 0,
      tabSize: 8,
      readOnly: this.readOnly
    })

    const doc = this.cm.doc

    const onKeyDown = (cm, event) => {
      const {line, ch} = cm.getCursor()
      daemon.send('input', {
        key: {
          "ctrl?": event.ctrlKey,
          "alt?": event.altKey,
          "shift?": event.shiftKey,
          "meta?": event.metaKey,
          code: event.keyCode,
          char: event.key,
          name: event.code,
          id: keycodes[event.keyCode] || "wtf"
        },
        buffer: this.buffer,
        pos: [line, ch]
      })
      event.preventDefault()
    }

    function onKeyPress (cm, event) {
      event.preventDefault()
    }

    this.cm.on('keydown', onKeyDown)
    this.cm.on('keypress', onKeyPress)
    this.cm.on('changes', () => {
      if (this.tailMode) {
        this.cm.extendSelection({line: doc.lastLine(), ch: 0})
      }
    })

    daemon.send('buffer-state', {id: this.buffer}).then(state => {
      this.cm.setValue(state)
      //doc.setCursor(cursor)
      //this.cm.scrollIntoView()
      //this.cm.extendSelection({ line: doc.lastLine() })
    })

    daemon.onBuffer(this.buffer, (op, data) => {
      // if (op === 'append') {
      //   const lastLine = doc.lastLine()
      //   doc.replaceRange(data, { line: lastLine })
      //   this.cm.extendSelection({ line: lastLine + 1, ch: 0 })
      // }
      if (op === 'insert') {
        const {string, pos} = data
        doc.replaceRange(string, {line: pos[0], ch: pos[1]})
      }
    })

    // TODO
    // "cursorActivity" (instance: CodeMirror)
    // doc.markText(from: {line, ch}, to: {line, ch}, ?options: object) → TextMarker
  },
}
</script>

<style scoped>

</style>
