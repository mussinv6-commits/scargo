import { createWebHistory, createRouter } from 'vue-router'; // npm i vue-router@next

// vite.config.js 에 @를 설정해야 @/ 경로를 사용할 수 있다
//import member from './member/member';
import member from '@/router/member/member.js';
import bbs from '@/router/bbs/bbs';

const router = createRouter({
    history:createWebHistory(),
    routes:[
        ...member,
        ...bbs,
    ],
    scrollBehavior(to, from, savedPosition) {
        if (savedPosition) {
            return savedPosition; // 뒤로가기/앞으로가기 시 이전 스크롤 위치 복원
        }
        return { top: 0 }; // 새로운 라우트로 이동 시 항상 맨 위로
    },
})

export default router;