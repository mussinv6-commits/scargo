<template>
  <div class="screen">
    <TopBar title="알림" back="/">
      <button class="btn sm" style="border: 0; background: transparent; color: var(--primary)" type="button" :disabled="!unread" @click="readAll">모두 읽음</button>
    </TopBar>
    <main class="content" style="padding: 0; gap: 0; background: #fff">
      <div class="tabs">
        <button v-for="t in topTabs" :key="t.id" type="button" class="tab" :class="{ on: top === t.id }" @click="top = t.id; sub = 'ALL'">{{ t.label }}</button>
      </div>
      <div v-if="top === 'NOTICE'" class="subtabs">
        <button v-for="s in noticeSubs" :key="s.id" type="button" class="sub" :class="{ on: sub === s.id }" @click="sub = s.id">{{ s.label }}</button>
      </div>
      <div v-if="error" class="error-box" style="margin: 12px">{{ error }}</div>
      <div v-if="!loading && !filtered.length" class="empty">{{ emptyText }}</div>
      <div v-for="n in filtered" :key="n.id" class="item tap" role="button" :class="{ hot: !n.read && isDanger(n) }" @click="open(n)">
        <i :style="{ background: n.read ? 'var(--line-strong)' : isDanger(n) ? 'var(--accent)' : 'var(--primary)' }"></i>
        <div class="stack" style="gap: 2px; flex: 1">
          <b :style="{ color: !n.read && isDanger(n) ? 'var(--danger-ink)' : 'inherit' }">{{ n.title }}</b>
          <span v-if="n.message && n.message.trim() !== (n.title||'').trim()" style="font-size: 13px; color: var(--ink-2)">{{ n.message }}</span>
          <span class="small faint">{{ ago(n.createdAt) }}</span>
        </div>
      </div>
    </main>
  </div>
</template>

<script setup>
import { errText } from '../lib/api'
import { computed, onMounted, ref } from 'vue'
import TopBar from '../components/TopBar.vue'
import { Api } from '../lib/api'
import { ago } from '../lib/format'
import { isDanger, isFire, isNotice, isUrgentNotice } from '../lib/notif'
import { fireNotices } from '../lib/inbox'
import { useRouter } from 'vue-router'

const items = ref([])
const loading = ref(true)
const error = ref('')
const top = ref('ALL') // ALL | NOTICE | FIRE
const sub = ref('ALL') // ALL | URGENT | GENERAL (when NOTICE)
const topTabs = [
  { id: 'ALL', label: '전체' },
  { id: 'NOTICE', label: '공지사항' },
  { id: 'FIRE', label: '화재경보' },
]
const noticeSubs = [
  { id: 'ALL', label: '전체' },
  { id: 'URGENT', label: '긴급' },
  { id: 'GENERAL', label: '일반' },
]
const unread = computed(() => items.value.filter(n => !n.read).length)
const filtered = computed(() => {
  let list = items.value
  if (top.value === 'NOTICE') {
    list = list.filter(isNotice)
    if (sub.value === 'URGENT') list = list.filter(isUrgentNotice)
    else if (sub.value === 'GENERAL') list = list.filter(n => isNotice(n) && !isUrgentNotice(n))
  } else if (top.value === 'FIRE') {
    list = list.filter(isFire)
  }
  return list
})
const emptyText = computed(() => {
  if (top.value === 'NOTICE') return sub.value === 'URGENT' ? '긴급 공지가 없습니다.' : sub.value === 'GENERAL' ? '일반 공지가 없습니다.' : '공지사항이 없습니다.'
  if (top.value === 'FIRE') return '화재경보가 없습니다.'
  return '알림이 없습니다.'
})

async function load() {
  let list = []
  try { list = (await Api.notifications()) || [] } catch (e) { error.value = errText(e) }
  items.value = [...list, ...fireNotices(list)].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
  loading.value = false
}
const router = useRouter()
function open(n) { n.read = true; router.push('/alerts/' + n.id) }
async function readAll() {
  await Api.readAll().catch(() => {})
  items.value = items.value.map(n => ({ ...n, read: true }))
}
onMounted(load)
</script>

<style scoped>
.tabs { display: flex; gap: 0; border-bottom: 1px solid var(--sunken); background: #fff; position: sticky; top: 0; z-index: 2 }
.tab { flex: 1; padding: 12px 8px; border: 0; background: transparent; font-size: 14px; font-weight: 600; color: var(--ink-2); border-bottom: 2px solid transparent }
.tab.on { color: var(--primary); border-bottom-color: var(--primary) }
.subtabs { display: flex; gap: 8px; padding: 10px 14px; background: #f8fafc; border-bottom: 1px solid var(--sunken) }
.sub { padding: 6px 12px; border-radius: 999px; border: 1px solid var(--line-strong); background: #fff; font-size: 13px; color: var(--ink-2) }
.sub.on { border-color: var(--primary); background: #eff6ff; color: var(--primary); font-weight: 600 }
.item.tap { cursor: pointer; }
.item.tap:active { background: var(--sunken); }
.item { padding: 14px 18px; border-bottom: 1px solid var(--sunken); display: flex; gap: 12px; }
.item.hot { background: #FFF6F0; color: #1f2937; } .item.hot span { color: #4b5563 !important; }
.item i { width: 8px; height: 8px; border-radius: 4px; margin-top: 7px; flex-shrink: 0; }
.item b { font-size: 14px; }
</style>