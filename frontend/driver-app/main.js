// 26.10.08 추가: 화물차 기사 앱 진입점
// 웹(src/main.js)과 같은 화면 컴포넌트를 쓰되, 기사 화면만 묶어서 모바일 앱으로 빌드한다.
// 빌드: npm run build:driver  →  dist-driver/  →  npx cap sync android
import { createApp } from 'vue'
import '@/style.css'
import 'bootstrap/dist/css/bootstrap.css'
import '@/components/CSS/theme.css'
import '@/components/CSS/unify.css'
import '@/driverApp/appStyle.css'

import DriverApp from '@/driverApp/DriverApp.vue'
import router from '@/driverApp/router.js'

createApp(DriverApp).use(router).mount('#app')
