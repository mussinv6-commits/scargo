package com.scargo.service;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.dto.AccountUpdateRequest; // 26.10.01 병합(태수님)
import com.scargo.dto.NotificationCreateRequest;
import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.Enum.NotificationType;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;
import com.scargo.repository.TruckRepository; // 26.10.01 병합: 회원 삭제 시 차량 배정 해제
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
    private final TruckRepository truckRepository; // 26.10.01 병합: 회원 삭제 시 차량 배정 해제
    private final BCryptPasswordEncoder passwordEncoder;
    private final NotificationService notificationService; // 알람 서비스 주입

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
    
    // 26.10.01 병합(태수님 26.09.30 추가): 회원정보 수정 로직
    // 값이 입력된 항목(비밀번호/이름/휴대폰 번호)만 변경
    @Transactional
    public AccountResponse updateAccount(Long accountId, AccountUpdateRequest request) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        // 1. 비밀번호 수정 (값이 입력된 경우에만 인코딩하여 변경)
        if (request.getUserPw() != null && !request.getUserPw().isBlank()) {
            account.setUserPw(passwordEncoder.encode(request.getUserPw()));
        }

        // 2. 이름 수정
        if (request.getUserName() != null && !request.getUserName().isBlank()) {
            account.setUserName(request.getUserName());
        }

        // 3. 휴대폰 번호 수정
        if (request.getPhoneNum() != null && !request.getPhoneNum().isBlank()) {
            account.setPhoneNum(request.getPhoneNum());
        }

        return new AccountResponse(account);
    }

    // 로그인 로직
    public AccountResponse login(LoginRequest request) {
        Account account = accountRepository.findByUserId(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 아이디입니다."));

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

    // 26.09.21 추가: 관리자에 의한 기업계정 거절
    // accounts.user_type 체크 제약조건(CORPORATE_PENDING/CORPORATE_APPROVED/GENERAL/ADMIN)에
    // "거절됨" 상태가 따로 없어서, DB 제약조건을 건드리는 대신 승인 대기 신청 자체를 삭제하는 방식으로 처리한다.
    // (거절된 사람은 정보를 고쳐서 다시 회원가입하면 됨)
    @Transactional
    public void rejectCorporateAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        if (account.getUserType() != Account.UserType.CORPORATE_PENDING) {
            throw new IllegalArgumentException("승인 대기 중인 기업 계정이 아닙니다.");
        }

        accountRepository.delete(account);
    }

    // 26.10.01 병합: 관리자 회원 관리 화면의 회원 삭제 (프론트에서 DELETE /api/accounts/{id} 호출하는데 백엔드에 없었음)
    // - 관리자 계정은 삭제 불가 (관리자 전원이 지워져 로그인 못 하는 상황 방지)
    // - 전담 차량이 배정된 기사면 배정을 먼저 해제 (trucks.assigned_account_id)
    // - 게시글/댓글/점검표 작성자는 DB 에서 NULL 처리, 알림은 같이 삭제됨 (FK ON DELETE 설정)
    @Transactional
    public void deleteAccount(Long accountId) {
        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 계정입니다. ID: " + accountId));

        if (account.getUserType() == Account.UserType.ADMIN) {
            throw new IllegalArgumentException("관리자 계정은 삭제할 수 없습니다.");
        }

        truckRepository.findByAssignedDriver_AccountId(accountId)
                .ifPresent(truck -> truck.setAssignedDriver(null));
        truckRepository.flush();

        accountRepository.delete(account);
    }

    // 아이디 중복 확인 (사용 가능하면 true)
    public boolean isUserIdAvailable(String userId) {
        return !accountRepository.existsByUserId(userId);
    }

    // 26.09.22 추가: 특정 업체 소속의 화물차 기사(GENERAL) 목록 조회 - 사업자 화면의 기사 배정용
    public List<AccountResponse> getDriversByCompany(Long companyId) {
        return accountRepository.findByCompanyIdAndUserType(companyId, Account.UserType.GENERAL).stream()
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }
}