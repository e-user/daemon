import 'bootstrap'
import 'typeface-inconsolata'
import './main.scss'
import Vue from 'vue'
import app from './app.vue'
import Daemon from './daemon'

Daemon.create({
  url: window.location.href
})

Vue.config.productionTip = false

new Vue({
  el: '#app',
  render: r => r(app)
})
