package com.scargo.amr.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 26.10.22 AMR 백그라운드 시뮬레이터
 *  - AMR 관제 화면을 열어야만 amr_telemetry / amr_task_logs 가 쌓여서, S-Bot 대시보드가 그 화면을 열 때만 바뀌던 문제를 없앤다.
 *  - 서버가 1.5초마다 활성 AMR(AMR·지게차)의 상태를 기록하고, 한 사이클마다 운반 1건(amr_task_logs)을 남긴다.
 *  - 충전은 관리자가 허브 관제에서 정한 기준(amr_settings.charge_at_pct) 이하일 때만 하고, hub_events 에 남긴다.
 *  - 고장 · 정비 · 예비기(AMR-05) 투입은 허브 관제의 승인 흐름이 정한다(여기서는 만들지 않음). 정비 중 장비는 건드리지 않고,
 *    AMR-05 는 관제에서 투입(ACTIVE)된 동안에만 같이 돈다.
 *  - AMR 관제 화면이 열려 있어 최근 15초 안에 센서 기록이 들어왔으면 아무것도 하지 않는다(화면이 우선, 서로 겹치지 않음).
 *  - 끄려면 application.properties 에 sbot.amr.bgsim=false
 *  - 테스트용 빠른 모드: sbot.amr.bgsim.interval=750 (기본 1500ms)
 */
@Component
@EnableScheduling
@ConditionalOnProperty(name = "sbot.amr.bgsim", havingValue = "true", matchIfMissing = true)
public class AmrBackgroundSim {

    // 한 사이클(7틱 · 약 10초): 집으로 이동 → 적재 → 하역 위치 이동 → 하역 → 대기 (가동 6/7)
    private static final String[] CYCLE = {"TO_PICK", "TO_PICK", "LOADING", "TO_DROP", "TO_DROP", "UNLOADING", "IDLE"};
    private static final String[] DEST = {"서울", "경기", "인천"};

    private static final Logger log = LoggerFactory.getLogger(AmrBackgroundSim.class);
    private int fails = 0;
    /** 대시보드 배속(1·2·4·8·16). POST /api/amr/sbot/speed 로 바꾼다 (재시작하면 1배). */
    public static volatile int speed = 1;

    @PersistenceContext
    private EntityManager em;
    private final TransactionTemplate tx;
    private final AmrService amr;
    private final Map<String, Integer> step = new HashMap<>();
    private final Random rnd = new Random();
    private final java.util.Set<String> charging = java.util.concurrent.ConcurrentHashMap.newKeySet(); // 지금 충전 중인 로봇 코드
    private boolean charging(String code) { return charging.contains(code); }
    private void startCharging(String code) { charging.add(code); }
    private void stopCharging(String code) { charging.remove(code); }
    private long seq = 0;

    // 충전·고장·예비기 투입 기록 (hub_events: 관제 이벤트 로그 · 대시보드 집계용)
    private void event(Long hub, long robotId, String level, String msg) {
        em.createNativeQuery("INSERT INTO hub_events (hub_id, robot_id, event_level, message) VALUES (:h, :r, :l, :m)")
                .setParameter("h", hub).setParameter("r", robotId).setParameter("l", level).setParameter("m", msg).executeUpdate();
    }

    public AmrBackgroundSim(PlatformTransactionManager tm, AmrService amr) {
        this.tx = new TransactionTemplate(tm);
        this.amr = amr;
    }

    @Scheduled(initialDelay = 20000, fixedDelayString = "${sbot.amr.bgsim.interval:1500}")
    public void run() {
        try {
            for (int k = 0; k < speed; k++) tx.executeWithoutResult(s -> tick()); // 배속만큼 한 번에 여러 틱 (틱마다 따로 커밋해서 시각이 겹치지 않게)
        } catch (Exception e) {
            // 시연용 보조 기능: 실패해도 다음 주기에 다시 시도한다 (원인은 처음 한 번과 이후 20번에 한 번 로그로 남긴다)
            if (fails++ % 20 == 0) log.warn("[AMR 백그라운드 시뮬레이터] 기록 실패: {}", String.valueOf(e.getMessage()), e);
        }
    }

    private void tick() {
        Object fresh = em.createNativeQuery(
                "SELECT CASE WHEN max(recorded_at) > now() - interval '15 seconds' THEN 1 ELSE 0 END FROM amr_telemetry").getSingleResult();
        boolean screenLive = fresh instanceof Number n && n.intValue() == 1; // AMR 관제 화면이 센서를 기록 중이면 센서/상태는 화면 것을 그대로 두고, 운반 건수만 서버가 채운다

        Long hub = amr.internalDefaultHubId(); // 26.10.22 통합: 스케줄러에는 로그인 사용자가 없어 권한 확인 없는 내부 메서드 사용
        @SuppressWarnings("unchecked")
        List<Object[]> robots = em.createNativeQuery(
                "SELECT robot_id, robot_code, robot_type, battery_pct, health_score, state FROM amr_robots"
                + " WHERE hub_id = :h AND robot_role = 'ACTIVE' ORDER BY robot_code").setParameter("h", hub).getResultList();

        // 충전 기준은 관리자가 허브 관제에서 정한 값(amr_settings)을 그대로 따른다. 정비·예비기(AMR-05) 투입은 관제 화면의 승인 흐름이 정하고, 여기서는 건드리지 않는다.
        var sv = amr.internalSettings(hub);
        int chargeAt = sv.chargeAtPct() == null ? 30 : sv.chargeAtPct();
        for (Object[] r : robots) {
            long id = ((Number) r[0]).longValue();
            String code = String.valueOf(r[1]);
            boolean fork = "FORKLIFT".equals(String.valueOf(r[2]));
            double bat = r[3] == null ? 80 : ((Number) r[3]).doubleValue();
            double health = r[4] == null ? 95 : ((Number) r[4]).doubleValue();
            String dbState = String.valueOf(r[5]);
            if ("MAINT".equals(dbState) || "TO_MAINT".equals(dbState)) continue; // 관리자가 정비 보낸 장비는 그대로 둔다

            String st;
            boolean busy;
            // 충전은 "목표 배터리(기준+20%, 최소 90%)"에 닿으면 끝난다 (고정 틱 수가 아님 → 기준을 높게 잡아도 끝없이 이어붙지 않는다)
            double chargeTarget = Math.min(99, Math.max(90, chargeAt + 20));
            boolean charging = charging(code);
            if (charging && bat < chargeTarget) {           // 충전 중 (한 틱에 12% 씩 올라가 금방 끝난다)
                st = "CHARGING"; busy = false; bat = Math.min(100, bat + 12);
                if (bat >= chargeTarget && !screenLive) { stopCharging(code); event(hub, id, "OK", code + " 충전 완료 (배터리 " + Math.round(bat) + "%)"); }
            } else if (charging) {                          // 목표 배터리 도달 → 충전 종료
                stopCharging(code); st = "IDLE"; busy = false;
            } else if (!screenLive && bat <= chargeAt) {    // 관리자 기준 이하 → 충전소 이동
                startCharging(code); st = "CHARGING"; busy = false;
                event(hub, id, "INFO", code + " 배터리 " + Math.round(bat) + "% · 기준 " + chargeAt + "% 이하 → 충전소 이동");
            } else {                                       // 정상 가동
                int cur = step.computeIfAbsent(code, k -> rnd.nextInt(CYCLE.length));
                int i0 = (cur + 1) % CYCLE.length;
                step.put(code, i0);
                st = fork ? (i0 < 4 ? "FORK_WORK" : "IDLE") : CYCLE[i0];
                busy = !"IDLE".equals(st);
                if (!screenLive && busy) bat -= 0.5;
            }
            int i = step.getOrDefault(code, 0);

            if (!screenLive) {
                double speed = ("TO_PICK".equals(st) || "TO_DROP".equals(st)) ? 1.1 + rnd.nextDouble() * 0.4 : 0;
                em.createNativeQuery("INSERT INTO amr_telemetry (robot_id, vibration_mm_s, motor_temp_c, battery_pct, health_score, speed_m_s, state, recorded_at)"
                        + " VALUES (:id, :v, :t, :b, :hs, :sp, :st, now())")
                        .setParameter("id", id).setParameter("v", 1.0 + rnd.nextDouble() * 1.5).setParameter("t", 45.0 + rnd.nextDouble() * 12)
                        .setParameter("b", bat).setParameter("hs", health).setParameter("sp", speed).setParameter("st", st).executeUpdate();
                em.createNativeQuery("UPDATE amr_robots SET state = :st, battery_pct = :b, updated_at = now() WHERE robot_id = :id")
                        .setParameter("st", st).setParameter("b", bat).setParameter("id", id).executeUpdate();
            }

            if (busy && ((!fork && "UNLOADING".equals(st)) || (fork && i == 3))) { // 하역(지게차는 적치/보충 끝)이 끝나면 운반 1건 기록
                double dur = 15 + rnd.nextDouble() * 10;
                double wait = rnd.nextInt(4) == 0 ? 1 + rnd.nextDouble() * 3 : 0;
                boolean in = rnd.nextBoolean();
                em.createNativeQuery("INSERT INTO amr_task_logs (hub_id, robot_code, task_type, waybill_no, dest_hub, handle_type, weight_kg,"
                        + " from_loc, to_loc, duration_sec, inter_wait_sec, started_at, finished_at)"
                        + " VALUES (:h, :c, :tt, :w, :d, :ht, :wt, :fl, :tl, :du, :iw, now() - (:du * interval '1 second'), now())")
                        .setParameter("h", hub).setParameter("c", code).setParameter("tt", in ? "IN" : "OUT")
                        .setParameter("w", "BG" + System.currentTimeMillis() + (seq++ % 100))
                        .setParameter("d", DEST[rnd.nextInt(DEST.length)]).setParameter("ht", "일반")
                        .setParameter("wt", 3.0 + rnd.nextDouble() * 12).setParameter("fl", fork ? (in ? "바닥 칸" : "상단 랙") : (in ? "입고도크" : "랙")).setParameter("tl", fork ? (in ? "상단 랙" : "바닥 칸") : (in ? "랙" : "출고도크"))
                        .setParameter("du", dur).setParameter("iw", wait).executeUpdate();
            }
        }
    }
}
