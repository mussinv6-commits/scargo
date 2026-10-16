package com.scargo.amr.service;

import com.scargo.amr.entity.AmrAuthToken;
import com.scargo.amr.entity.AmrOtpKey;
import com.scargo.amr.repository.AmrAuthTokenRepository;
import com.scargo.amr.repository.AmrOtpKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
 *  6자리 계산은 TOTP(RFC 6238) 규칙: 서버만 아는 비밀키 + 현재 시각(30초 단위) → HmacSHA1 → 6자리
 *
 *  안전장치
 *   - 5번 틀리면 1분 잠금 (IP 기준)
 *   - 이미 한 번 쓴 6자리는 다시 못 씀
 *   - 토큰 원문은 DB에 저장하지 않고 SHA-256 해시만 저장
 *   - "모든 앱 로그아웃" 하면 비밀키를 새로 만들고 기존 토큰 전부 무효
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

    private final SecureRandom rnd = new SecureRandom();
    private final Map<String, Fail> fails = new ConcurrentHashMap<>();
    private volatile long lastUsedStep = -1; // 재사용 방지

    private record Fail(int count, OffsetDateTime lockedUntil) {}

    public record LoginResult(String token, OffsetDateTime expiresAt, String client) {}

    // ── 웹: 지금 앱 연결 코드 (+ 휴대폰이 접속할 노트북 주소) ─────────
    @Transactional
    public Map<String, Object> currentCode(String adminId, int port) {
        AmrOtpKey k = activeKey(adminId);
        long now = System.currentTimeMillis() / 1000;
        Map<String, Object> m = new HashMap<>();
        m.put("code", totp(unbase32(k.getSecret()), now / STEP_SEC));
        m.put("secondsLeft", STEP_SEC - (now % STEP_SEC));
        m.put("period", STEP_SEC);
        String ip = lanIp();
        m.put("lanBase", ip == null ? null : "http://" + ip + ":" + port);
        return m;
    }

    // ── 웹: 모든 앱 로그아웃 (비밀키 새로 만들기) ─────────────────
    @Transactional
    public void rotate(String adminId) {
        OffsetDateTime now = OffsetDateTime.now();
        for (AmrOtpKey k : keys.findByActiveTrue()) { k.setActive(false); k.setRevokedAt(now); }
        tokens.revokeAll();
        newKey(adminId);
        amr.event(null, "INFO", "AMR 앱 연결 모두 해제 (" + adminId + ") · 앱은 새 코드로 다시 로그인");
    }

    // ── 앱: 6자리 → 24시간 토큰 ───────────────────────────────
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String code, String client, String ip) {
        AmrOtpKey k = keys.findFirstByActiveTrueOrderByKeyIdDesc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "웹 AMR 관제 화면을 먼저 열어 앱 연결 코드를 확인하세요"));
        checkLock(ip);
        long step = match(k.getSecret(), code);
        if (step < 0) { failed(ip); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "코드가 맞지 않습니다 (" + left(ip) + "번 남음)"); }
        if (step <= lastUsedStep) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이미 사용한 코드입니다. 웹에서 다음 코드를 확인하세요");
        lastUsedStep = step;
        fails.remove(ip);
        return issue(k.getKeyId(), "WEB".equalsIgnoreCase(client) ? "WEB" : "APP", ip);
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

    @Transactional
    public void logout(String raw) {
        if (raw == null || raw.isBlank()) return;
        tokens.findById(sha256(raw.trim())).ifPresent((t) -> t.setRevoked(true));
    }

    // ── 내부 ─────────────────────────────────────────────
    private AmrOtpKey activeKey(String adminId) {
        return keys.findFirstByActiveTrueOrderByKeyIdDesc().orElseGet(() -> newKey(adminId));
    }

    private AmrOtpKey newKey(String adminId) {
        byte[] b = new byte[20]; rnd.nextBytes(b);
        AmrOtpKey k = new AmrOtpKey();
        k.setLabel("app"); k.setSecret(base32(b)); k.setActive(true); k.setCreatedBy(adminId); k.setCreatedAt(OffsetDateTime.now());
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

    private LoginResult issue(Long keyId, String client, String ip) {
        byte[] b = new byte[32]; rnd.nextBytes(b);
        String raw = HexFormat.of().formatHex(b);
        OffsetDateTime now = OffsetDateTime.now();
        tokens.deleteExpired(now.minusDays(1)); // 하루 넘게 지난 출입증 정리
        AmrAuthToken t = new AmrAuthToken();
        t.setTokenHash(sha256(raw)); t.setClient(client); t.setKeyId(keyId); t.setIp(ip);
        t.setCreatedAt(now); t.setExpiresAt(now.plus(TOKEN_TTL)); t.setLastUsedAt(now); t.setRevoked(false);
        tokens.save(t);
        return new LoginResult(raw, t.getExpiresAt(), client);
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
            amr.event(null, "WARN", "AMR 앱 로그인 " + MAX_FAIL + "회 실패 · 1분 잠금 (" + ip + ")");
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
