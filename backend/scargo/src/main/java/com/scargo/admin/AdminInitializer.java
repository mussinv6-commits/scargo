package com.scargo.admin;

import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@org.springframework.context.annotation.Profile("!h2 & !prod") // fix_1300: 운영(prod) 프로필에서는 시드 비밀번호 계정 생성 안 함
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(name = "scargo.seed-accounts", havingValue = "true") // fix_1300: dev 프로필만 true // 26.10.20: h2 기동 확인 때는 1차 DB 업체 데이터가 없어 제외 (OpsDemoSeeder 사용)
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate; // 26.10.22 (share): 관리자 운영 계열사 조회
    private final Environment environment;   // 26.10.22 (share): 시드 비밀번호 재설정 여부

    // 26.10.22 (share): DB 스크립트가 넣는 권한별 시드 계정. 통합본 DB(00_scargo_db_최종통합.sql)의 비밀번호는 demo1234.
    //  scargo.seed-password-reset=true 로 실행하면 아래 계정 비밀번호를 SEED_TEST_PASSWORD(test1234) 로 맞춘다 (기본은 끔)
    private static final String SEED_TEST_PASSWORD = "test1234";
    private static final java.util.List<String> SEED_ACCOUNT_IDS = java.util.List.of(
            "driver01", "admin01", "corp01", "control01", "hubop01", "centerop01", "branch01");

    @Override
    public void run(ApplicationArguments args) {

        if (Boolean.parseBoolean(environment.getProperty("scargo.seed-password-reset", "false"))) {
            resetSeedPasswords();
        }

        // 26.10.22 (share): DB 는 ADMIN 에 운영 계열사가 필수(chk_accounts_admin_operator) → 첫 운영 계열사에 소속시킨다
        //  운영 계열사가 없으면 관리자 계정을 만들지 않는다 (만들면 DB 제약 위반으로 서버 기동 실패)
        Long operatingCompanyId = jdbcTemplate.queryForObject(
                "SELECT min(operating_company_id) FROM operating_companies", Long.class);
        if (operatingCompanyId == null) {
            System.out.println("[AdminInitializer] 운영 계열사(operating_companies)가 없어 관리자 계정 생성을 건너뜀");
            return;
        }

        // 관리자 계정 생성: DB에서 직접 생성은 비밀번호 암호화 때문에 암호화된 값으로 넣어야 함
        // 현재는 관리자 권한이 하나이지만 추후에 관리자를 기능별로 구별한다면 userType 수정 필요

        // 1번 관리자 계정 생성
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
        // 26.10.21 v4 병합(팀장 v3): S-Bot 관리자 계정 (S Cargo 관리자와 분리. 아이디가 sbot 으로 시작하면 S-Bot 관리자)
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


        // 26.10.20: admin 은 통합 DB SQL 시드(admin1234)가 넣음 → 위에서 있으면 건너뜀. 여기서는 admin2 만 새로 만든다
        // 2번 관리자 계정 생성
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

        approveCarrier(dummyCompany);

        // 테스트용 승인완료 사업자 계정 생성
        if (accountRepository.findByUserId("biz1").isEmpty()) {
            Account biz1 = Account.builder()
                    .userName("테스트사업자")
                    .userId("biz1")
                    .userPw(passwordEncoder.encode("biz1234"))
                    .userType(Account.UserType.CORPORATE_APPROVED)   // 26.10.21: 운송사 담당자 (회사가 APPROVED)
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

        approveCarrier(scargoCompany);

        // 26.10.06 추가: S카고 종합물류 테스트 기업회원 생성
        if (accountRepository.findByUserId("biz2").isEmpty()) {
            Account biz2 = Account.builder()
                    .userName("한솔")
                    .userId("biz2")
                    .userPw(passwordEncoder.encode("biz1234"))
                    .userType(Account.UserType.CORPORATE_APPROVED)   // 26.10.21: 운송사 담당자 (회사가 APPROVED)
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
                    .userType(Account.UserType.CONTAINER_DRIVER)   // 26.10.21: GENERAL 폐지
                    .companyId(scargoCompany.getCompanyId())
                    .phoneNum("010-5555-5555")
                    .businessNo(null)
                    .build();

            accountRepository.save(per1);
        }
    }

    // 26.10.20: 테스트 운송사 회사를 승인 상태로
    private void approveCarrier(Company c) {
        if (!"APPROVED".equals(c.getApprovalStatus())) {
            // 승인자는 ADMIN 이어야 함 (DB trg_company_approver). SQL 시드의 admin 또는 방금 만든 admin2
            Long approver = accountRepository.findByUserId("admin").or(() -> accountRepository.findByUserId("admin2")).map(Account::getAccountId).orElse(null);
            c.markCarrierPending(); c.approveCarrier(approver); companyRepository.save(c);
        }
    }

    // 26.10.22 (share): 시드 계정 비밀번호만 test1234 로 맞춘다 (scargo.seed-password-reset=true 일 때만)
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
        System.out.println("[AdminInitializer] 시드 계정 비밀번호 test1234 로 맞춤 (" + changed + "개 변경)");
    }
}
