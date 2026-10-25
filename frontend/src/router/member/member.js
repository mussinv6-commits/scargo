//import home from "../../components/home.vue";
// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import home from "@/components/home.vue"; // @/ 기본경로인 src가 된다
import GroupMain from "@/views/GroupMain.vue"; // 26.10.20 추가: S그룹 메인
import SbotLayout from "@/views/SbotLayout.vue"; // 26.10.20 추가: S-Bot 전용 셸
import AdminAmrTwin from "@/components/admin/screens/AdminAmrTwin.vue";
import SbotDashboard from "@/views/SbotDashboard.vue"; // 26.10.22 추가: S-Bot 대시보드
import SbotFrame from "@/views/SbotFrame.vue"; // 26.10.22 추가: 택배 이동/물류센터 3D 화면
import SbotInspections from "@/views/SbotInspections.vue"; // 26.10.23 추가: 허브 도착 · 인계 · 컨테이너 점검
import SbotAmrAudit from "@/views/SbotAmrAudit.vue"; // 26.10.23 추가: AMR 앱 출입증 감사
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
        // 26.10.22 통합: 권한을 화면별로 나눔
        //  허브 AMR 관제(/sbot/hub) = S-Bot 관리자 · CONTROL(조회 전용) · HUB_OP(소속 허브) / 나머지 = S-Bot 관리자만
        children:[
            { path:'', name:'sbot', component:SbotDashboard, meta:{ requiresSbotAdmin:true } }, // 26.10.22: /sbot 은 대시보드, 허브 AMR 관제는 /sbot/hub
            { path:'hub', name:'sbot-hub', component:AdminAmrTwin, meta:{ requiresHubControl:true } },
            { path:'inspections', name:'sbot-inspections', component:SbotInspections, meta:{ requiresHubControl:true } }, // 26.10.23 허브 관제와 같은 권한
            { path:'amr-audit', name:'sbot-amr-audit', component:SbotAmrAudit, meta:{ requiresHubControl:true } }, // 26.10.23 조회는 S-Bot 관리자 · HUB_OP (CONTROL 은 서버 403)
            { path:'move', name:'sbot-move', component:SbotFrame, props:{ page:'move', title:'택배 이동' }, meta:{ requiresSbotAdmin:true } },
            { path:'center', name:'sbot-center', component:SbotFrame, props:{ page:'center', title:'물류센터 관제' }, meta:{ requiresSbotAdmin:true } },
        ]
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