<template>
  <!-- 26.10.08 추가: 허브 AMR 3D 디지털 트윈 (Three.js)
       로직은 src/components/amr/ 아래 모듈에 있고, 이 화면은 붙이기만 한다. -->
  <div ref="root" class="amr-twin">
  <div class="wrap">
    <header class="twin-head">
      <div>
        <div class="eyebrow">S-CARGO · Logistics Center Digital Twin</div>
        <h1>허브 AMR 예지보전</h1>
      </div>
      <div class="stages">
        <div class="stage now" data-p="1"><span class="n">STEP 01</span><span class="t">정상 운영</span></div>
        <div class="stage" data-p="2"><span class="n">STEP 02</span><span class="t">이상 징후 감지</span></div>
        <div class="stage" data-p="3"><span class="n">STEP 03</span><span class="t">AI 고장 예측</span></div>
        <div class="stage" data-p="4"><span class="n">STEP 04</span><span class="t">교대 · 계획 정비</span></div>
        <div class="stage" data-p="5"><span class="n">STEP 05</span><span class="t">정상 복귀</span></div>
      </div>
      <div class="controls">
        <span class="live"><i></i>LIVE</span>
        <button id="tw-play" type="button">일시정지</button>
        <button type="button" class="spd" data-s="1" aria-pressed="true">1x</button>
        <button type="button" class="spd" data-s="2" aria-pressed="false">2x</button>
        <button type="button" class="spd" data-s="4" aria-pressed="false">4x</button>
        <button id="tw-restart" type="button">처음부터</button>
        <div class="qual" role="group" aria-label="화질">
          <button type="button" data-q="normal" aria-pressed="true" title="그림자 · 빛 번짐 · 먼지 효과 포함">일반</button>
          <button type="button" data-q="low" aria-pressed="false" title="그래픽카드 없는 노트북용 — 효과를 끄고 가볍게">저사양</button>
        </div>
        <button id="tw-2d" type="button" aria-pressed="false" title="위에서 내려다본 평면 지도 (회전 없음 · 가볍게)">2D</button>
        <button id="tw-theme" type="button" title="다크 / 라이트 전환">라이트 모드</button>
        <button id="tw-notes" type="button" aria-expanded="false" title="화물 데이터 · 센서 · 예지보전 설명">설명</button>
        <button id="tw-sound" type="button" class="snd" aria-pressed="false" title="현장 음향 (AMR 모터 · OCR · 셔터 · 트럭 · 경보)"><span class="ico" aria-hidden="true"></span><span class="lbl">소리 켜기</span></button>
      </div>
    </header>

    <div class="main">
      <div class="side side-l">
        <div class="card">
          <div class="grouphead"><span class="gtitle">로봇 상태</span><span class="gmeta">AMR 가동 <b id="tw-kAct">4/5</b> · 비계획 정지 <b class="good" id="tw-kStop">0</b></span></div>
          <div class="fleet" id="tw-fleet"></div>
        </div>
        <div class="card">
          <div class="selhead"><span class="label">Selected Unit</span><span class="pill ok" id="tw-selPill">정상</span></div>
          <div class="selname" id="tw-selName">AMR-03</div>
          <div class="selstate" id="tw-selState">-</div>
          <div class="metrics">
            <div class="metric"><span class="v" id="tw-mVib">-</span><span class="k">진동 mm/s</span></div>
            <div class="metric"><span class="v" id="tw-mTemp">-</span><span class="k">모터 °C</span></div>
            <div class="metric"><span class="v" id="tw-mHealth">-</span><span class="k">건강점수</span></div>
            <div class="metric"><span class="v" id="tw-mCum">-</span><span class="k">누적 운반 t</span></div>
            <div class="metric"><span class="v" id="tw-mLoad">-</span><span class="k">현재 적재 kg</span></div>
            <div class="metric"><span class="v" id="tw-mBatt">-</span><span class="k">배터리 %</span></div>
          </div>
          <canvas class="twin-chart" id="tw-chart" aria-label="진동 추세 그래프"></canvas>
          <div class="rul" id="tw-rul"></div>
        </div>
        <div class="card card-log">
          <span class="label">Event Log</span>
          <ul class="log" id="tw-log"></ul>
        </div>
      </div>
      <div class="floorwrap">
        <div class="twin-viewport" id="tw-viewport" aria-label="허브 3D 디지털 트윈. 입고 도크에서 운송장을 스캔하고, 권역별 랙에 보관한 뒤, 권역별 출고 도크의 택배차로 옮긴다.">
          <div class="twin-banner" id="tw-banner"><i></i><span></span></div>
          <div class="twin-views">
            <button type="button" data-view="auto" aria-pressed="true">자동 연출</button>
            <button type="button" data-view="overview" aria-pressed="false">전체 보기</button>
            <button type="button" data-view="follow" aria-pressed="false">선택 AMR 따라가기</button>
          </div>
          <div class="capbar">
            <p class="caption" id="tw-caption"></p>
            <div class="pipe" aria-label="데이터 흐름">
              <span class="node" data-k="0">AMR 센서</span><span class="arr">›</span>
              <span class="node" data-k="1">엣지 게이트웨이</span><span class="arr">›</span>
              <span class="node" data-k="2">시계열 DB</span><span class="arr">›</span>
              <span class="node" data-k="3">AI 예측 모델</span><span class="arr">›</span>
              <span class="node" data-k="4">관제 대시보드</span><span class="arr">›</span>
              <span class="node" data-k="5">재배분 · 정비</span>
            </div>
          </div>
          <div class="twin-help">드래그 회전 · 휠 확대 · 우클릭 드래그 이동 · AMR/박스 클릭 선택</div>
        </div>
        <div class="legend">
          <span><i style="background:var(--hub0)"></i>인천 권역</span>
          <span><i style="background:var(--hub1)"></i>서울 권역</span>
          <span><i style="background:var(--hub2)"></i>경기 권역</span>
          <span><i style="background:#e9eff2"></i>냉장 창고 화물</span>
          <span><i style="background:#f2b705"></i>무인 지게차 (위 단 보충)</span>
          <span><i style="background:var(--cyan)"></i>AMR 정상</span>
          <span><i style="background:var(--amber)"></i>주의</span>
          <span><i style="background:var(--red)"></i>정비 필요</span>
        </div>
      </div>
      <div class="panel side side-r">
        <div class="card">
          <div class="grouphead"><span class="gtitle">화물 처리</span><span class="gmeta">처리 <b id="tw-kThru">0</b>건</span></div>
          <div class="hubcount" id="tw-hubcount"></div>
        </div>
        <div class="card">
          <span class="label">Storage · 보관 현황</span>
          <div class="stock" id="tw-stock"></div>
          <div class="hint">바닥 칸 = AMR이 꺼내 가는 출고 대기 · 위 단 = 무인 지게차가 다루는 보충 재고</div>
        </div>
        <div class="card">
          <div class="selhead"><span class="label">Cargo · 화물 정보</span><span class="hubchip" id="tw-cHub">-</span></div>
          <div class="cargo-id" id="tw-cId">-</div>
          <dl class="kv">
            <dt>품목</dt><dd id="tw-cCat">-</dd>
            <dt>무게</dt><dd id="tw-cW">-</dd>
            <dt>크기</dt><dd id="tw-cSize">-</dd>
            <dt>취급</dt><dd id="tw-cHandle">-</dd>
            <dt>출고 도크</dt><dd id="tw-cDock">-</dd>
          </dl>
          <div class="loc" id="tw-cLoc">-</div>
          <div class="hint" id="tw-cHint">선택한 AMR이 운반 중인 화물을 따라갑니다. 랙의 박스를 클릭하면 그 화물로 고정됩니다.</div>
        </div>
        <div class="card">
          <span class="label">Inbound Scan · 운송장 OCR</span>
          <ul class="scans" id="tw-scans"></ul>
        </div>
      </div>
    </div>

    <div class="notes" id="tw-notesPanel">
      <div class="notes-head"><h2>S카고 2차 프로젝트 · 허브 AMR 예지보전</h2><button type="button" class="notes-close" id="tw-notesClose" aria-label="설명 닫기">닫기</button></div>
      <div class="notes-intro">
        <p class="sub">운송장으로 목적지를 분류해 권역별로 출고하고, AMR 고장은 미리 감지해 물류 흐름을 멈추지 않는다</p>
        <div class="flow" aria-label="S카고 2차 프로젝트 흐름">
          <span>항만 게이트</span>→<span>톨게이트</span>→<span>과적검문소</span>→<b>허브 하적 · AMR</b>→<span>권역 택배차</span>
        </div>
      </div>
      <div class="card">
        <h2>화물 데이터 항목</h2>
        <div class="tbl"><table>
          <thead><tr><th>항목</th><th>어디에 쓰나</th></tr></thead>
          <tbody>
            <tr class="hl"><td>목적지 권역</td><td>보관 구역과 출고 도크 결정</td></tr>
            <tr><td>무게</td><td>AMR 적재 한도, 누적 운반 중량(마모 판단)</td></tr>
            <tr><td>크기</td><td>랙 칸 배정, 택배차 공간</td></tr>
            <tr><td>취급 속성</td><td>냉장 → 냉장 구역 보관, 파손주의 · 취급주의 · 중량물 → 감속 운반 (박스 스티커로 표시)</td></tr>
            <tr><td>품목</td><td>화면 표시용 (분류에는 사용 안 함)</td></tr>
          </tbody>
        </table></div>
        <p class="foot">입고 시 운송장 OCR로 읽어 들입니다. 1차의 번호판 OCR과 같은 파이프라인입니다.</p>
      </div>
      <div class="card">
        <h2>센서로 잡아내는 고장</h2>
        <div class="tbl"><table>
          <thead><tr><th>센서</th><th>잡아내는 고장</th><th>기준 (데모)</th></tr></thead>
          <tbody>
            <tr class="hl"><td>진동</td><td>구동모터 베어링 · 감속기 마모</td><td>4.5 주의 · 7.1 고장 (mm/s)</td></tr>
            <tr><td>누적 운반 중량</td><td>무거운 화물 반복 → 마모 가속</td><td>베어링 교체 후 누적</td></tr>
            <tr><td>모터 온도</td><td>과부하, 윤활 불량</td><td>70 °C 이상 주의</td></tr>
            <tr><td>배터리</td><td>배터리 열화</td><td>SOH 80% 이하</td></tr>
            <tr><td>라이다</td><td>센서 오염, 위치 인식 오류</td><td>지도 매칭 점수 하락</td></tr>
          </tbody>
        </table></div>
      </div>
      <div class="card">
        <h2>왜 예지보전인가</h2>
        <div class="tbl"><table>
          <thead><tr><th></th><th>흐름</th><th>결과</th></tr></thead>
          <tbody>
            <tr><td>사후 정비</td><td>고장 → 통로 막힘 → 긴급 수리</td><td>라인 정지, 권역 출고 지연</td></tr>
            <tr class="hl"><td>예지보전</td><td>징후 감지 → 예비기 교대 → 계획 정비</td><td>정지 0, 처리량 유지</td></tr>
          </tbody>
        </table></div>
        <p class="foot">수명 예측 시간과 권역명은 데모용 값입니다 (화면 1초 = 실제 약 4시간).</p>
      </div>
    </div>
  </div>
  </div>
</template>

<script setup>
import { onMounted, onBeforeUnmount, ref } from 'vue'
import { mountAmrTwin } from '@/components/amr/amrTwinApp.js'
import '@/components/amr/amrTwin.css'

const root = ref(null)
let app = null

onMounted(() => { app = mountAmrTwin(root.value) })
onBeforeUnmount(() => { if (app) app.destroy() })
</script>
