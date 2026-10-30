import { createApp } from 'vue'
import './style.css'
import App from './App.vue'

import BootstrapVue3 from 'bootstrap-vue-3'
import 'bootstrap/dist/css/bootstrap.css'
import 'bootstrap-vue-3/dist/bootstrap-vue-3.css'
import './components/CSS/theme.css'
import './components/CSS/unify.css'
import './components/CSS/sg-vars.css' // 26.10.21 v7 (통합)

import router from './router/index'
import { startSafetyWatch } from './utils/safetyWatch.js' // 26.10.29 긴급 경보(화재) 전체 화면 표시
import { API_BASE } from './utils/apiBase.js'

const app = createApp(App)
app.use(BootstrapVue3)
app.use(router)
app.mount('#app')
startSafetyWatch({ base: API_BASE, mode: 'popup', skip: () => /\/(sbot|amr)/i.test(location.pathname + location.hash) }) // 종료는 허브 관제(S-Bot)의 "화재 종료" 버튼
