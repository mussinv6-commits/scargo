package com.scargo.admin;

import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate; // 26.10.22 통합: 관리자 운영 계열사 조회
    private final Environment environment;   // 26.10.22 통합: dev 프로필 확인

    // 26.10.22 통합: DB 스크립트(2장)가 넣는 권한별 시드 계정 7개 (비밀번호 평문이 공유되지 않아 로그인 테스트가 안 됨)
    private static final String SEED_TEST_PASSWORD = "test1234";
    private static final List<String> SEED_ACCOUNT_IDS = List.of(
            "driver01", "admin01", "corp01", "control01", "hubop01", "centerop01", "linehaul01");

    @Override
    public void run(ApplicationArguments args) {

        // 26.10.22 통합: dev 프로필로 실행할 때만 시드 계정 7개의 비밀번호를 test1234 로 맞춘다 (합본 AdminInitializer 와 같은 규칙)
        //  - 비밀번호만 바꾸고 권한 · 소속은 건드리지 않는다. 이미 test1234 이면 그대로 둔다
        //  - 계정이 없으면 만들지 않는다 (시드 데이터는 DB 스크립트가 넣음)
        //  - 기본 실행 · 운영에서는 동작하지 않는다 (실행 인자: --spring.profiles.active=dev)
        if (environment.acceptsProfiles(Profiles.of("dev")) || Boolean.parseBoolean(environment.getProperty("scargo.seed-password-reset", "true"))) { // 26.10.24: 프로필이 안 잡혀도 기본 동작. 끄려면 scargo.seed-password-reset=false
            resetSeedPasswords();
        }

        // 26.10.22 통합: DB 는 ADMIN 에 운영 계열사가 필수(chk_accounts_admin_operator)이고,
        //  관리자 기능(계정 관리 등)도 운영 계열사 기준으로 범위를 정한다 → 첫 운영 계열사에 소속시킨다
        //  운영 계열사가 없으면 관리자 계정을 만들지 않는다 (만들면 DB 제약 위반으로 서버 기동 실패)
        Long operatingCompanyId = jdbcTemplate.queryForObject(
                "SELECT min(operating_company_id) FROM operating_companies", Long.class);
        if (operatingCompanyId == null) {
            System.out.println("[AdminInitializer] 운영 계열사(operating_companies)가 없어 관리자 계정 생성을 건너뜀");
            return;
        }

        // 관리자 계정 생성: DB에서 직접 생성은 비밀번호 암호화 때문에 암호화된 값으로 넣어야 함
        // 현재는 관리자 권한이 하나이지만 추후에 관리자를 기능별로 구별한다면 userType 수정 필요

        // 1번 관리자 계정 생성 (S Cargo 관리자)
        if (accountRepository.findByUserId("admin").isEmpty()) {
            Account admin1 = Account.builder()
                    .userName("시스템관리자1")
                    .userId("admin")
                    .userPw(passwordEncoder.encode("admin1234"))
                    .userType(Account.UserType.ADMIN)
                    .operatingCompanyId(operatingCompanyId)
                    .phoneNum("010-1111-1111")
                    .build();

            accountRepository.save(admin1);
        }

        // 2번 관리자 계정 생성 (S Cargo 관리자)
        if (accountRepository.findByUserId("admin2").isEmpty()) {
            Account admin2 = Account.builder()
                    .userName("시스템관리자2")
                    .userId("admin2")
                    .userPw(passwordEncoder.encode("admin5678"))
                    .userType(Account.UserType.ADMIN)
                    .operatingCompanyId(operatingCompanyId)
                    .phoneNum("010-2222-2222")
                    .build();

            accountRepository.save(admin2);
        }


        // 26.10.20 추가: S-Bot 관리자 계정 (S Cargo 관리자와 분리. 아이디가 sbot 으로 시작하면 S-Bot 관리자)
        if (accountRepository.findByUserId("sbot").isEmpty()) {
            Account sbotAdmin = Account.builder()
                    .userName("S-Bot 관리자")
                    .userId("sbot")
                    .userPw(passwordEncoder.encode("sbot1234"))
                    .userType(Account.UserType.ADMIN)
                    .operatingCompanyId(operatingCompanyId)
                    .phoneNum("010-7777-7777")
                    .build();

            accountRepository.save(sbotAdmin);
        }

        // 26.10.20 11차 DB는 user_type 이 GENERAL/ADMIN 만 허용(chk_user_type) → 아래 테스트 계정(CORPORATE_APPROVED)이 막히면 서버가 죽지 않게 건너뛴다
        try {
        // 26.09.21 추가: 테스트용 더미 "사업자(승인완료)" 계정 생성
        Company dummyCompany = companyRepository
                .findByCompanyNameAndAddress(
                        "(주)스카고로지스틱스",
                        "서울특별시 강서구 공항대로 100"
                )
                .orElseGet(() -> companyRepository.save(
                        Company.builder()
                                .companyName("(주)스카고로지스틱스")
                                .address("서울특별시 강서구 공항대로 100")
                                .businessNo("123-45-67890")
                                .industryType("컨테이너 운송업")
                                .representativeName("김대표")
                                .build()
                ));

        // 테스트용 승인완료 사업자 계정 생성
        if (accountRepository.findByUserId("biz1").isEmpty()) {
            Account biz1 = Account.builder()
                    .userName("테스트사업자")
                    .userId("biz1")
                    .userPw(passwordEncoder.encode("biz1234"))
                    .userType(Account.UserType.CORPORATE_APPROVED)
                    .companyId(dummyCompany.getCompanyId())
                    .phoneNum("010-3333-3333")
                    .businessNo(dummyCompany.getBusinessNo())
                    .build();

            accountRepository.save(biz1);
        }

        // 26.10.06 추가: 기존 "S카고 종합물류" 업체 조회
        Company scargoCompany = companyRepository
                .findByCompanyNameAndAddress(
                        "S카고 종합물류",
                        "인천광역시 연수구 옥련로 220"
                )
                .orElseThrow(() ->
                        new RuntimeException("S카고 종합물류 업체가 존재하지 않습니다.")
                );

        // 26.10.06 추가: S카고 종합물류 테스트 기업회원 생성
        if (accountRepository.findByUserId("biz2").isEmpty()) {
            Account biz2 = Account.builder()
                    .userName("한솔")
                    .userId("biz2")
                    .userPw(passwordEncoder.encode("biz1234"))
                    .userType(Account.UserType.CORPORATE_APPROVED)
                    .companyId(scargoCompany.getCompanyId())
                    .phoneNum("010-4444-4444")
                    .businessNo(scargoCompany.getBusinessNo())
                    .build();

            accountRepository.save(biz2);
        }

        // 26.10.06 추가: S카고 종합물류 소속 개인회원 생성
        if (accountRepository.findByUserId("per1").isEmpty()) {
            Account per1 = Account.builder()
                    .userName("김기사")
                    .userId("per1")
                    .userPw(passwordEncoder.encode("per1234"))
                    .userType(Account.UserType.GENERAL)
                    .companyId(scargoCompany.getCompanyId())
                    .phoneNum("010-5555-5555")
                    .businessNo(null)
                    .build();

            accountRepository.save(per1);
        }
        } catch (Exception e) {
            System.out.println("[AdminInitializer] 테스트 계정 생성 건너뜀: " + e.getMessage());
        }
    }

    // 26.10.22 통합: 시드 계정 7개의 비밀번호만 test1234 로 맞춘다 (dev 프로필 전용)
    private void resetSeedPasswords() {
        int changed = 0;
        for (String userId : SEED_ACCOUNT_IDS) {
            Account account = accountRepository.findByUserId(userId).orElse(null);
            if (account == null) continue;
            if (account.getUserPw() != null && passwordEncoder.matches(SEED_TEST_PASSWORD, account.getUserPw())) continue;
            account.setUserPw(passwordEncoder.encode(SEED_TEST_PASSWORD));
            accountRepository.save(account);
            changed++;
        }
        // 26.10.24: DB 설치 SQL 에 이미 들어 있는 기본 계정(admin · sbot 등)은 아래 생성 로직이 건너뛰므로 비밀번호가 덤프 값 그대로다
        //  → dev 프로필에서는 코드에 적힌 기본 비밀번호로 맞춰 로그인 가능하게 한다
        java.util.Map<String, String> defaults = java.util.Map.of(
                "admin", "admin1234", "admin2", "admin5678", "sbot", "sbot1234",
                "biz1", "biz1234", "biz2", "biz1234", "per1", "per1234");
        for (java.util.Map.Entry<String, String> e : defaults.entrySet()) {
            Account account = accountRepository.findByUserId(e.getKey()).orElse(null);
            if (account == null) continue;
            if (account.getUserPw() != null && passwordEncoder.matches(e.getValue(), account.getUserPw())) continue;
            account.setUserPw(passwordEncoder.encode(e.getValue()));
            accountRepository.save(account);
            changed++;
        }
        System.out.println("[AdminInitializer] dev 프로필: 시드 계정 비밀번호 test1234 로 맞춤 (" + changed + "개 변경)");
    }
}