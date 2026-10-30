<template>
  <!-- 26.10.24 점검표 손상 사진: 작성 중(DRAFT)일 때만 올리기 · 삭제, 확정 · 취소된 기록은 보기만 -->
  <div class="ip" :class="{ 'is-dark': dark }">
    <div class="ip-head">
      <strong>손상 사진 <span class="ip-n">{{ photos.length }}/10</span></strong>
      <label v-if="canEdit" class="ip-add" :class="{ off: busy || photos.length >= 10 }">
        + 사진 추가
        <input type="file" accept="image/jpeg,image/png,image/webp" multiple hidden :disabled="busy || photos.length >= 10" @change="onPick" />
      </label>
    </div>
    <p v-if="message" class="ip-msg">{{ message }}</p>
    <p v-if="!photos.length && !loading" class="ip-muted">{{ canEdit ? '손상이 있으면 사진을 남겨 두세요 (jpg · png · webp, 1장 5MB 이하).' : '첨부된 사진이 없습니다.' }}</p>
    <div class="ip-grid">
      <figure v-for="p in photos" :key="p.photoId" class="ip-item">
        <a v-if="urls[p.photoId]" :href="urls[p.photoId]" target="_blank" rel="noopener"><img :src="urls[p.photoId]" :alt="p.fileName" /></a>
        <div v-else class="ip-ph">...</div>
        <figcaption>
          <span class="ip-name" :title="p.fileName">{{ p.fileName }}</span>
          <button v-if="canEdit" type="button" class="btn-act del" :disabled="busy" @click="remove(p)">삭제</button>
        </figcaption>
      </figure>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, reactive, ref, watch } from 'vue'
import { listPhotos, uploadPhoto, deletePhoto, photoBlobUrl } from '@/utils/containerInspectionApi.js'
import { friendlyError } from '@/utils/apiHelpers.js'

const props = defineProps({
  inspectionId: { type: Number, required: true },
  canEdit: { type: Boolean, default: false },
  dark: { type: Boolean, default: false },
})

const photos = ref([])
const urls = reactive({})
const loading = ref(false)
const busy = ref(false)
const message = ref('')

function clearUrls() {
  Object.keys(urls).forEach((k) => { URL.revokeObjectURL(urls[k]); delete urls[k] })
}

async function load() {
  loading.value = true
  message.value = ''
  clearUrls()
  try {
    photos.value = await listPhotos(props.inspectionId)
    for (const p of photos.value) {
      photoBlobUrl(props.inspectionId, p.photoId).then((u) => { urls[p.photoId] = u }).catch(() => {})
    }
  } catch (err) {
    photos.value = []
    message.value = friendlyError(err, '사진을 불러오지 못했습니다.')
  } finally {
    loading.value = false
  }
}

async function onPick(e) {
  const files = Array.from(e.target.files || [])
  e.target.value = ''
  if (!files.length) return
  busy.value = true
  message.value = ''
  try {
    for (const f of files) {
      if (photos.value.length >= 10) break
      if (f.size > 5 * 1024 * 1024) { message.value = `${f.name}: 5MB 이하만 올릴 수 있습니다.`; continue }
      await uploadPhoto(props.inspectionId, f)
      photos.value = await listPhotos(props.inspectionId)
    }
  } catch (err) {
    message.value = friendlyError(err, '사진을 올리지 못했습니다.')
  } finally {
    busy.value = false
    await load()
  }
}

async function remove(p) {
  if (!confirm(`${p.fileName} 사진을 삭제할까요?`)) return
  busy.value = true
  try {
    await deletePhoto(props.inspectionId, p.photoId)
    await load()
  } catch (err) {
    message.value = friendlyError(err, '사진을 삭제하지 못했습니다.')
  } finally {
    busy.value = false
  }
}

watch(() => props.inspectionId, load, { immediate: true })
onBeforeUnmount(clearUrls)
</script>

<style scoped>
.ip { margin-top: 12px; padding-top: 10px; border-top: 1px dashed #d5dbe5; font-size: 13px; }
.ip-head { display: flex; align-items: center; gap: 10px; }
.ip-n { color: #6b7280; font-weight: 400; margin-left: 4px; }
.ip-add { margin-left: auto; padding: 5px 12px; border: 1px solid #c9d3e0; border-radius: 6px; cursor: pointer; background: #fff; }
.ip-add.off { opacity: .5; cursor: default; }
.ip-msg { margin: 6px 0 0; color: #c0392b; }
.ip-muted { margin: 6px 0 0; color: #6b7280; }
.ip-grid { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 8px; }
.ip-item { margin: 0; width: 120px; }
.ip-item img, .ip-ph { width: 120px; height: 90px; object-fit: cover; border-radius: 6px; border: 1px solid #d5dbe5; display: flex; align-items: center; justify-content: center; }
.ip-item figcaption { display: flex; justify-content: space-between; gap: 4px; margin-top: 3px; }
.ip-name { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #6b7280; font-size: 12px; }
.ip-del { border: 0; background: none; color: #c0392b; cursor: pointer; font-size: 12px; padding: 0; }
.is-dark { border-top-color: #1d3b60; }
.is-dark .ip-add { background: #0b1d36; border-color: #2a4d76; color: #e6eff7; }
.is-dark .ip-n, .is-dark .ip-muted, .is-dark .ip-name { color: #9fb0c6; }
.is-dark .ip-msg, .is-dark .ip-del { color: #fca5a5; }
.is-dark .ip-item img, .is-dark .ip-ph { border-color: #2a4d76; }
</style>
