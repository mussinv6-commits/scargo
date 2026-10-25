package com.scargo.amr.service;

import com.scargo.amr.entity.AmrAuthToken;
import com.scargo.amr.entity.AmrOtpKey;
import com.scargo.amr.repository.AmrAuthTokenRepository;
import com.scargo.amr.repository.AmrOtpKeyRepository;
import com.scargo.amr.security.AmrAccessPolicy;
import com.scargo.amr.security.AmrDevicePrincipal;
import com.scargo.security.AuthenticatedAccountPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 26.10.16 AMR 관리자 앱 인증 (앱 연결 코드 · OTP)
 *
 *  흐름
 *   - 웹 AMR 관제 화면(팀 ADMIN 로그인 상태)에 30초마다 바뀌는 6자리 "앱 연결 코드"와 QR을 보여 준다
 *   - 관리자 앱은 QR을 찍거나 6자리를 입력 → 서버가 확인 → 24시간짜리 출입증(토큰) 발급
 *   - QR에는 노트북 서버 주소도 들어 있어서, 노트북이 바뀌어도 앱에서 주소를 따로 입력할 필요가 없다
 *   - 앱의 모든 AMR API 요청은 토큰을 확인한다 (웹은 팀 관리자 로그인 세션으로 통과)
 *
 *  26.10.22 허브별 키
 *   - 앱 연결 코드는 ADMIN(전체 허브 키, hub_id NULL)과 HUB_OP(소속 허브 키)만 볼 수 있다. CONTROL은 조회 전용이라 발급 불가
 *   - 앱 출입증의 범위 = 그 코드를 만든 키의 허브 (AmrDevicePrincipal.hubId)
 *   - HUB_OP의 "모든 앱 로그아웃"은 자기 허브 키 · 출입증만, ADMIN은 기존처럼 전부
 *
 *  6자리 계산은 TOTP(RFC 6238) 규칙: 서버만 아는 비밀키 + 현재 시각(30초 단위) → HmacSHA1 → 6자리
 *
 *  안전장치
 *   - 5번 틀리면 1분 잠금 (IP 기준)
 *   - 이미 한 번 쓴 6자리는 다시 못 씀
 *   - 토큰 원문은 DB에 저장하지 않고 SHA-256 해시만 저장
 *   - "모든 앱 로그아웃" 하면 비밀키를 새로 만들고 기존 토큰 전부 무효
 *
 *  26.10.23 출입증 감사 (마이그레이션 14장 = 팀 DB v16 10장)
 *   - 발급: 그 키의 코드를 마지막으로 화면에 띄운 웹 계정(없으면 키를 만든 계정)을 issued_by_* 에, 접속 기기를 user_agent 에 남김
 *   - 폐기: 앱 로그아웃 LOGOUT, 관리자 "모든 앱 로그아웃" ADMIN_REVOKE_ALL, 허브 관제사 "모든 앱 로그아웃" ROTATE (revoked_at · revoked_by)
 *   - 실패: amr_auth_failures 에 NO_KEY · BAD_CODE · REUSED_CODE · IP_LOCKED (입력한 코드 · 비밀키 · 토큰은 저장하지 않음)
 *  26.10.23 감사 보존 (마이그레이션 15장)
 *   - 발급 · 폐기 값은 DB 트리거가 amr_auth_token_audits 에 옮겨 적는다 (토큰 해시는 옮기지 않음)
 *   - 만료 출입증 정리(deleteExpired)는 그대로 amr_auth_tokens 행만 지움 → 감사 이력은 남음. 보관 기간은 미정(별도 승인)
 */
@Service
@RequiredArgsConstructor
public class AmrAuthService {

    public static final Duration TOKEN_TTL = Duration.ofHours(24);
    public static final int STEP_SEC = 30;
    private static final int DIGITS = 6, WINDOW = 1; // 앞뒤 30초까지 허용 (입력하는 사이 코드가 바뀌어도 통과)
    private static final int MAX_FAIL = 5;
    private static final Duration LOCK = Duration.ofMinutes(1);

    private final AmrOtpKeyRepository keys;
    private final AmrAuthTokenRepository tokens;
    private final AmrService amr;
    private final AmrAccessPolicy access;
    private final JdbcTemplate jdbc;

    private final SecureRandom rnd = new SecureRandom();
    private final Map<String, Fail> fails = new ConcurrentHashMap<>();
    private final Map<Long, Long> lastUsedStep = new ConcurrentHashMap<>(); // 키별 마지막 사용 구간 (재사용 방지)
    private final Map<Long, Issuer> lastShownBy = new ConcurrentHashMap<>(); // 26.10.23 키별로 코드를 마지막에 화면에 띄운 웹 계정

    private record Issuer(Long accountId, String userId) {}

    private record Fail(int count, OffsetDateTime lockedUntil) {}

    public record LoginResult(String token, OffsetDateTime expiresAt, String client) {}

    // ── 웹: 지금 앱 연결 코드 (+ 휴대폰이 접속할 노트북 주소) ─────────
    @Transactional
    public Map<String, Object> currentCode(int port) {
        Long hub = access.issuerHubId();
        AmrOtpKey k = activeKey(hub, access.actorName());
        Issuer viewer = webIssuer();
        if (viewer != null) lastShownBy.put(k.getKeyId(), viewer);
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> m = new HashMap<>();
        m.put("code", totp(unbase32(k.getSecret()), now / STEP_SEC));
        m.put("secondsLeft", STEP_SEC - (now % STEP_SEC));
        m.put("period", STEP_SEC);
        String ip = lanIp();
        m.put("lanBase", ip == null ? null : "http://" + ip + ":" + port);
        m.put("hubId", hub); // null = 전체 허브용 코드
        return m;
    }

    // ── 웹: 모든 앱 로그아웃 (비밀키 새로 만들기) ─────────────────
    @Transactional
    public void rotate() {
        Long hub = access.issuerHubId();
        String by = access.actorName();
        OffsetDateTime now = OffsetDateTime.now();
        List<AmrOtpKey> old = hub == null ? keys.findByActiveTrue() : keys.findByActiveTrueAndHubId(hub);
        for (AmrOtpKey k : old) { k.setActive(false); k.setRevokedAt(now); }
        // 26.10.23 폐기 감사: 무효로 만들 출입증에 폐기 시각 · 처리자 · 사유를 먼저 남긴다
        if (hub == null) {
            jdbc.update("UPDATE amr_auth_tokens SET revoked_at = ?, revoked_by = ?, revoke_reason = 'ADMIN_REVOKE_ALL' WHERE revoked = false", now, by);
            tokens.revokeAll(); // 관리자: 모든 허브의 앱 로그아웃 (기존 동작)
        } else if (!old.isEmpty()) {
            for (AmrOtpKey k : old) {
                jdbc.update("UPDATE amr_auth_tokens SET revoked_at = ?, revoked_by = ?, revoke_reason = 'ROTATE' WHERE revoked = false AND key_id = ?", now, by, k.getKeyId());
            }
            tokens.revokeByKeyIds(old.stream().map(AmrOtpKey::getKeyId).toList());
        }
        newKey(hub, by);
        Long eventHub = hub == null ? access.defaultHubId() : hub;
        amr.event(eventHub, null, "INFO", "AMR 앱 연결 모두 해제 (" + by + ") · 앱은 새 코드로 다시 로그인");
    }

    // ── 앱: 6자리 → 24시간 토큰 ───────────────────────────────
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String code, String client, String ip) {
        return login(code, client, ip, null);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String code, String client, String ip, String userAgent) {
        List<AmrOtpKey> active = keys.findByActiveTrueOrderByKeyIdDesc();
        if (active.isEmpty()) {
            auditFailure(ip, userAgent, "NO_KEY");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "웹 AMR 관제 화면을 먼저 열어 앱 연결 코드를 확인하세요");
        }
        try {
            checkLock(ip);
        } catch (ResponseStatusException e) {
            auditFailure(ip, userAgent, "IP_LOCKED");
            throw e;
        }
        // 어느 허브의 코드인지는 맞는 키로 정해진다 (관리자 키 = 전체 허브, 허브 관제사 키 = 그 허브)
        AmrOtpKey k = null; long step = -1;
        for (AmrOtpKey candidate : active) {
            step = match(candidate.getSecret(), code);
            if (step >= 0) { k = candidate; break; }
        }
        if (k == null) { auditFailure(ip, userAgent, "BAD_CODE"); failed(ip); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "코드가 맞지 않습니다 (" + left(ip) + "번 남음)"); }
        if (step <= lastUsedStep.getOrDefault(k.getKeyId(), -1L)) { auditFailure(ip, userAgent, "REUSED_CODE"); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이미 사용한 코드입니다. 웹에서 다음 코드를 확인하세요"); }
        lastUsedStep.put(k.getKeyId(), step);
        fails.remove(ip);
        return issue(k, "WEB".equalsIgnoreCase(client) ? "WEB" : "APP", ip, userAgent);
    }

    // ── 토큰 확인 (앱의 모든 AMR API 요청마다) ────────────────────
    @Transactional
    public AmrAuthToken check(String raw) {
        if (raw == null || raw.isBlank()) return null;
        AmrAuthToken t = tokens.findById(sha256(raw.trim())).orElse(null);
        if (t == null || t.isRevoked() || t.getExpiresAt().isBefore(OffsetDateTime.now())) return null;
        OffsetDateTime now = OffsetDateTime.now();
        if (t.getLastUsedAt() == null || t.getLastUsedAt().isBefore(now.minusMinutes(1))) t.setLastUsedAt(now); // 1분에 한 번만 갱신
        return t;
    }

    /** 26.10.22 출입증 → 허브 앱 인증 주체 (출입증이 없거나 만료 · 무효면 null) */
    @Transactional
    public AmrDevicePrincipal authenticate(String raw) {
        AmrAuthToken t = check(raw);
        if (t == null) return null;
        return keys.findById(t.getKeyId())
                .map(k -> new AmrDevicePrincipal(k.getKeyId(), k.getHubId(), t.getClient()))
                .orElse(null);
    }

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        tokens.findById(sha256(raw.trim())).ifPresent((t) -> {
            if (t.isRevoked()) return;
            t.setRevoked(true);
            t.setRevokedAt(OffsetDateTime.now());
            t.setRevokedBy(t.getClient()); // 앱 · 웹 출입증 스스로 로그아웃
            t.setRevokeReason("LOGOUT");
        });
    }

    // ── 26.10.23 출입증 감사 조회 ──────────────────────────
    @Transactional(readOnly = true)
    public Map<String, Object> audit() {
        Long hub = access.issuerHubId(); // ADMIN = null(전체), HUB_OP = 소속 허브, 그 외 403
        // 26.10.23 마이그레이션 15장: 감사 이력은 amr_auth_token_audits 에서 읽는다 (만료 출입증이 정리돼도 남음)
        //  마지막 사용 시각은 출입증이 남아 있을 때만 (tokenRetained = false 면 만료 후 정리된 출입증)
        String sql = "SELECT a.hub_id AS \"hubId\", a.client, a.ip, a.user_agent AS \"userAgent\", "
                + "a.issued_by_user_id AS \"issuedByUserId\", a.issued_at AS \"createdAt\", a.expires_at AS \"expiresAt\", "
                + "t.last_used_at AS \"lastUsedAt\", a.revoked, a.revoked_at AS \"revokedAt\", a.revoked_by AS \"revokedBy\", "
                + "a.revoke_reason AS \"revokeReason\", (t.audit_id IS NOT NULL) AS \"tokenRetained\" "
                + "FROM amr_auth_token_audits a LEFT JOIN amr_auth_tokens t ON t.audit_id = a.audit_id "
                + (hub == null ? "" : "WHERE a.hub_id = ? ") + "ORDER BY a.issued_at DESC, a.audit_id DESC LIMIT 100";
        Map<String, Object> m = new HashMap<>();
        m.put("tokens", hub == null ? jdbc.queryForList(sql) : jdbc.queryForList(sql, hub));
        // 실패 기록에는 허브 정보가 없어 전체 범위(ADMIN)만 본다
        m.put("failures", hub == null ? jdbc.queryForList("SELECT occurred_at AS \"occurredAt\", ip, user_agent AS \"userAgent\", reason "
                + "FROM amr_auth_failures ORDER BY occurred_at DESC LIMIT 100") : List.of());
        m.put("hubId", hub);
        return m;
    }

    // ── 내부 ─────────────────────────────────────────────
    private AmrOtpKey activeKey(Long hub, String by) {
        return (hub == null ? keys.findFirstByActiveTrueAndHubIdIsNullOrderByKeyIdDesc()
                : keys.findFirstByActiveTrueAndHubIdOrderByKeyIdDesc(hub))
                .orElseGet(() -> newKey(hub, by));
    }

    private AmrOtpKey newKey(Long hub, String adminId) {
        byte[] b = new byte[20]; rnd.nextBytes(b);
        AmrOtpKey k = new AmrOtpKey();
        k.setLabel("app"); k.setSecret(base32(b)); k.setActive(true); k.setCreatedBy(adminId); k.setCreatedAt(OffsetDateTime.now()); k.setHubId(hub);
        return keys.save(k);
    }

    /** 노트북의 와이파이(사설망) IPv4 — 가상 어댑터(VirtualBox · VMware · WSL 등)는 건너뜀 */
    static String lanIp() {
        try {
            String best = null;
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                String name = (ni.getName() + " " + ni.getDisplayName()).toLowerCase();
                if (name.matches(".*(virtual|vmware|vbox|hyper-v|vethernet|wsl|docker|loopback|bluetooth|tap|tun).*")) continue;
                for (InetAddress a : Collections.list(ni.getInetAddresses())) {
                    if (!(a instanceof Inet4Address) || !a.isSiteLocalAddress()) continue;
                    String ip = a.getHostAddress();
                    if (name.contains("wi-fi") || name.contains("wlan") || name.contains("wireless")) return ip; // 와이파이 우선
                    if (best == null) best = ip;
                }
            }
            return best;
        } catch (Exception e) { return null; }
    }

    private LoginResult issue(AmrOtpKey k, String client, String ip, String userAgent) {
        Long keyId = k.getKeyId();
        Issuer issuer = lastShownBy.get(keyId);
        byte[] b = new byte[32]; rnd.nextBytes(b);
        String raw = HexFormat.of().formatHex(b);
        OffsetDateTime now = OffsetDateTime.now();
        tokens.deleteExpired(now.minusDays(1)); // 하루 넘게 지난 출입증 정리 (감사 이력 amr_auth_token_audits 는 지우지 않음)
        AmrAuthToken t = new AmrAuthToken();
        t.setTokenHash(sha256(raw)); t.setClient(client); t.setKeyId(keyId); t.setIp(ip);
        t.setCreatedAt(now); t.setExpiresAt(now.plus(TOKEN_TTL)); t.setLastUsedAt(now); t.setRevoked(false);
        // 26.10.23 발급 감사: 코드를 보여 준 웹 계정 (서버 재시작 등으로 모르면 키를 만든 계정 아이디)
        t.setIssuedByAccountId(issuer == null ? null : issuer.accountId());
        t.setIssuedByUserId(issuer == null ? k.getCreatedBy() : issuer.userId());
        t.setUserAgent(trim(userAgent, 300));
        tokens.save(t);
        return new LoginResult(raw, t.getExpiresAt(), client);
    }

    // 26.10.23 실패 감사 기록. 입력한 코드는 저장하지 않는다. 기록 실패가 로그인 응답을 바꾸지 않게 예외는 삼킨다
    private void auditFailure(String ip, String userAgent, String reason) {
        try {
            jdbc.update("INSERT INTO amr_auth_failures (ip, user_agent, reason) VALUES (?, ?, ?)", trim(ip, 60), trim(userAgent, 300), reason);
        } catch (Exception ignored) { /* 감사 기록 실패는 무시 */ }
    }

    private static Issuer webIssuer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getPrincipal() instanceof AuthenticatedAccountPrincipal p ? new Issuer(p.getId(), p.getUserId()) : null;
    }

    private static String trim(String v, int max) {
        return v == null ? null : v.length() <= max ? v : v.substring(0, max);
    }

    private void checkLock(String ip) {
        Fail f = fails.get(ip);
        if (f != null && f.lockedUntil() != null) {
            long sec = Duration.between(OffsetDateTime.now(), f.lockedUntil()).toSeconds();
            if (sec > 0) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MAX_FAIL + "번 틀려 잠겼습니다. " + (sec + 1) + "초 뒤 다시 시도하세요");
            fails.remove(ip);
        }
    }

    private void failed(String ip) {
        Fail f = fails.getOrDefault(ip, new Fail(0, null));
        int c = f.count() + 1;
        if (c >= MAX_FAIL) {
            fails.put(ip, new Fail(0, OffsetDateTime.now().plus(LOCK)));
            amr.event(access.defaultHubId(), null, "WARN", "AMR 앱 로그인 " + MAX_FAIL + "회 실패 · 1분 잠금 (" + ip + ")");
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, MAX_FAIL + "번 틀려 1분 동안 잠깁니다");
        }
        fails.put(ip, new Fail(c, null));
    }

    private int left(String ip) { Fail f = fails.get(ip); return MAX_FAIL - (f == null ? 0 : f.count()); }

    /** 맞으면 그 30초 구간 번호, 틀리면 -1 */
    long match(String secret, String code) {
        if (code == null) return -1;
        String c = code.replaceAll("\\s", "");
        if (!c.matches("\\d{" + DIGITS + "}")) return -1;
        byte[] key = unbase32(secret);
        long now = System.currentTimeMillis() / 1000 / STEP_SEC;
        for (int d = -WINDOW; d <= WINDOW; d++) {
            if (MessageDigest.isEqual(totp(key, now + d).getBytes(StandardCharsets.US_ASCII), c.getBytes(StandardCharsets.US_ASCII))) return now + d;
        }
        return -1;
    }

    static String totp(byte[] key, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] h = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int o = h[h.length - 1] & 0x0f;
            int bin = ((h[o] & 0x7f) << 24) | ((h[o + 1] & 0xff) << 16) | ((h[o + 2] & 0xff) << 8) | (h[o + 3] & 0xff);
            return String.format("%0" + DIGITS + "d", bin % 1_000_000);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static final String B32 = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    static String base32(byte[] data) {
        StringBuilder sb = new StringBuilder(); int buf = 0, bits = 0;
        for (byte b : data) { buf = (buf << 8) | (b & 0xff); bits += 8; while (bits >= 5) { sb.append(B32.charAt((buf >> (bits - 5)) & 31)); bits -= 5; } }
        if (bits > 0) sb.append(B32.charAt((buf << (5 - bits)) & 31));
        return sb.toString();
    }

    static byte[] unbase32(String s) {
        String t = s.replace("=", "").replace(" ", "").toUpperCase();
        ByteBuffer out = ByteBuffer.allocate(t.length() * 5 / 8); int buf = 0, bits = 0;
        for (char ch : t.toCharArray()) { int v = B32.indexOf(ch); if (v < 0) continue; buf = (buf << 5) | v; bits += 5; if (bits >= 8) { out.put((byte) ((buf >> (bits - 8)) & 0xff)); bits -= 8; } }
        return java.util.Arrays.copyOf(out.array(), out.position());
    }

    static String sha256(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
