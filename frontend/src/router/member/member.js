//import home from "../../components/home.vue";
// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import home from "@/components/home.vue"; // @/ 기본경로인 src가 된다
import GroupMain from "@/views/GroupMain.vue"; // 26.10.20 추가: S그룹 메인
import SbotLayout from "@/views/SbotLayout.vue"; // 26.10.20 추가: S-Bot 전용 셸
import AdminAmrTwin from "@/components/admin/screens/AdminAmrTwin.vue";
import login from "@/components/member/login.vue";
import regi from "../../components/member/regi.vue";
// 26.09.16: driver.vue(대시보드)는 MY/차량 화면(driver/screens/MyPage.vue)으로 기능 통합 후 제거.
// 실제 라우트 정의는 router/driver/driverApp.js 에 있고, 여기서는 기존 /driver 경로 호환을 위해 리다이렉트만 유지한다.

export default [
    {
        path:'/',
        name:'group',
        component:GroupMain
    },
    {
        path:'/scargo', // 26.10.20: 기존 S Cargo 홈은 /scargo 로 이동
        name:'home',
        component:home
    },
    {
        path:'/sbot', // 26.10.20: S-Bot 은 S Cargo 관리자 화면과 분리 (관리자 전용)
        component:SbotLayout,
        meta:{ requiresSbotAdmin:true },
        children:[{ path:'', name:'sbot', component:AdminAmrTwin }]
    },
    {
        path:'/login',
        name:'login',
        component:GroupMain /* 26.10.20: 로그인은 S그룹 메인 화면에서 */
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