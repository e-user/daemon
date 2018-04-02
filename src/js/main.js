import 'bootstrap'
import 'typeface-inconsolata'
import './main.scss'
import Vue from 'vue'
import App from './App.vue'
import Daemon from './daemon'

const daemon = Daemon.create({
  url: window.location.href
})

Vue.config.productionTip = false
Vue.use(daemon)

new Vue({
  el: '#app',
  render: r => r(App)
})
