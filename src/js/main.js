import 'bootstrap'
import 'typeface-inconsolata'
import './main.scss'
import Vue from 'vue'
import App from './App.vue'
import Daemon from './daemon'

Vue.config.productionTip = false

Daemon.create({
  url: window.location.href
}).then(daemon => {
  Vue.use(daemon)

  new Vue({
    el: '#app',
    render: r => r(App)
  })
})
