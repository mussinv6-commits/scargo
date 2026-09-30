//import home from "../../components/home.vue";
// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import home from "@/components/home.vue"; // @/ 기본경로인 src가 된다
import login from "@/components/member/login.vue";
import regi from "../../components/member/regi.vue";
// 26.09.16: driver.vue(대시보드)는 MY/차량 화면(driver/screens/MyPage.vue)으로 기능 통합 후 제거.
// 실제 라우트 정의는 router/driver/driverApp.js 에 있고, 여기서는 기존 /driver 경로 호환을 위해 리다이렉트만 유지한다.

export default [
    {
        path:'/',
        name:'home',
        component:home
    },
    {
        path:'/login',
        name:'login',
        component:login
    },
    {
        path:'/regi',
        name:'regi',
        component:regi
    },
    {
        path:'/driver',
        redirect:'/driver/app/my-page'
    },


]