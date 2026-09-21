//import home from "../../components/home.vue";
// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
import home from "@/components/home.vue"; // @/ 기본경로인 src가 된다
import login from "@/components/member/login.vue";
import regi from "../../components/member/regi.vue";

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

]