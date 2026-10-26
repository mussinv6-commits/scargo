<template>
  <!-- 26.10.21 v7: 기획 HTML(웹 관제 W-01~W-16) 원본 화면을 그대로 보여 준다. 로그인(W-01)만 실제 서버와 연결 -->
  <iframe class="plan-frame" :src="src" title="SCARGO 웹 관제 (기획 화면)"></iframe>
</template>
<script setup>
import { onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import axios from 'axios'
import { API_BASE } from '@/utils/apiBase.js'
import { setLogin } from '@/auth/authState.js'

const route = useRoute()
const src = `${import.meta.env.BASE_URL}plan/web.html${route.query.role ? '?role=' + encodeURIComponent(route.query.role) : ''}#${route.hash.slice(1) || 'W-01'}`

// 기획 화면 안 로그인 버튼이 부른다. 아이디·비밀번호는 서버로만 보내고 저장하지 않는다.
window.scargoPlanLogin = async (userId, userPw) => {
  try {
    const resp = await axios.post(`${API_BASE}/api/accounts/login`, { userId, userPw }, { withCredentials: true })
    if (!resp.data || resp.data.userId === undefined) throw new Error('아이디 또는 비밀번호를 확인해 주세요.')
    setLogin(resp.data)
    return resp.data
  } catch (e) {
    throw new Error(e.response?.data?.message || (e.response ? '아이디 또는 비밀번호를 확인해 주세요.' : '서버에 연결할 수 없어요. 백엔드(8080)가 켜져 있는지 확인해 주세요.'))
  }
}
onBeforeUnmount(() => { delete window.scargoPlanLogin })
</script>
<style scoped>
.plan-frame { position: fixed; inset: 0; width: 100%; height: 100%; border: 0; background: #f3f5f9; }
</style>
