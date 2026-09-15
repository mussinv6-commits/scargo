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

    // 계정 등록 로직 (관리자 / 일반회원 / 기업회원 구분)
    // 관리자는 회사명+주소 입력 X 일반회원/기업회원은 필수 기업회원은 추가로 사업자 등록번호
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        accountRepository.findByUserId(request.getUserId())
                .ifPresent(a -> {
                    throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
                });

        String userType = request.getUserType();
        if (userType == null || userType.isBlank()) {
            userType = "GENERAL"; // 기본값 일반회원
        }

        Long companyId = null;
        String businessNo = null;

        // 관리자(ADMIN)가 아닌 경우에만 소속 회사 및 위치(주소) 필수 체크
        if (!"ADMIN".equals(userType)) {
            if (request.getCompanyId() == null) {
                throw new IllegalArgumentException("소속 업체를 선택해주세요.");
            }

            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다."));

            companyId = company.getCompanyId();

         // 추가 검증 (기업회원인 경우 사업자 등록번호 필수)
            if ("CORPORATE_PENDING".equals(userType)) {
                businessNo = request.getBusinessNo();

                if (businessNo == null || businessNo.isBlank()) {
                    throw new IllegalArgumentException("기업 회원은 사업자 등록번호 입력이 필수입니다.");
                }

                if (company.getBusinessNo() == null || !company.getBusinessNo().equals(businessNo)) {
                    throw new IllegalArgumentException("입력하신 사업자 등록번호가 선택하신 업체 정보와 일치하지 않습니다.");
                }
            }
        }

        // 회원데이터 DB에 저장
        Account account = Account.builder()
                .userId(request.getUserId())
                .userPw(passwordEncoder.encode(request.getUserPw()))
                .userName(request.getUserName())
                .phoneNum(request.getPhoneNum())
                .userType(userType)
                .companyId(companyId)
                .businessNo(businessNo)
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
    // 아이디 중복 확인 (사용 가능하면 true)
    public boolean isUserIdAvailable(String userId) {
        return !accountRepository.existsByUserId(userId);
    }
    
}