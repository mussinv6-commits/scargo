<template>
  <div class="screen">
    <header class="topbar"><button class="icon-btn" type="button" @click="$router.back()">←</button><h1>앱 설정</h1></header>
    <main class="content">
      <div v-if="err" class="error-box">{{ err }}</div>
      <section class="card" v-if="form">
        <div class="between"><div class="stack"><b>음성 안내 (TTS)</b><span class="small muted">내비·검문소·화재</span></div>
          <input type="checkbox" class="switch" v-model="form.ttsEnabled" @change="save" /></div>
        <label class="field">음량 {{ form.ttsVolume }}
          <input type="range" min="0" max="1" step="0.05" v-model.number="form.ttsVolume" @change="save" />
        </label>
        <label class="field">속도 {{ form.ttsRate }}
          <input type="range" min="0.5" max="2" step="0.05" v-model.number="form.ttsRate" @change="save" />
        </label>
      </section>
      <section class="card" v-if="form">
        <b>알림 종류</b>
        <label class="check"><input type="checkbox" v-model="form.alertDispatch" @change="save" /> 배차</label>
        <label class="check"><input type="checkbox" v-model="form.alertException" @change="save" /> 이상·예외</label>
        <label class="check"><input type="checkbox" v-model="form.alertFire" @change="save" /> 화재</label>
        <label class="check"><input type="checkbox" v-model="form.alertNotice" @change="save" /> 공지</label>
      </section>
      <section class="card" v-if="form">
        <label class="field">검문소 필터
          <input class="input" v-model="form.checkpointAllow" placeholder="CHECKPOINT-18,이름 (비우면 전체)" @change="save" />
          <span class="hint">기본값 CHECKPOINT-18(인천항). 비우면 서버가 준 검문소 전부 안내</span>
        </label>
        <div class="between"><div class="stack"><b>큰 글씨</b><span class="small muted">운전 중 가독성</span></div>
          <input type="checkbox" class="switch" v-model="form.largeText" @change="save" /></div>
      </section>
      <section class="card tight">
        <div class="between"><b style="font-size:14px">화면 모드 · 서버 주소</b><button class="btn sm" type="button" @click="$router.push('/me')">내 정보에서</button></div>
        <p class="small muted">다크/라이트 모드와 서버 주소 변경은 기존 내 정보 화면을 사용합니다.</p>
      </section>
      <p v-if="msg" class="hint good">{{ msg }}</p>
    </main>
  </div>
</template>
<script setup>
import { onMounted, reactive, ref } from 'vue'
import { Api, errText } from '../lib/api'
import { applyDriverSettings } from '../lib/driverSettings'
const form = reactive({ ttsEnabled:true, ttsVolume:1, ttsRate:1, alertDispatch:true, alertException:true, alertFire:true, alertNotice:true, checkpointAllow:'CHECKPOINT-18,인천항 과적검문소', largeText:false })
const err=ref(''); const msg=ref('')
async function load(){ try{ Object.assign(form, await Api.menuSettings()); applyDriverSettings(form) }catch(e){ err.value=errText(e)} }
async function save(){ msg.value=''; try{ Object.assign(form, await Api.menuSaveSettings({...form})); applyDriverSettings(form); msg.value='저장했습니다.' }catch(e){ err.value=errText(e)} }
onMounted(load)
</script>
<style scoped>
.switch{appearance:none;-webkit-appearance:none;width:48px;height:28px;border-radius:14px;background:var(--line-strong);position:relative;cursor:pointer}
.switch::after{content:'';position:absolute;top:3px;left:3px;width:22px;height:22px;border-radius:11px;background:#fff;box-shadow:0 1px 2px rgba(0,0,0,.3);transition:left .15s}
.switch:checked{background:var(--primary)}.switch:checked::after{left:23px}
</style>
