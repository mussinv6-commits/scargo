package com.scargo.service;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.dto.LoginRequest;
import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.Enum.NotificationType;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
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
    private final NotificationService notificationService;

    // 계정 등록 로직 (관리자 / 일반회원 / 기업회원 구분)
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        accountRepository.findByUserId(request.getUserId())
                .ifPresent(a -> {
                    throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
                });

        String strUserType = request.getUserType();
        Account.UserType userType;
        if (strUserType == null || strUserType.isBlank()) {
            userType = Account.UserType.GENERAL; 
        } else {
            userType = Account.UserType.valueOf(strUserType);
        }

        Long companyId = null;
        String businessNo = null;

        if (userType != Account.UserType.ADMIN) {
            if (request.getCompanyId() == null) {
                throw new IllegalArgumentException("소속 업체를 선택해주세요.");
            }

            Company company = companyRepository.findById(request.getCompanyId())
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다."));

            companyId = company.getCompanyId();

            if (userType == Account.UserType.CORPORATE_PENDING) {
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

        // 기업 회원가입 신청(승인 대기) 시 관리자(ADMIN)들에게 알림 발송 트리거
        if (userType == Account.UserType.CORPORATE_PENDING) {
            List<Account> admins = accountRepository.findByUserType(Account.UserType.ADMIN);
            for (Account admin : admins) {
                NotificationCreateRequest notificationRequest = NotificationCreateRequest.builder()
                        .accountId(admin.getAccountId())
                        .title("기업 회원 가입 승인 요청")
                        .message("새로운 기업 회원(" + savedAccount.getUserName() + ")이 가입 승인을 요청했습니다.")
                        .notificationType(NotificationType.CORPORATE_APPROVAL) 
                        .referenceId(savedAccount.getAccountId())
                        .build();

                notificationService.createNotification(notificationRequest);
            }
        }

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
    
    // 로그인 로직 (Spring Security Context 세션 저장 반영)
    public AccountResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        Account account = accountRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

        if (!passwordEncoder.matches(request.getUserPw(), account.getUserPw())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }

        // 1. Spring Security 권한 생성 (ADMIN, GENERAL, CORPORATE_APPROVED 등)
        List<SimpleGrantedAuthority> authorities = List.of(
                new SimpleGrantedAuthority(account.getUserType().name())
        );

        // 2. Authentication 객체 생성
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                account.getUserId(),
                null,
                authorities
        );

        // 3. SecurityContext 생성 및 ContextHolder 설정
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);

        // 4. Spring Security 세션 및 사용자 정보 저장
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute("SPRING_SECURITY_CONTEXT", context);
        session.setAttribute("accountId", account.getAccountId());

        return new AccountResponse(account);
    }

    // 관리자에 의한 기업계정 허가
    @Transactional
    public void approveCorporateAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        if (account.getUserType() != Account.UserType.CORPORATE_PENDING) {
            throw new IllegalArgumentException("승인 대기 중인 기업 계정이 아닙니다.");
        }

        // 상태를 승인 완료로 변경
        account.setUserType(Account.UserType.CORPORATE_APPROVED);  

        // 관리자가 기업 계정을 승인했을 때 해당 사용자에게 알림 발송 트리거
        NotificationCreateRequest notificationRequest = NotificationCreateRequest.builder()
                .accountId(account.getAccountId())
                .title("기업 회원 가입 승인 완료")
                .message("기업 회원 가입 신청이 승인되었습니다. 이제 정상적인 서비스 이용이 가능합니다.")
                .notificationType(NotificationType.CORPORATE_APPROVAL) 
                .referenceId(account.getAccountId())
                .build();

        notificationService.createNotification(notificationRequest);
    }

    // 아이디 중복 확인 (사용 가능하면 true)
    public boolean isUserIdAvailable(String userId) {
        return !accountRepository.existsByUserId(userId);
    }
}