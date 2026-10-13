package com.scargo.admin;

import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository; // 26.09.21 추가: 더미 사업자 계정용 업체 생성에 필요
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {  
        // 관리자 계정 생성: DB에서 직접 생성은 비밀번호 암호화 때문에 암호화된 값으로 넣어야 함
        // 현재는 관리자 권한이 하나이지만 추후에 관리자를 기능별로 구별한다면 userType 수정 필요    
        
        // 1번 관리자 계정 생성
        if (accountRepository.findByUserId("admin").isEmpty()) {
            Account admin1 = Account.builder()
                    .userName("시스템관리자1")
                    .userId("admin")
                    .userPw(passwordEncoder.encode("admin1234")) // 비밀번호 입력: 평문 -> 저장: 암호문
                    .userType(Account.UserType.ADMIN)
                    .phoneNum("010-1111-1111")
                    .build();
            accountRepository.save(admin1);
        }

        // 2번 관리자 계정 생성
        if (accountRepository.findByUserId("admin2").isEmpty()) {
            Account admin2 = Account.builder()
                    .userName("시스템관리자2")
                    .userId("admin2")
                    .userPw(passwordEncoder.encode("admin5678")) // 비밀번호 입력: 평문 -> 저장: 암호문
                    .userType(Account.UserType.ADMIN)
                    .phoneNum("010-2222-2222")
                    .build();
            accountRepository.save(admin2);
        }

        // 26.09.21 추가: 테스트용 더미 "사업자(승인완료)" 계정 생성
        // 승인 절차(회원가입 → 관리자 승인)를 매번 거치지 않고 바로 로그인해서
        // 사업자 화면을 테스트할 수 있도록 앱 기동 시 자동으로 만들어둔다.
        Company dummyCompany = companyRepository
                .findByCompanyNameAndAddress("(주)스카고로지스틱스", "서울특별시 강서구 공항대로 100")
                .orElseGet(() -> companyRepository.save(
                        Company.builder()
                                .companyName("(주)스카고로지스틱스")
                                .address("서울특별시 강서구 공항대로 100")
                                .businessNo("123-45-67890")
                                .industryType("컨테이너 운송업")
                                .representativeName("김대표")
                                .build()
                ));

        if (accountRepository.findByUserId("biz1").isEmpty()) {
            Account biz1 = Account.builder()
                    .userName("테스트사업자")
                    .userId("biz1")
                    .userPw(passwordEncoder.encode("biz1234"))
                    .userType(Account.UserType.CORPORATE_APPROVED) // 승인 절차 없이 바로 승인완료 상태로 생성
                    .companyId(dummyCompany.getCompanyId())
                    .phoneNum("010-3333-3333")
                    .businessNo(dummyCompany.getBusinessNo())
                    .build();
            accountRepository.save(biz1);
        }
    }
}
