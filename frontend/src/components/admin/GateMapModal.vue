<!-- src/components/admin/GateMapModal.vue -->
<template>
  <div v-if="isOpen" class="modal-backdrop fade show" @click.self="close"></div>
  <div v-if="isOpen" class="modal d-block" tabindex="-1" @click.self="close">
    <div class="modal-dialog modal-lg modal-dialog-centered">
      <div class="modal-content">
        <div class="modal-header">
          <h5 class="modal-title">📍 게이트 위치 지도</h5>
          <button type="button" class="btn-close" @click="close"></button>
        </div>
        <div class="modal-body p-0">
          <div ref="mapContainer" style="height: 480px; width: 100%;"></div>
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
import 'leaflet/dist/leaflet.css'

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
  isOpen: { type: Boolean, default: false },
  gates: { type: Array, default: () => [] }
})

const emit = defineEmits(['close'])

const mapContainer = ref(null)
let map = null
let markersLayer = null

function close() {
  destroyMap()
  emit('close')
}

function destroyMap() {
  if (map) {
    map.remove()
    map = null
    markersLayer = null
  }
}

function initMap() {
  if (!mapContainer.value) return

  destroyMap()

  // 기본 중심 좌표 설정 (인천 물류센터 부근)
  map = L.map(mapContainer.value).setView([37.478, 126.604], 14)

  L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 19,
    attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors'
  }).addTo(map)

  markersLayer = L.layerGroup().addTo(map)
  updateMarkers()

  // 모달 렌더링 타이밍에 맞춰 크기 깨짐 방지
  setTimeout(() => {
    if (map) map.invalidateSize()
  }, 200)
}

function updateMarkers() {
  if (!map || !markersLayer) return

  markersLayer.clearLayers()

  const validRows = props.gates.filter(r => r.latitude && r.longitude)
  if (validRows.length === 0) return

  const bounds = []

  validRows.forEach(gate => {
    const lat = Number(gate.latitude)
    const lng = Number(gate.longitude)
    bounds.push([lat, lng])

    const marker = L.marker([lat, lng])
    marker.bindPopup(`
      <div style="font-family: inherit; font-size: 13px; line-height: 1.4;">
        <strong style="font-size: 14px; color: #1e293b;">${gate.gateName}</strong><br/>
        <span style="color: #64748b;">코드:</span> ${gate.gateCode}<br/>
        <span style="color: #64748b;">유형:</span> ${gate.gateType}<br/>
        <span style="color: #64748b;">설명:</span> ${gate.locationDescription || '없음'}<br/>
        <span style="color: ${gate.isActive ? 'green' : 'red'}; font-weight: bold;">
          ${gate.isActive ? '● 활성화' : '● 비활성화'}
        </span>
      </div>
    `)
    markersLayer.addLayer(marker)
  })

  if (bounds.length > 0) {
    map.fitBounds(bounds, { padding: [50, 50], maxZoom: 16 })
  }
}

watch(() => props.isOpen, (newVal) => {
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