<template>
  <!-- 26.09.30 추가: 홈 화면 항만(야드) 날씨 위젯
       - 상단 메뉴의 '날씨' 페이지는 월간 캘린더라 정작 오늘 현장 날씨를 보려면 한 번 더 들어가야 했음
       - 컨테이너 작업에 영향을 주는 풍속/강수를 바로 보여주고, 자세한 달력은 링크로 연결 -->
  <aside class="wx" aria-label="항만 날씨">
    <div class="wx-head">
      <span class="wx-title">항만 날씨</span>
      <div class="wx-ports" role="tablist">
        <button
          v-for="p in PORTS"
          :key="p.key"
          type="button"
          role="tab"
          :aria-selected="port.key === p.key"
          :class="{ on: port.key === p.key }"
          @click="selectPort(p)"
        >
          {{ p.label }}
        </button>
      </div>
    </div>

    <div v-if="loading" class="wx-state">불러오는 중...</div>
    <div v-else-if="error" class="wx-state">{{ error }}</div>
    <template v-else-if="now">
      <div class="wx-now">
        <img :src="icon(now.icon)" :alt="now.desc" width="64" height="64" />
        <div>
          <div class="wx-temp">{{ now.temp }}<small>°C</small></div>
          <div class="wx-desc">{{ now.desc }} · 체감 {{ now.feels }}°</div>
        </div>
      </div>

      <dl class="wx-stats">
        <div :class="{ warn: now.wind >= 10 }">
          <dt>풍속</dt>
          <dd>{{ now.wind }} m/s</dd>
        </div>
        <div>
          <dt>습도</dt>
          <dd>{{ now.humidity }}%</dd>
        </div>
        <div :class="{ warn: now.rain > 0 }">
          <dt>강수(1h)</dt>
          <dd>{{ now.rain }} mm</dd>
        </div>
      </dl>

      <p v-if="now.wind >= 10" class="wx-alert">
        <i class="bi bi-wind"></i> 강풍 주의 — 크레인·고소 작업 일정을 확인하세요.
      </p>

      <ol class="wx-days">
        <li v-for="d in days" :key="d.key">
          <span class="wx-day">{{ d.label }}</span>
          <img :src="icon(d.icon)" :alt="d.desc" width="32" height="32" />
          <span class="wx-range">{{ d.max }}° <em>{{ d.min }}°</em></span>
        </li>
      </ol>
    </template>

    <RouterLink to="/weather" class="wx-more">월간 날씨 캘린더 보기 ›</RouterLink>
  </aside>
</template>

<script setup>
import { onMounted, ref } from 'vue'

const API_KEY = '6b144ee4dd2306755be21d9f00eb8850' // WeatherCalendar.vue 와 같은 OpenWeather 키

const PORTS = [
  { key: 'incheon', label: '인천', lat: 37.4563, lon: 126.6052 },
  { key: 'busan', label: '부산', lat: 35.1028, lon: 129.0403 },
  { key: 'gwangyang', label: '광양', lat: 34.9066, lon: 127.6956 },
  { key: 'pyeongtaek', label: '평택', lat: 36.9667, lon: 126.8333 },
]

const port = ref(PORTS[0])
const now = ref(null)
const days = ref([])
const loading = ref(false)
const error = ref('')

const DOW = ['일', '월', '화', '수', '목', '금', '토']

function icon(code) {
  return `https://openweathermap.org/img/wn/${code}@2x.png`
}

function selectPort(p) {
  if (p.key === port.value.key) return
  port.value = p
  load()
}

async function load() {
  loading.value = true
  error.value = ''
  const q = `lat=${port.value.lat}&lon=${port.value.lon}&units=metric&lang=kr&appid=${API_KEY}`
  try {
    const [cur, fc] = await Promise.all([
      fetch(`https://api.openweathermap.org/data/2.5/weather?${q}`).then((r) => (r.ok ? r.json() : Promise.reject(r.status))),
      fetch(`https://api.openweathermap.org/data/2.5/forecast?${q}`).then((r) => (r.ok ? r.json() : Promise.reject(r.status))),
    ])
    now.value = {
      temp: Math.round(cur.main.temp),
      feels: Math.round(cur.main.feels_like),
      humidity: cur.main.humidity,
      wind: Math.round(cur.wind.speed * 10) / 10,
      rain: cur.rain?.['1h'] ?? 0,
      icon: cur.weather[0].icon,
      desc: cur.weather[0].description,
    }

    // 3시간 단위 예보 → 날짜별 최고/최저 + 정오에 가까운 아이콘 (오늘 제외 4일)
    const byDay = {}
    for (const item of fc.list) {
      const dt = new Date(item.dt * 1000)
      const key = `${dt.getMonth() + 1}-${dt.getDate()}`
      const d = (byDay[key] ||= { key, date: dt, min: Infinity, max: -Infinity, icon: '', desc: '', gap: 99 })
      d.min = Math.min(d.min, item.main.temp_min)
      d.max = Math.max(d.max, item.main.temp_max)
      const gap = Math.abs(dt.getHours() - 12)
      if (gap < d.gap) {
        d.gap = gap
        d.icon = item.weather[0].icon
        d.desc = item.weather[0].description
      }
    }
    const todayKey = `${new Date().getMonth() + 1}-${new Date().getDate()}`
    days.value = Object.values(byDay)
      .filter((d) => d.key !== todayKey)
      .slice(0, 4)
      .map((d) => ({
        ...d,
        label: `${DOW[d.date.getDay()]} ${d.date.getDate()}일`,
        min: Math.round(d.min),
        max: Math.round(d.max),
      }))
  } catch (e) {
    error.value = '날씨 정보를 불러오지 못했습니다.'
    now.value = null
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.wx {
  width: 100%;
  max-width: 340px;
  background: rgba(255, 255, 255, 0.97);
  color: var(--color-text);
  border-radius: 16px;
  padding: 18px 18px 14px;
  box-shadow: 0 18px 40px rgba(6, 22, 40, 0.35);
  text-align: left;
}
.wx-head { display: flex; align-items: center; justify-content: space-between; gap: 8px; margin-bottom: 12px; }
.wx-title { font-size: 14px; font-weight: 800; }
.wx-ports { display: flex; gap: 2px; background: #eef1f6; border-radius: 999px; padding: 2px; }
.wx-ports button {
  border: none;
  background: transparent;
  font-size: 12px;
  font-weight: 700;
  color: var(--color-subtext);
  padding: 4px 9px;
  border-radius: 999px;
  cursor: pointer;
}
.wx-ports button.on { background: var(--color-primary); color: #fff; }
.wx-ports button:focus-visible { outline: 2px solid var(--color-accent); outline-offset: 1px; }
.wx-state { font-size: 13px; color: var(--color-subtext); padding: 28px 0; text-align: center; }
.wx-now { display: flex; align-items: center; gap: 8px; }
.wx-now img { margin: -6px 0 -6px -8px; }
.wx-temp { font-family: 'Barlow Condensed', 'Inter', sans-serif; font-size: 40px; font-weight: 700; line-height: 1; }
.wx-temp small { font-size: 18px; margin-left: 2px; color: var(--color-subtext); }
.wx-desc { font-size: 12.5px; color: var(--color-subtext); margin-top: 4px; }
.wx-stats { display: grid; grid-template-columns: repeat(3, 1fr); gap: 6px; margin: 12px 0 0; }
.wx-stats div { background: #f4f6f8; border-radius: 10px; padding: 8px 10px; }
.wx-stats dt { font-size: 11px; font-weight: 600; color: var(--color-subtext); }
.wx-stats dd { margin: 2px 0 0; font-size: 14px; font-weight: 700; }
.wx-stats .warn { background: rgba(255, 107, 0, 0.1); }
.wx-stats .warn dd { color: var(--color-accent); }
.wx-alert { margin: 10px 0 0; font-size: 12px; font-weight: 700; color: var(--color-accent); }
.wx-days { list-style: none; padding: 0; margin: 12px 0 0; border-top: 1px solid #eef1f6; }
.wx-days li { display: flex; align-items: center; justify-content: space-between; padding: 3px 0; font-size: 13px; }
.wx-day { width: 56px; color: var(--color-subtext); font-weight: 600; }
.wx-range { font-weight: 700; min-width: 64px; text-align: right; }
.wx-range em { font-style: normal; color: var(--color-subtext); font-weight: 500; margin-left: 4px; }
.wx-more { display: block; margin-top: 10px; font-size: 12.5px; font-weight: 700; color: var(--color-primary); }
.wx-more:hover { color: var(--color-accent); }
</style>
