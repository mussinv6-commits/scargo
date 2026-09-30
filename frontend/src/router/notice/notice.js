import noticeList from "@/components/notice/noticeList.vue";
import noticeDetail from "@/components/notice/noticeDetail.vue";
import noticeUpdate from "@/components/notice/noticeUpdate.vue";
import noticeWrite from "@/components/notice/noticeWrite.vue";

export default [
    {
        path: '/notice',
        name: 'notice',
        component: noticeList
        // http://localhost:5173/notice
    },
    {
        path: '/notice/:id',
        name: 'noticeDetail',
        component: noticeDetail
        // http://localhost:5173/notice/1
    },
    {
        path: '/notice/update/:id',
        name: 'noticeUpdate',
        component: noticeUpdate
    },
    {
        path: '/notice/write',
        name: 'noticeWrite',
        component: noticeWrite
    },
]
