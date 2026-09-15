package com.scargo.admin;

import com.scargo.entity.Account;
import com.scargo.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    private final AccountRepository accountRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {  
    //관리자 계정 생성: DB에서 직접생성은 힘듦(비밀번호 암호화 때문에 postgre로 생성시 평문말고 암호화된 비밀번호로 넣어야하기 때문)
    // 현재는 관리자권한이 하나이지만 추후에 관리자를 기능별로 구별한다면 userType 수정 필요	
        // 1번 관리자 계정 생성
        if (accountRepository.findByUserId("admin").isEmpty()) {
            Account admin1 = Account.builder()
                    .userName("시스템관리자1")
                    .userId("admin")
                    .userPw(passwordEncoder.encode("admin1234"))  // 알아서 암호화후 DB에 저장(비밀번호 입력:평문  저장:암호문)
                    .userType("ADMIN")
                    .phoneNum("010-1111-1111")
                    .build();
            accountRepository.save(admin1);
        }

        // 2번 관리자 계정 생성 (원하시는 만큼 추가) -> 해당 양식 그대로 
        if (accountRepository.findByUserId("admin2").isEmpty()) {
            Account admin2 = Account.builder()
                    .userName("시스템관리자2")
                    .userId("admin2")
                    .userPw(passwordEncoder.encode("admin5678"))  // 알아서 암호화후 DB에 저장(비밀번호 입력:평문  저장:암호문)
                    .userType("ADMIN")
                    .phoneNum("010-2222-2222")
                    .build();
            accountRepository.save(admin2);
        }
    }
}