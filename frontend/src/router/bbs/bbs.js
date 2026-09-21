import bbslist from "@/components/bbs/bbslist.vue";
import bbswrite from "@/components/bbs/bbswrite.vue";
import bbsdetail from "@/components/bbs/bbsdetail.vue";
import scrlist from "@/components/bbsScroll/scrlist.vue";
import bbsupdate from "@/components/bbs/bbsupdate.vue";

export default [
    {
        path:'/bbslist',    // <a href='/bbslist'
        name:'bbslist',     // this.$router
        component:bbslist
    },
    {
        path:'/bbswrite',
        name:'bbswrite',
        component:bbswrite
    },
    {
        path:'/bbsdetail',
        name:'bbsdetail',
        component:bbsdetail
    },
    {
        path:'/scrlist',
        name:'scrlist',
        component:scrlist
    },
    {
        path:'/bbsupdate',
        name:'bbsupdate',
        component:bbsupdate
    },

]