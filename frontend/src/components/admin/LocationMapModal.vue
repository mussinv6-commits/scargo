<!-- src/components/admin/LocationMapModal.vue -->
<template>
  <div v-if="show" class="modal-backdrop fade show" @click.self="close"></div>
  <div v-if="show" class="modal d-block" tabindex="-1" @click.self="close">
    <div class="modal-dialog modal-lg modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">{{ title }}</h5>
          <button type="button" class="btn-close" @click="close"></button>
        </div>
        <div class="modal-body p-0">
          <div id="map" ref="mapContainer" style="height: 480px; width: 100%;"></div>
        </div>
        <div class="modal-footer">
          <button type="button" class="btn btn-secondary" @click="close">닫기</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import L from 'leaflet'

import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'

delete L.Icon.Default.prototype._getIconUrl
L.Icon.Default.mergeOptions({
  iconUrl: markerIcon,
  iconRetinaUrl: markerIcon2x,
  shadowUrl: markerShadow
})

const props = defineProps({
  show: Boolean,
  title: { type: String, default: '지도 보기' },
  items: { type: Array, default: () => [] } // 적재 위치(섹터) 또는 야드 목록
})

const emit = defineEmits(['close'])
const mapContainer = ref(null)
let map = null

function close() {
  destroyMap()
  emit('close')
}

function destroyMap() {
  if (map) {
    map.remove()
    map = null
  }
}

function initMap() {
  if (!mapContainer.value) return

  destroyMap()

  map = L.map(mapContainer.value).setView([37.4810, 126.6070], 14)

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; OpenStreetMap'
  }).addTo(map)

  const markerGroup = L.layerGroup().addTo(map)
  const bounds = []

  const validItems = props.items.filter(item => item.latitude && item.longitude)

  validItems.forEach(item => {
    const lat = Number(item.latitude)
    const lng = Number(item.longitude)
    bounds.push([lat, lng])

    // 표시할 이름 설정 (섹터명 / 야드명 / 소속야드)
    const label = item.sector || item.yardName || '위치'
    const subLabel = item.yardName ? `소속야드: ${item.yardName}` : ''
    const status = item.status ? `상태: ${item.status}` : ''

    L.marker([lat, lng])
      .bindPopup(`
        <div style="font-size: 13px;">
          <strong>${label}</strong><br/>
          ${subLabel ? subLabel + '<br/>' : ''}
          ${status}
        </div>
      `)
      .addTo(markerGroup)
  })

  if (bounds.length > 0) {
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 17 })
  }

  // 모달 렌더링 타이밍 맞춰 깨짐 방지
  setTimeout(() => {
    if (map) map.invalidateSize()
  }, 200)
}

watch(() => props.show, (newVal) => {
  if (newVal) {
    nextTick(() => {
      initMap()
    })
  } else {
    destroyMap()
  }
})
</script>

<style scoped>
.modal-backdrop {
  background-color: rgba(0, 0, 0, 0.5);
}
</style>