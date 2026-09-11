package com.scargo.service;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import com.scargo.dto.LoginRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository; // 업체 조회를 위한 리포지토리
    private final BCryptPasswordEncoder passwordEncoder;

    // 계정 등록 로직
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        // 아이디 중복 체크
        accountRepository.findByUserId(request.getUserId())
                .ifPresent(a -> {
                    throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
                });

        // 프론트에서 전달받은 업체명과 주소로 companies 테이블 조회하여 companyId 획득
        Company company = companyRepository.findByCompanyNameAndAddress(request.getCompanyName(), request.getAddress())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 정보가 일치하는 업체가 없습니다."));

        Account account = Account.builder()
                .userId(request.getUserId())
                .userPw(passwordEncoder.encode(request.getUserPw())) // 비밀번호 암호화
                .userName(request.getUserName())
                .phoneNum(request.getPhoneNum())
                .companyId(company.getCompanyId()) // 조회된 company_id 매핑
                .build();

        Account savedAccount = accountRepository.save(account);
        return new AccountResponse(savedAccount);
    }

    // 전체 계정 목록 조회
    public List<AccountResponse> getAllAccounts() {
        return accountRepository.findAll().stream()
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }

    // 단건 계정 조회
    public AccountResponse getAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));
        return new AccountResponse(account);
    }
    
 // 로그인 로직
    public AccountResponse login(LoginRequest request) {
        // 1. 아이디 존재 여부 확인
        Account account = accountRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 2. 비밀번호 일치 여부 확인 (사용자가 입력한 평문과 DB의 암호화된 비밀번호 비교)
        if (!passwordEncoder.matches(request.getUserPw(), account.getUserPw())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return new AccountResponse(account);
    }
}