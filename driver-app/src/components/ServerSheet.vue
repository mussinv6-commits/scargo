<template>
  <div class="overlay" style="position: fixed" @click.self="$emit('close')">
    <form class="sheet" @submit.prevent="save">
      <h2 class="section-title">서버 주소</h2>
      <p class="small muted" style="margin: 0">
        PC 브라우저 테스트는 비워 두세요(개발 프록시 사용).<br />
        에뮬레이터: <b>http://10.0.2.2:8080</b> · 휴대폰: <b>http://PC의IP:8080</b><br />
        같은 Wi-Fi면 <b>자동 찾기</b>로 PC IP가 바뀌어도 서버를 찾습니다. (AMR 앱의 QR 서버주소와 같은 목적)
      </p>
      <label class="field">백엔드 주소
        <input v-model="url" class="input" placeholder="예) http://192.168.0.15:8080" inputmode="url" autocapitalize="off" />
      </label>
      <div v-if="result" :class="['banner', result.ok ? 'ok' : 'danger']"><div><b>{{ result.ok ? '연결 성공' : '연결 실패' }}</b><span>{{ result.msg }}</span></div></div>
      <p class="small muted" style="margin: 0">현재 사용 중: <b>{{ getApiBase() || '개발 프록시' }}</b> · 기본값(.env): {{ defBase || '개발 프록시' }}</p>
      <div class="row">
        <button type="button" class="btn" :disabled="testing || finding" @click="findLan">{{ finding ? '찾는 중…' : '같은 Wi-Fi에서 자동 찾기' }}</button>
      </div>
      <div class="row">
        <button type="button" class="btn" @click="reset">기본값으로</button>
        <button type="button" class="btn" @click="$emit('close')">닫기</button>
      </div>
      <div class="row">
        <button type="button" class="btn" :disabled="testing || finding" @click="test">{{ testing ? '확인 중…' : '연결 확인' }}</button>
        <button type="submit" class="btn primary">저장</button>
      </div>
    </form>
  </div>
</template>

<script setup>
import { errText } from '../lib/api' // 26.10.29 QA: 오류는 기사용 문구로
import { ref } from 'vue'
import { Api, getApiBase, getSavedApiBase, getDefaultApiBase, setApiBase } from '../lib/api'
import { discoverApiBase, markApiBaseManual, rememberLastOkBase } from '../lib/discover'

const emit = defineEmits(['close', 'saved'])
const defBase = getDefaultApiBase()
function reset() { setApiBase(''); markApiBaseManual(false); url.value = getApiBase(); result.value = null; emit('saved'); emit('close') }
const url = ref(getSavedApiBase() || getApiBase())
const testing = ref(false)
const finding = ref(false)
const result = ref(null)

async function test() {
  const prev = getSavedApiBase() // fix_app_1026b: 저장값(없으면 빈 값)으로 복원 → .env 기본값이 저장되지 않음
  setApiBase(url.value)
  testing.value = true
  try {
    await Api.health()
    rememberLastOkBase(getApiBase())
    result.value = { ok: true, msg: (url.value || '개발 프록시') + ' 에 연결되었습니다.' }
  } catch (e) {
    result.value = { ok: false, msg: errText(e) }
  } finally {
    testing.value = false
    setApiBase(prev)
  }
}
async function findLan() {
  finding.value = true
  result.value = null
  try {
    const found = await discoverApiBase({ force: true })
    if (!found) {
      result.value = { ok: false, msg: '같은 Wi-Fi에서 서버(8080)를 찾지 못했습니다. PC에서 백엔드가 켜져 있는지 확인해 주세요.' }
      return
    }
    url.value = found
    setApiBase(found)
    markApiBaseManual(false)
    rememberLastOkBase(found)
    result.value = { ok: true, msg: '찾았습니다: ' + found }
    emit('saved')
  } catch (e) {
    result.value = { ok: false, msg: errText(e) }
  } finally {
    finding.value = false
  }
}
function save() {
  setApiBase(url.value === defBase ? '' : url.value)
  markApiBaseManual(!!url.value && url.value !== defBase)
  if (url.value) rememberLastOkBase(url.value)
  emit('saved')
  emit('close')
}
</script>