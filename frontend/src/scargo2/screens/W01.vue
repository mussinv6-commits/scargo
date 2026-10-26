<template>
  <section class="scr" id="W-01"><div class="card login"><h2 style="margin-top:0">SCARGO 로그인</h2><div class="meta">관리자 · 관제사 · 허브 관제사 · 센터 관제사 · 운송사 담당자 계정으로 로그인하세요.</div><br>
    아이디<div class="fld"><input v-model="id" autocomplete="username" placeholder="아이디" @keyup.enter="login"><label class="sid"><input type="checkbox" v-model="sid" @change="!sid && storeSavedId(false)"> 아이디 저장</label></div>
    비밀번호<div class="fld"><input :type="pwOn ? 'text' : 'password'" v-model="pw" autocomplete="current-password" placeholder="비밀번호" @keyup.enter="login"><button type="button" class="eye" :aria-label="pwOn ? '비밀번호 숨기기' : '비밀번호 표시'" @click="pwOn = !pwOn">{{ pwOn ? '숨김' : '표시' }}</button></div>
    <button class="btn y" style="width:100%" @click="login">로그인</button>
    <div v-if="err" class="b crit" style="margin-top:10px;display:block;text-align:center;padding:6px">{{ err }}</div></div></section>
</template>
<script setup>
// W-01 실제 로그인 (POST /api/accounts/login). 아이디 저장: 아이디만 localStorage, 비밀번호는 저장하지 않음
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { API_BASE } from '@/utils/apiBase.js'
import { setLogin } from '@/auth/authState.js'
import { loadSavedId, storeSavedId } from '@/utils/savedId.js'
import { WEB_ROLE_OF, APP_ONLY, HOME, homeOf } from '../roles.js'
const router = useRouter()
const saved = loadSavedId(); const id = ref(saved), pw = ref(''), sid = ref(!!saved), pwOn = ref(false), err = ref('')
async function login() {
  err.value = ''
  try {
    const r = await fetch(API_BASE + '/api/accounts/login', { method: 'POST', credentials: 'include', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ userId: id.value, userPw: pw.value }) })
    const mem = r.ok ? await r.json() : null
    if (!mem || mem.userId === undefined) { const j = mem || await r.json().catch(() => ({})); err.value = j.message || '아이디 또는 비밀번호를 확인해 주세요.'; return }
    storeSavedId(sid.value, mem.userId)
    if (APP_ONLY.includes(mem.userType)) { err.value = '기사 계정은 앱을 이용해 주세요.'; fetch(API_BASE + '/api/accounts/logout', { method: 'POST', credentials: 'include' }).catch(() => {}); return }
    setLogin(mem)
    if (mem.userType === 'ADMIN' && mem.adminScope === 'SBOT') return router.push('/sbot')
    const role = WEB_ROLE_OF[mem.userType]
    if (mem.userType === 'CORPORATE_PENDING') return router.push('/pending') // v13: 승인 대기 안내 화면
    if (!role) { err.value = '웹 관제 권한이 없는 계정입니다.'; return }
    const back = sessionStorage.getItem('location'); sessionStorage.removeItem('location')
    router.push(back && back.startsWith('/w/') ? back : '/w/' + HOME[role])
  } catch { err.value = '서버에 연결할 수 없습니다.' }
}
</script>
<style scoped>
.fld{position:relative}.fld input{padding-right:96px}
.sid{position:absolute;right:10px;top:50%;transform:translateY(-50%);margin-top:-3px;font-size:12px;color:#6b7590;display:flex;align-items:center;gap:4px;cursor:pointer;white-space:nowrap}
.sid input{width:auto;margin:0;padding:0}
.eye{position:absolute;right:8px;top:50%;transform:translateY(-50%);margin-top:-3px;border:1px solid #c9d0de;background:#fff;border-radius:5px;font-size:12px;padding:3px 8px;cursor:pointer;color:#14213d;font-family:inherit}
</style>
