package com.scargo.admin;

import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {  
        
        // 1번 관리자 계정 생성 (없는 경우에만 최초 1회 생성)
        if (accountRepository.findByUserId("admin").isEmpty()) {
            Account admin1 = Account.builder()
                    .userName("시스템관리자1")
                    .userId("admin")
                    .userPw(passwordEncoder.encode("admin1234"))
                    .userType(Account.UserType.ADMIN)
                    .phoneNum("010-1111-1111")
                    .build();
            accountRepository.save(admin1);
            System.out.println(">>> [AdminInitializer] admin 계정 최초 생성 완료");
        }

        // 2번 관리자 계정 생성 (없는 경우에만 최초 1회 생성)
        if (accountRepository.findByUserId("admin2").isEmpty()) {
            Account admin2 = Account.builder()
                    .userName("시스템관리자2")
                    .userId("admin2")
                    .userPw(passwordEncoder.encode("admin5678"))
                    .userType(Account.UserType.ADMIN)
                    .phoneNum("010-2222-2222")
                    .build();
            accountRepository.save(admin2);
            System.out.println(">>> [AdminInitializer] admin2 계정 최초 생성 완료");
        }
    }
}