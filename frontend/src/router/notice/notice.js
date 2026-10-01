import noticeList from "@/components/notice/noticeList.vue";
import noticeDetail from "@/components/notice/noticeDetail.vue";
import noticeUpdate from "@/components/notice/noticeUpdate.vue";
import noticeWrite from "@/components/notice/noticeWrite.vue";

export default [
    {
        path: '/notice',
        name: 'notice',
        component: noticeList
    },
    {
        path: '/notice/write',
        name: 'noticeWrite',
        component: noticeWrite,
        meta: { requiresAdmin: true },
    },
    {
        path: '/notice/update/:id',
        name: 'noticeUpdate',
        component: noticeUpdate,
        meta: { requiresAdmin: true },
    },
    {
        path: '/notice/:id',
        name: 'noticeDetail',
        component: noticeDetail
    },
]
