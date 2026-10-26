<script setup>
// S GROUP 랜딩 — 계열 서비스 진입점. 로직 없음.
const ticks = Array.from({ length: 60 }, (_, i) => i * 6);
</script>

<template>
  <main class="sg">
    <header class="sg-top">
      <span class="sg-top__mark">S GROUP</span>
      <span class="sg-top__meta">Busan · Incheon · Pyeongtaek</span>
    </header>

    <section class="sg-hero">
      <svg class="sg-logo" viewBox="0 0 400 400" role="img" aria-label="S GROUP 로고">
        <defs>
          <linearGradient id="sgGold" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0" stop-color="#ead8b4" />
            <stop offset=".55" stop-color="#c9a96e" />
            <stop offset="1" stop-color="#9c7a45" />
          </linearGradient>
        </defs>
        <circle cx="200" cy="200" r="190" fill="none" stroke="url(#sgGold)" stroke-width="1.5" />
        <circle cx="200" cy="200" r="176" fill="none" stroke="#c9a96e" stroke-opacity=".35" stroke-width="1" />
        <g stroke="#c9a96e" stroke-opacity=".55">
          <line v-for="a in ticks" :key="a" x1="200" :y1="a % 30 === 0 ? 30 : 26" x2="200" y2="24"
                :stroke-width="a % 30 === 0 ? 1.4 : .8" :transform="`rotate(${a} 200 200)`" />
        </g>
        <!-- S: 두 줄 레일 -->
        <g fill="none" stroke="url(#sgGold)" stroke-linecap="round">
          <path d="M262 108 C238 82 150 78 140 136 C130 192 262 186 264 252 C266 318 176 324 146 296" stroke-width="22" />
          <path d="M262 108 C238 82 150 78 140 136 C130 192 262 186 264 252 C266 318 176 324 146 296" stroke="#0f1d33" stroke-width="5" />
        </g>
        <circle cx="262" cy="108" r="7" fill="#0f1d33" stroke="#ead8b4" stroke-width="2" />
        <circle cx="146" cy="296" r="7" fill="#0f1d33" stroke="#ead8b4" stroke-width="2" />
        <!-- 트럭 배지 -->
        <g transform="translate(318 84)">
          <circle r="34" fill="#0b1626" stroke="#c9a96e" stroke-width="1.5" />
          <g fill="none" stroke="#ead8b4" stroke-width="1.6" stroke-linejoin="round">
            <rect x="-19" y="-11" width="22" height="16" />
            <path d="M3 -5 H12 L18 1 V5 H3 Z" />
            <circle cx="-11" cy="9" r="3.5" fill="#0b1626" /><circle cx="11" cy="9" r="3.5" fill="#0b1626" />
          </g>
        </g>
        <!-- 이송 로봇 배지 -->
        <g transform="translate(82 316)">
          <circle r="34" fill="#0b1626" stroke="#c9a96e" stroke-width="1.5" />
          <g fill="none" stroke="#ead8b4" stroke-width="1.6" stroke-linejoin="round">
            <rect x="-9" y="-16" width="18" height="12" /><line x1="0" y1="-16" x2="0" y2="-4" />
            <rect x="-17" y="-4" width="34" height="10" />
            <circle cx="-10" cy="11" r="3.5" fill="#0b1626" /><circle cx="10" cy="11" r="3.5" fill="#0b1626" />
          </g>
        </g>
      </svg>

      <h1 class="sg-title">S GROUP</h1>
      <p class="sg-tagline">화물 운송부터 허브 자동화까지, 물류의 모든 흐름을 하나로 잇습니다</p>
      <p class="sg-eyebrow">Connecting every flow of logistics</p>
    </section>

    <nav class="sg-services" aria-label="계열 서비스">
      <RouterLink to="/scargo" class="sg-svc">
        <span class="sg-svc__no">01</span>
        <span class="sg-svc__name">S CARGO</span>
        <span class="sg-svc__desc">항만·화물 운송 플랫폼</span>
        <span class="sg-svc__go">배차 · 게이트 · 계량 <i aria-hidden="true">→</i></span>
      </RouterLink>
      <RouterLink to="/w/W-01" class="sg-svc">
        <span class="sg-svc__no">02</span>
        <span class="sg-svc__name">S-BOT</span>
        <span class="sg-svc__desc">허브 자율 이송 로봇 관제</span>
        <span class="sg-svc__go">컨트롤타워 <i aria-hidden="true">→</i></span>
      </RouterLink>
    </nav>

    <footer class="sg-foot">© 2026 S GROUP</footer>
  </main>
</template>

<style scoped>
.sg {
  min-height: 100vh; display: flex; flex-direction: column; align-items: center;
  background: radial-gradient(ellipse 60% 45% at 50% 30%, #16294a 0%, var(--sg-navy-800) 55%, var(--sg-navy-900) 100%);
  color: var(--sg-gold-300); padding: 0 var(--sp-5) var(--sp-6);
}
.sg-top { width: 100%; max-width: 1120px; display: flex; justify-content: space-between; align-items: center;
  padding: var(--sp-5) 0; border-bottom: 1px solid rgba(201,169,110,.18); }
.sg-top__mark { font-family: var(--font-display); font-weight: 600; letter-spacing: .32em; font-size: 15px; }
.sg-top__meta { font-size: 12px; color: #8792a3; letter-spacing: .04em; }
.sg-hero { text-align: center; padding-top: var(--sp-7); }
.sg-logo { width: min(340px, 72vw); height: auto; display: block; margin: 0 auto; }
.sg-title { font-family: var(--font-display); font-weight: 500; font-size: clamp(44px, 7vw, 72px);
  letter-spacing: .42em; margin: var(--sp-7) 0 var(--sp-4) .42em; color: #efe2c4; line-height: 1; }
.sg-tagline { font-size: clamp(16px, 2.1vw, 21px); color: var(--sg-gold-400); margin: 0 0 var(--sp-3); letter-spacing: .02em; }
.sg-eyebrow { font-size: 12px; text-transform: uppercase; letter-spacing: .34em; color: #7f8ba0; margin: 0; }
.sg-services { display: grid; grid-template-columns: 1fr 1fr; gap: 0; width: 100%; max-width: 720px;
  margin-top: var(--sp-8); border: 1px solid rgba(201,169,110,.45); border-radius: var(--radius); }
.sg-svc { display: grid; grid-template-columns: auto 1fr; column-gap: var(--sp-4); row-gap: var(--sp-1);
  padding: var(--sp-5) var(--sp-5); text-decoration: none; color: inherit; text-align: left; transition: background .2s; }
.sg-svc + .sg-svc { border-left: 1px solid rgba(201,169,110,.45); }
.sg-svc:hover { background: rgba(201,169,110,.07); color: inherit; }
.sg-svc__no { grid-row: span 3; font-family: var(--font-mono); font-size: 12px; color: var(--sg-gold-600); padding-top: 4px; }
.sg-svc__name { font-weight: 700; letter-spacing: .2em; font-size: 17px; color: #efe2c4; }
.sg-svc__desc { font-size: 14px; color: #a9b6c9; }
.sg-svc__go { margin-top: var(--sp-3); font-size: 12px; color: var(--sg-gold-500); }
.sg-svc__go i { font-style: normal; display: inline-block; transition: transform .2s; }
.sg-svc:hover .sg-svc__go i { transform: translateX(3px); }
.sg-foot { margin-top: auto; padding-top: var(--sp-8); font-size: 11px; color: #5f6b7e; letter-spacing: .08em; }
@media (max-width: 640px) {
  .sg-services { grid-template-columns: 1fr; }
  .sg-svc + .sg-svc { border-left: 0; border-top: 1px solid rgba(201,169,110,.45); }
  .sg-top__meta { display: none; }
}
</style>
