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
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 26.10.16 AMR 관리자 OTP 인증 (팀 공통 로그인과는 별개 · AMR 화면과 /api/amr/** 만 잠근다)
 *
 *  구글 OTP(TOTP · RFC 6238) 방식
 *   - 서버와 휴대폰이 같은 비밀키를 나눠 갖고, "비밀키 + 현재 시각(30초 단위)" 으로 6자리를 각자 계산해 비교한다
 *   - 숫자가 맞으면 24시간짜리 출입증(토큰)을 주고, 이후 AMR API 요청마다 그 토큰을 확인한다
 *   - 외부 라이브러리 없이 HmacSHA1 로 직접 계산 (Google Authenticator 와 같은 규칙)
 *
 *  안전장치
 *   - 5번 틀리면 1분 잠금 (IP 기준)
 *   - 이미 한 번 쓴 6자리는 다시 못 씀 (같은 30초 안 재사용 방지)
 *   - 토큰 원문은 DB에 저장하지 않고 SHA-256 해시만 저장
 *   - OTP 키를 새로 등록하면 기존 토큰은 전부 무효
 */
@Service
@RequiredArgsConstructor
public class AmrAuthService {

    public static final String ISSUER = "S-BOT AMR";
    public static final Duration TOKEN_TTL = Duration.ofHours(24);
    private static final int STEP_SEC = 30, DIGITS = 6, WINDOW = 1; // 앞뒤 30초까지 허용 (휴대폰 시계 오차)
    private static final int MAX_FAIL = 5;
    private static final Duration LOCK = Duration.ofMinutes(1);
    private static final Duration SETUP_TTL = Duration.ofMinutes(10);

    private final AmrOtpKeyRepository keys;
    private final AmrAuthTokenRepository tokens;
    private final AmrService amr;

    private final SecureRandom rnd = new SecureRandom();
    private final Map<String, Fail> fails = new ConcurrentHashMap<>();
    private volatile long lastUsedStep = -1; // 재사용 방지
    private volatile Pending pending;         // 등록 중인 비밀키 (확인 전에는 DB에 넣지 않음)

    private record Fail(int count, OffsetDateTime lockedUntil) {}
    private record Pending(String secret, String label, String by, OffsetDateTime until) {}

    public record LoginResult(String token, OffsetDateTime expiresAt, String client) {}

    // ── 상태 ─────────────────────────────────────────────
    public boolean registered() { return keys.findFirstByActiveTrueOrderByKeyIdDesc().isPresent(); }

    // ── 등록 (웹 · 팀 ADMIN 로그인 상태에서만) ─────────────────
    public Map<String, Object> startSetup(String adminId) {
        byte[] b = new byte[20]; rnd.nextBytes(b);
        String secret = base32(b);
        String label = adminId == null || adminId.isBlank() ? "admin" : adminId;
        pending = new Pending(secret, label, adminId, OffsetDateTime.now().plus(SETUP_TTL));
        String uri = "otpauth://totp/" + enc(ISSUER) + ":" + enc(label) + "?secret=" + secret + "&issuer=" + enc(ISSUER)
                + "&algorithm=SHA1&digits=" + DIGITS + "&period=" + STEP_SEC;
        return Map.of("secret", secret, "uri", uri, "issuer", ISSUER, "label", label);
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult confirmSetup(String code, String ip) {
        Pending p = pending;
        if (p == null || p.until().isBefore(OffsetDateTime.now()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "등록 시간이 지났습니다. QR을 다시 띄워 주세요");
        checkLock(ip);
        if (match(p.secret(), code) < 0) { failed(ip); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "코드가 맞지 않습니다"); }
        fails.remove(ip);
        OffsetDateTime now = OffsetDateTime.now();
        for (AmrOtpKey k : keys.findByActiveTrue()) { k.setActive(false); k.setRevokedAt(now); }
        tokens.revokeAll();
        AmrOtpKey k = new AmrOtpKey();
        k.setLabel(p.label()); k.setSecret(p.secret()); k.setActive(true); k.setCreatedBy(p.by()); k.setCreatedAt(now);
        keys.save(k);
        pending = null;
        amr.event(null, "INFO", "AMR 관리자 OTP 등록 (" + p.label() + ") · 기존 로그인 모두 해제");
        return issue(k.getKeyId(), "WEB", ip);
    }

    // ── 로그인 (웹 · 앱) ─────────────────────────────────────
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResult login(String code, String client, String ip) {
        AmrOtpKey k = keys.findFirstByActiveTrueOrderByKeyIdDesc()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "OTP가 아직 등록되지 않았습니다. 웹 AMR 화면에서 먼저 등록하세요"));
        checkLock(ip);
        long step = match(k.getSecret(), code);
        if (step < 0) { failed(ip); throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "코드가 맞지 않습니다 (" + left(ip) + "번 남음)"); }
        if (step <= lastUsedStep) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "이미 사용한 코드입니다. 다음 숫자를 기다려 주세요");
        lastUsedStep = step;
        fails.remove(ip);
        return issue(k.getKeyId(), "APP".equalsIgnoreCase(client) ? "APP" : "WEB", ip);
    }

    // ── 토큰 확인 (모든 AMR API 요청마다) ───────────────────────
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
            amr.event(null, "WARN", "AMR OTP " + MAX_FAIL + "회 실패 · 1분 잠금 (" + ip + ")");
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

    private static String enc(String s) { return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"); }
}
