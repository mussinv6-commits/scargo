package com.scargo.admin;

import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("dev")
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private static final String TEST_PASSWORD = "test1234";

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {

        // 단일 운영 계열사의 실제 ID 조회
        List<Long> operatorIds = jdbcTemplate.queryForList(
                "SELECT operating_company_id FROM operating_companies",
                Long.class
        );

        if (operatorIds.size() != 1) {
            throw new IllegalStateException(
                    "운영 계열사 데이터가 정확히 1개 있어야 합니다."
            );
        }

        Long operatingCompanyId = operatorIds.get(0);

        // 소속 인자 순서:
        // operatingCompanyId, companyId, hubId, centerId

        // 총관리자: 운영 계열사 직접 소속
        saveTestAccount(
                "관리자",
                "admin01",
                Account.UserType.ADMIN,
                operatingCompanyId, null, null, null,
                "010-0000-0001",
                null
        );

        // 통합 관제사: 못먹어도S카고 물류 소속
        saveTestAccount(
                "통합관제사",
                "control01",
                Account.UserType.CONTROL,
                null, 1L, null, null,
                "010-0000-0004",
                null
        );

        // 허브 관제사: 곤지암허브
        saveTestAccount(
                "곤지암허브 관제사",
                "hubop01",
                Account.UserType.HUB_OP,
                null, null, 1L, null,
                "010-0000-0005",
                null
        );

        // 지역센터 관제사: 인천 물류센터
        saveTestAccount(
                "인천센터 관제사",
                "centerop01",
                Account.UserType.CENTER_OP,
                null, null, null, 3L,
                "010-0000-0006",
                null
        );

        // 컨테이너 운송기사: 못먹어도S카고 물류
        saveTestAccount(
                "김기사",
                "driver01",
                Account.UserType.CONTAINER_DRIVER,
                null, 1L, null, null,
                "010-0000-0000",
                null
        );

        // 간선 운송기사: 데모운송(주)
        saveTestAccount(
                "이간선",
                "linehaul01",
                Account.UserType.LINEHAUL_DRIVER,
                null, 16L, null, null,
                "010-0000-0007",
                null
        );

        // 운송사 담당자: 못먹어도S카고 물류
        saveTestAccount(
                "못먹어도S카고 담당자",
                "corp01",
                Account.UserType.CORPORATE_APPROVED,
                null, 1L, null, null,
                "032-000-0001",
                "101-81-10001"
        );
    }

    private void saveTestAccount(
            String userName,
            String userId,
            Account.UserType userType,
            Long operatingCompanyId,
            Long companyId,
            Long hubId,
            Long centerId,
            String phoneNum,
            String businessNo
    ) {
        if (userType == Account.UserType.ADMIN
                && operatingCompanyId == null) {
            throw new IllegalArgumentException(
                    "총관리자는 운영 계열사 소속이 필수입니다."
            );
        }

        // 같은 로그인 ID가 있으면 기존 계정 갱신
        Account account = accountRepository.findByUserId(userId)
                .orElseGet(Account::new);

        account.setUserName(userName);
        account.setUserId(userId);
        account.setUserType(userType);
        account.setOperatingCompanyId(operatingCompanyId);
        account.setCompanyId(companyId);
        account.setHubId(hubId);
        account.setCenterId(centerId);
        account.setPhoneNum(phoneNum);
        account.setBusinessNo(businessNo);

        String currentHash = account.getUserPw();
        if (currentHash == null
                || !passwordEncoder.matches(TEST_PASSWORD, currentHash)) {
            account.setUserPw(passwordEncoder.encode(TEST_PASSWORD));
        }

        accountRepository.save(account);
    }
}