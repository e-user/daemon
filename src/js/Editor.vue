<template>
  <textarea v-bind:autofocus="active"></textarea>
</template>

<script>
import CodeMirror from 'codemirror'
import keycodes from './daemon/keycodes'

export default {
  props: ['buffer', 'active', 'tailMode'],
  mounted () {
    const daemon = this.$daemon

    const cm = CodeMirror.fromTextArea(this.$el, {
      theme: 'cyberpunk',
      cursorBlinkRate: 0,
      tabSize: 8,
      scrollbarStyle: null
    })

    const {doc} = cm

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

    let inversionMarker
    function invertCursor () {
      if (inversionMarker) {
        inversionMarker.clear()
      }

      if (cm.hasFocus()) {
        const {line, ch} = cm.getCursor()
        inversionMarker = doc.markText({line, ch}, {line, ch: ch + 1}, {
          className: 'invert',
          atomic: true
        })
      }
    }

    cm.on('keydown', onKeyDown)
    cm.on('keypress', onKeyPress)
    cm.on('changes', () => {
      if (this.tailMode) {
        cm.extendSelection({line: doc.lastLine(), ch: 0})
      }
    })
    cm.on('cursorActivity', invertCursor)
    cm.on('focus', invertCursor)
    cm.on('blur', invertCursor)

    daemon.send('buffer-state', {id: this.buffer}).then(state => {
      cm.setValue(state)
      //doc.setCursor(cursor)
      //this.cm.scrollIntoView()
      //this.cm.extendSelection({ line: doc.lastLine() })
    })

    const handlers = {
      insert ([[line, ch], string]) {
        doc.replaceRange(string, {line, ch})
      },
      'move-cursor' ([line, ch]) {
        doc.setCursor({line, ch})
      },
      delete ([[line1, ch1], [line2, ch2]]) {
        doc.replaceRange('', {line: line1, ch: ch1}, {line: line2, ch: ch2})
      }
    }

    daemon.onBuffer(this.buffer, ops => {
      cm.operation(() => ops.forEach(({op, data}) => (handlers[op] || (() => {}))(data)))
    })
  },
}
</script>

<style>
.invert {
  filter: invert(100%);
}
</style>
