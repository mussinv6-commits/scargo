import { bootDriverSettings } from './lib/driverSettings'
bootDriverSettings()
import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import 'leaflet/dist/leaflet.css'
import './styles.css'
import './theme-dark.css' // 26.10.24 다크 모드
import { startThemeWatcher } from './lib/theme'

startThemeWatcher() // 26.10.24: 라이트/다크/자동(일몰·일출)

createApp(App).use(router).mount('#app')
