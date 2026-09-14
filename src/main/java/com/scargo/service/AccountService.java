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
    private final CompanyRepository companyRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    // 계정 등록 로직 (일반회원 / 기업회원 분기 처리)
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        // 1. 아이디 중복 체크
        accountRepository.findByUserId(request.getUserId())
                .ifPresent(a -> {
                    throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
                });

        Long companyId = null;
        String userType = "GENERAL"; // 기본값 일반회원
        String businessNo = null;

        // 2. 기업회원 가입인 경우 
        if (request.getCompanyName() != null && !request.getCompanyName().isBlank()) {
            Company company = companyRepository.findByCompanyNameAndAddress(request.getCompanyName(), request.getAddress())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않거나 정보가 일치하는 업체가 없습니다."));
            
            companyId = company.getCompanyId();
            userType = "CORPORATE_PENDING"; // 승인 전 대기 상태로 설정
            businessNo = request.getBusinessNo();

            if (businessNo == null || businessNo.isBlank()) {
                throw new IllegalArgumentException("기업 회원은 사업자 등록번호입력 필수.");
            }
        }

        // 3. Account 엔티티 빌드 및 저장
        Account account = Account.builder()
                .userId(request.getUserId())
                .userPw(passwordEncoder.encode(request.getUserPw())) // 비밀번호 암호화
                .userName(request.getUserName())
                .phoneNum(request.getPhoneNum())
                .userType(userType)          // 'GENERAL' 또는 'CORPORATE_PENDING'
                .companyId(companyId)        // 소속 회사 ID
                .businessNo(businessNo)      // 사업자 등록번호 (기업회원만)
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
    //기업 계정(승인전, 승인후)여부 상관없이 로그인은 가능 but 기능면에서 분리필요
    public AccountResponse login(LoginRequest request) {
        // 1. 아이디 존재 여부 확인
        Account account = accountRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        // 2. 비밀번호 일치 여부 확인
        if (!passwordEncoder.matches(request.getUserPw(), account.getUserPw())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        return new AccountResponse(account);
    }

    // 관리자에 의한 기업계정 허가
    @Transactional
    public void approveCorporateAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        if (!"CORPORATE_PENDING".equals(account.getUserType())) {
            throw new IllegalArgumentException("승인 대기 중인 기업 계정이 아닙니다.");
        }

        // 상태를 승인 완료로 변경
        account.setUserType("CORPORATE_APPROVED");  // 기업계정(대기)-> 기업계정(승인)
    }
}