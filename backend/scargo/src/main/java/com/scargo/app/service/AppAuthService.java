package com.scargo.app.service;

import com.scargo.app.dto.AppDtos.*;
import com.scargo.app.entity.AppToken;
import com.scargo.app.entity.VehicleSpec;
import com.scargo.app.repository.AppTokenRepository;
import com.scargo.app.repository.VehicleSpecRepository;
import com.scargo.dto.AccountResponse;
import com.scargo.entity.Account;
import com.scargo.entity.Truck;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import com.scargo.repository.TruckRepository;
import com.scargo.service.AccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;

/** 26.10.03 2차: 기사 앱 로그인/로그아웃, 내 정보(배정 차량 + 제원) */
@Service
@RequiredArgsConstructor
public class AppAuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final AccountService accountService;          // 1차 로그인 로직(아이디/비밀번호 검증) 재사용
    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final TruckRepository truckRepository;
    private final VehicleSpecRepository vehicleSpecRepository;
    private final AppTokenRepository appTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Value("${scargo.app.token-days:30}")
    private int tokenDays;

    @Transactional
    public LoginResponse login(LoginRequest request) {
        com.scargo.dto.LoginRequest legacy = new com.scargo.dto.LoginRequest();
        legacy.setUserId(request.userId());
        legacy.setUserPw(request.userPw());
        AccountResponse account = accountService.login(legacy); // 틀리면 IllegalArgumentException → 400

        appTokenRepository.deleteExpired(OffsetDateTime.now());

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        AppToken token = new AppToken();
        token.setToken(HexFormat.of().formatHex(bytes));
        token.setAccountId(account.getAccountId());
        token.setExpiresAt(OffsetDateTime.now().plusDays(tokenDays));
        appTokenRepository.save(token);

        return new LoginResponse(token.getToken(), token.getExpiresAt(), me(account.getAccountId()));
    }

    @Transactional
    public void logout(String authorizationHeader) {
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            appTokenRepository.deleteById(authorizationHeader.substring(7).trim());
        }
    }

    @Transactional(readOnly = true)
    public MeResponse me(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
        String companyName = account.getCompanyId() == null ? null
                : companyRepository.findById(account.getCompanyId()).map(c -> c.getCompanyName()).orElse(null);
        TruckInfo truck = truckRepository.findByAssignedDriver_AccountId(accountId).map(this::truckInfo).orElse(null);
        return new MeResponse(account.getAccountId(), account.getUserId(), account.getUserName(), account.getPhoneNum(),
                account.getCompanyId(), companyName, account.getUserType().name(), truck);
    }

    private TruckInfo truckInfo(Truck truck) {
        SpecInfo spec = vehicleSpecRepository.findById(truck.getVehicleNo()).map(AppAuthService::specInfo).orElse(null);
        return new TruckInfo(truck.getVehicleNo(), truck.getTrailerNo(), truck.isSemiTrailer(), truck.getTruckType(),
                truck.getMaxLoadWeight(), truck.getStatus(), spec);
    }

    static SpecInfo specInfo(VehicleSpec s) {
        return new SpecInfo(s.getUsagePurpose(), s.getEmptyWeightKg(), s.getGrossWeightKg(), s.getMaxPayloadKg(),
                s.getAxleCount(), s.getTireCount(), s.getChassisHeightM());
    }
    /** 기사 앱 비밀번호 변경: 현재 비밀번호 확인 후 새 비밀번호 저장. 성공 시 다른 기기 토큰은 유지(요청 토큰만 유지해도 됨) */
    @Transactional
    public void changePassword(Long accountId, String currentPw, String newPw) {
        if (currentPw == null || currentPw.isBlank()) throw new IllegalArgumentException("현재 비밀번호를 입력하세요.");
        if (newPw == null || newPw.isBlank()) throw new IllegalArgumentException("새 비밀번호를 입력하세요.");
        if (newPw.length() < 4) throw new IllegalArgumentException("새 비밀번호는 4자 이상이어야 합니다.");
        if (currentPw.equals(newPw)) throw new IllegalArgumentException("새 비밀번호가 현재 비밀번호와 같습니다.");
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다."));
        if (!passwordEncoder.matches(currentPw, account.getUserPw()))
            throw new IllegalArgumentException("현재 비밀번호가 올바르지 않습니다.");
        account.setUserPw(passwordEncoder.encode(newPw));
        accountRepository.save(account);
    }
}
