package com.scargo.service;

import com.scargo.dto.AccountCreateRequest;
import com.scargo.dto.AccountResponse;
import com.scargo.dto.AccountUpdateRequest;
import com.scargo.dto.LoginRequest;
import com.scargo.dto.NotificationCreateRequest; // 26.10.22 통합: 기업회원 가입 · 승인 알림
import com.scargo.Enum.NotificationType;
import com.scargo.entity.Account;
import com.scargo.entity.Company;
import com.scargo.repository.AccountRepository;
import com.scargo.repository.CompanyRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import com.scargo.security.AuthenticatedAccountPrincipal;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final CompanyRepository companyRepository;

    private final BCryptPasswordEncoder passwordEncoder;
    private final EntityManager entityManager;
    private final NotificationService notificationService; // 26.10.22 통합: 기업회원 가입 · 승인 알림

    // 26.10.22 통합(share_1022_1517 가입 화면 유지): 비로그인 회원가입
    //  - GENERAL(일반) · CORPORATE_PENDING(기업, 관리자 승인 대기)만 가입 가능. 그 외 권한은 관리자 · 운송사 담당자가 생성
    //  - 기업회원은 사업자번호가 소속 업체와 일치해야 하고, 가입하면 관리자에게 승인 요청 알림
    //  - DB chk_user_type 에 GENERAL · CORPORATE_PENDING 이 있어야 함 (통합 DB 9장)
    @Transactional
    public AccountResponse signUp(AccountCreateRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("아이디를 입력해주세요.");
        }
        if (request.getUserPw() == null || request.getUserPw().isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요.");
        }
        String userId = request.getUserId().trim();
        if (accountRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        Account.UserType userType = request.getUserType() == null || request.getUserType().isBlank()
                ? Account.UserType.GENERAL : parseUserType(request.getUserType());
        if (userType != Account.UserType.GENERAL && userType != Account.UserType.CORPORATE_PENDING) {
            throw new AccessDeniedException("회원가입으로는 일반회원 또는 기업회원(승인 대기)만 만들 수 있습니다.");
        }
        if (request.getHubId() != null || request.getCenterId() != null) {
            throw new IllegalArgumentException("회원가입에서는 허브·센터 소속을 지정할 수 없습니다.");
        }
        if (request.getCompanyId() == null) {
            throw new IllegalArgumentException("소속 업체를 선택해주세요.");
        }
        Company company = companyRepository.findById(request.getCompanyId())
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다."));
        // 26.10.22 추가: 관리자는 같은 운영 계열사 계정만 조회 · 승인하므로, 외부 업체(운영 계열사 없음)로 가입하면 승인할 수 없다
        if (nativeCount("SELECT COUNT(*) FROM companies WHERE company_id = :id "
                + "AND operating_company_id IS NOT NULL", company.getCompanyId()) == 0) {
            throw new IllegalArgumentException("운영 계열사 소속 업체만 선택할 수 있습니다.");
        }

        String businessNo = null;
        if (userType == Account.UserType.CORPORATE_PENDING) {
            businessNo = request.getBusinessNo() == null ? "" : request.getBusinessNo().trim();
            if (businessNo.isEmpty()) {
                throw new IllegalArgumentException("기업 회원은 사업자 등록번호 입력이 필수입니다.");
            }
            if (company.getBusinessNo() == null || !company.getBusinessNo().equals(businessNo)) {
                throw new IllegalArgumentException("입력하신 사업자 등록번호가 선택하신 업체 정보와 일치하지 않습니다.");
            }
        }

        Account saved = accountRepository.save(Account.builder()
                .userId(userId)
                .userPw(passwordEncoder.encode(request.getUserPw()))
                .userName(request.getUserName())
                .phoneNum(request.getPhoneNum())
                .userType(userType)
                .companyId(company.getCompanyId())
                .businessNo(businessNo)
                .build());

        if (userType == Account.UserType.CORPORATE_PENDING) {
            for (Account admin : accountRepository.findByUserType(Account.UserType.ADMIN)) {
                notificationService.createNotification(NotificationCreateRequest.builder()
                        .accountId(admin.getAccountId())
                        .title("기업 회원 가입 승인 요청")
                        .message("새로운 기업 회원(" + saved.getUserName() + ")이 가입 승인을 요청했습니다.")
                        .notificationType(NotificationType.CORPORATE_APPROVAL)
                        .referenceId(saved.getAccountId())
                        .build());
            }
        }
        return new AccountResponse(saved);
    }

    // 관리자의 기업계정 승인 (같은 운영 계열사 소속 계정만)
    //  통합(26.10.26): 두 승인 방식을 함께 처리
    //   ① share: 회원가입으로 만든 CORPORATE_PENDING 계정 → CORPORATE_APPROVED 로 변경
    //   ② scargo_1: 운송사 승인은 회사 단위(companies.is_carrier + approval_status). 승인 전 회사의 담당자는
    //      ROLE_CORPORATE_PENDING 만 받으므로(CarrierLegacy), 계정 승인 시 소속 회사도 운송사 APPROVED 로 만든다
    @Transactional
    public void approveCorporateAccount(Long accountId) {
        Account caller = currentAccount();
        Account account = findAccount(accountId);
        requireManagedAccount(account, requireAdminOperator(caller));
        boolean pendingAccount = account.getUserType() == Account.UserType.CORPORATE_PENDING;
        Company company = account.getCompanyId() == null ? null
                : companyRepository.findById(account.getCompanyId()).orElse(null);
        boolean pendingCompany = account.getUserType() == Account.UserType.CORPORATE_APPROVED && company != null
                && !"APPROVED".equals(company.getApprovalStatus());
        if (!pendingAccount && !pendingCompany) {
            throw new IllegalArgumentException("승인 대기 중인 기업 계정이 아닙니다.");
        }
        // DB 트리거(trg_account_carrier_contact): CORPORATE_APPROVED 는 운송사(is_carrier) 소속이어야 하므로 회사를 먼저 승인·반영한다
        if (company != null && !(Boolean.TRUE.equals(company.getIsCarrier()) && "APPROVED".equals(company.getApprovalStatus()))) {
            company.markCarrierPending();
            company.approveCarrier(caller.getAccountId());
            companyRepository.saveAndFlush(company);
            notificationService.createNotification(NotificationCreateRequest.builder()
                    .companyId(company.getCompanyId())
                    .title("운송사 승인 완료")
                    .message(company.getCompanyName() + " 운송사 등록이 승인되었습니다. 운송오더·배차·기사 승인 화면을 쓸 수 있습니다.")
                    .notificationType(NotificationType.CORPORATE_APPROVAL)
                    .referenceId(company.getCompanyId())
                    .build());
        }
        if (pendingAccount) {
            account.setUserType(Account.UserType.CORPORATE_APPROVED);
            accountRepository.flush();
        }
        notificationService.createNotification(NotificationCreateRequest.builder()
                .accountId(account.getAccountId())
                .title("기업 회원 가입 승인 완료")
                .message("기업 회원 가입 신청이 승인되었습니다. 이제 정상적인 서비스 이용이 가능합니다.")
                .notificationType(NotificationType.CORPORATE_APPROVAL)
                .referenceId(account.getAccountId())
                .build());
    }

    // 관리자의 기업계정 거절
    //  ① share: CORPORATE_PENDING 신청 = 계정 삭제 (다시 가입하면 됨)
    //  ② scargo_1: 승인 대기 운송사(회사)의 담당자 = 회사 단위 반려 (REJECTED + reject_reason, 계정은 남김)
    @Transactional
    public void rejectCorporateAccount(Long accountId) { rejectCorporateAccount(accountId, "관리자 반려"); }

    @Transactional
    public void rejectCorporateAccount(Long accountId, String reason) {
        Account account = findAccount(accountId);
        requireManagedAccount(account, requireAdminOperator(currentAccount()));
        if (account.getUserType() == Account.UserType.CORPORATE_PENDING) {
            accountRepository.delete(account);
            return;
        }
        Company company = account.getUserType() == Account.UserType.CORPORATE_APPROVED && account.getCompanyId() != null
                ? companyRepository.findById(account.getCompanyId()).orElse(null) : null;
        if (company == null || !"PENDING".equals(company.getApprovalStatus())) {
            throw new IllegalArgumentException("승인 대기 중인 기업 계정이 아닙니다.");
        }
        company.rejectCarrier(reason == null || reason.isBlank() ? "관리자 반려" : reason);
        notificationService.createNotification(NotificationCreateRequest.builder()
                .companyId(company.getCompanyId())
                .title("운송사 승인 반려")
                .message(company.getCompanyName() + " 운송사 등록이 반려되었습니다. 사유: " + company.getRejectReason())
                .notificationType(NotificationType.CORPORATE_APPROVAL)
                .referenceId(company.getCompanyId())
                .build());
    }

    // 계정 등록
    // 관리자·관제사·기업계정 생성 권한은 호출 경로에서 확인해야 함
    @Transactional
    public AccountResponse createAccount(AccountCreateRequest request) {
        if (request.getUserId() == null || request.getUserId().isBlank()) {
            throw new IllegalArgumentException("아이디를 입력해주세요.");
        }

        if (request.getUserPw() == null || request.getUserPw().isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해주세요.");
        }

        String userId = request.getUserId().trim();

        if (accountRepository.existsByUserId(userId)) {
            throw new IllegalArgumentException("이미 존재하는 아이디입니다.");
        }

        Account.UserType userType = parseUserType(request.getUserType());

        Account caller = currentAccount();
        Long companyId = request.getCompanyId();
        Long hubId = request.getHubId();
        Long centerId = request.getCenterId();
        Long operatingCompanyId = null;

        if (caller.getUserType() == Account.UserType.ADMIN) {
            Long operatorId = requireAdminOperator(caller);
            // ADMIN 및 소속 업체가 없는 통합 관제사는 운영 계열사에 직접 연결
            if (userType == Account.UserType.ADMIN
                    || (userType == Account.UserType.CONTROL && companyId == null)) {
                operatingCompanyId = operatorId;
            }
            validateAffiliations(operatorId, operatingCompanyId, companyId, hubId, centerId);
        } else {
            requireCorporate(caller);
            if (!isDriver(userType)) {
                throw new AccessDeniedException("운송사 담당자는 기사 계정만 생성할 수 있습니다.");
            }
            if (companyId != null && !companyId.equals(caller.getCompanyId())) {
                throw new AccessDeniedException("자기 업체의 기사만 생성할 수 있습니다.");
            }
            companyId = caller.getCompanyId();
        }

        String businessNo = request.getBusinessNo();
        if (businessNo != null) {
            businessNo = businessNo.trim();
            if (businessNo.isEmpty()) {
                businessNo = null;
            }
        }

        if (businessNo != null
                && !businessNo.matches("^[0-9]{3}-[0-9]{2}-[0-9]{5}$")) {
            throw new IllegalArgumentException(
                    "사업자번호는 000-00-00000 형식으로 입력해주세요."
            );
        }

        // 운송사 및 기사 계정은 소속 업체 필수
        boolean companyRequired =
                userType == Account.UserType.CORPORATE_APPROVED
                || userType == Account.UserType.CONTAINER_DRIVER
                || userType == Account.UserType.BRANCH_DRIVER;

        if (companyRequired && companyId == null) {
            throw new IllegalArgumentException("소속 업체를 선택해주세요.");
        }

        Company company = null;
        if (companyId != null) {
            company = companyRepository.findById(companyId)
                    .orElseThrow(() ->
                            new IllegalArgumentException("존재하지 않는 업체입니다.")
                    );
        }

        // 기업계정 사업자번호는 NULL 허용.
        // 입력된 경우 소속 업체 사업자번호와 일치해야 함
        if (userType == Account.UserType.CORPORATE_APPROVED
                && businessNo != null
                && company != null
                && !businessNo.equals(company.getBusinessNo())) {
            throw new IllegalArgumentException(
                    "입력하신 사업자번호가 소속 업체 정보와 일치하지 않습니다."
            );
        }

        validateAccountScope(userType, hubId, centerId);
        if (userType == Account.UserType.ADMIN && companyId != null) {
            throw new IllegalArgumentException("총관리자는 운송사 대신 운영 계열사에 직접 소속됩니다.");
        }

        Account account = Account.builder()
                .userId(userId)
                .userPw(passwordEncoder.encode(request.getUserPw()))
                .userName(request.getUserName())
                .phoneNum(request.getPhoneNum())
                .userType(userType)
                .operatingCompanyId(operatingCompanyId)
                .companyId(companyId)
                .hubId(hubId)
                .centerId(centerId)
                .businessNo(businessNo)
                .build();

        Account savedAccount = accountRepository.save(account);
        return new AccountResponse(savedAccount);
    }

    // 계정 유형 필수 입력 및 enum 검증
    private Account.UserType parseUserType(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("계정 유형을 선택해주세요.");
        }

        try {
            return Account.UserType.valueOf(
                    value.trim().toUpperCase(Locale.ROOT)
            );
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "지원하지 않는 계정 유형입니다: " + value
            );
        }
    }

    // 권한별 허브·센터 소속 검증
    private void validateAccountScope(
            Account.UserType userType,
            Long hubId,
            Long centerId
    ) {
        if (userType == null) {
            throw new IllegalArgumentException("계정 유형이 필요합니다.");
        }
        if (userType == Account.UserType.HUB_OP) {
            if (hubId == null) {
                throw new IllegalArgumentException(
                        "허브 관제사는 소속 허브를 선택해야 합니다."
                );
            }

            Number count = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM hubs WHERE hub_id = :hubId"
            )
                    .setParameter("hubId", hubId)
                    .getSingleResult();

            if (count.longValue() == 0) {
                throw new IllegalArgumentException("존재하지 않는 허브입니다.");
            }
        } else if (hubId != null) {
            throw new IllegalArgumentException(
                    "허브 관제사만 소속 허브를 지정할 수 있습니다."
            );
        }

        if (userType == Account.UserType.CENTER_OP) {
            if (centerId == null) {
                throw new IllegalArgumentException(
                        "지역센터 관제사는 소속 지역센터를 선택해야 합니다."
                );
            }

            Number count = (Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM logistics_centers "
                            + "WHERE center_id = :centerId "
                            + "AND center_type = 'DISTRIBUTION_CENTER'"
            )
                    .setParameter("centerId", centerId)
                    .getSingleResult();

            if (count.longValue() == 0) {
                throw new IllegalArgumentException(
                        "존재하지 않거나 지역 물류센터가 아닌 센터입니다."
                );
            }
        } else if (centerId != null) {
            throw new IllegalArgumentException(
                    "지역센터 관제사만 소속 지역센터를 지정할 수 있습니다."
            );
        }
    }

    // 전체 계정 목록 조회
    public List<AccountResponse> getAllAccounts() {
        Long operatorId = requireAdminOperator(currentAccount());
        return accountRepository.findAccountsInOperatingCompany(operatorId).stream()
                .filter(account -> isConsistentMember(account, operatorId))
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }

    // 단건 계정 조회
    public AccountResponse getAccount(Long accountId) {
        Account account = findAccount(accountId);
        requireAccountRead(currentAccount(), account);
        return new AccountResponse(account);
    }

    // 회원정보 및 소속 변경: null인 필드는 기존 값 유지
    @Transactional
    public AccountResponse updateAccount(
            Long accountId,
            AccountUpdateRequest request
    ) {
        Account account = findAccount(accountId);
        Account caller = currentAccount();
        Long operatorId = null;
        if (caller.getUserType() == Account.UserType.ADMIN) {
            operatorId = requireAdminOperator(caller);
            requireManagedAccount(account, operatorId);
        } else {
            if (!caller.getAccountId().equals(accountId)) {
                throw new AccessDeniedException("본인 계정만 수정할 수 있습니다.");
            }
            if (request.getCompanyId() != null || request.getHubId() != null
                    || request.getCenterId() != null || request.getBusinessNo() != null) {
                throw new AccessDeniedException("소속 및 사업자번호는 관리자만 변경할 수 있습니다.");
            }
        }
        Account.UserType userType = account.getUserType();

        if (userType == null) {
            throw new IllegalArgumentException("계정 권한이 설정되어 있지 않습니다.");
        }

        Long companyId = request.getCompanyId() != null
                ? request.getCompanyId() : account.getCompanyId();
        Long hubId = request.getHubId() != null
                ? request.getHubId() : account.getHubId();
        Long centerId = request.getCenterId() != null
                ? request.getCenterId() : account.getCenterId();

        boolean companyRequired =
                userType == Account.UserType.CORPORATE_APPROVED
                || userType == Account.UserType.CONTAINER_DRIVER
                || userType == Account.UserType.BRANCH_DRIVER;

        if (companyRequired && companyId == null) {
            throw new IllegalArgumentException("운송사 및 기사 계정은 소속 업체가 필요합니다.");
        }

        Company company = null;
        if (companyId != null) {
            company = companyRepository.findById(companyId)
                    .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 업체입니다."));
        }

        validateAccountScope(userType, hubId, centerId);
        if (userType == Account.UserType.ADMIN && companyId != null) {
            throw new IllegalArgumentException("총관리자는 운송사 대신 운영 계열사에 직접 소속됩니다.");
        }
        if (operatorId != null) {
            validateAffiliations(operatorId, account.getOperatingCompanyId(),
                    companyId, hubId, centerId);
        }

        // 기사 이직 시 차량·기존 오더의 회사까지 자동 변경하지 않음
        if (!Objects.equals(account.getCompanyId(), companyId) && isDriver(userType)) {
            long references = nativeCount(
                    "SELECT COUNT(*) FROM trucks WHERE assigned_account_id = :id", accountId)
                    + nativeCount("SELECT COUNT(*) FROM transport_orders "
                            + "WHERE driver_account_id = :id "
                            + "AND status NOT IN ('HANDED_OVER', 'CANCELED')", accountId);
            if (references > 0) {
                throw new IllegalArgumentException(
                        "배정 차량 또는 진행 중 오더가 있습니다. 배정을 해제·재배정한 뒤 소속을 변경해주세요.");
            }
        }

        String businessNo = account.getBusinessNo();
        if (request.getBusinessNo() != null) {
            businessNo = request.getBusinessNo().trim();
            if (businessNo.isEmpty()) {
                businessNo = null;
            }
        }

        if (businessNo != null
                && !businessNo.matches("^[0-9]{3}-[0-9]{2}-[0-9]{5}$")) {
            throw new IllegalArgumentException("사업자번호는 000-00-00000 형식으로 입력해주세요.");
        }

        if (userType == Account.UserType.CORPORATE_APPROVED
                && businessNo != null
                && company != null
                && !businessNo.equals(company.getBusinessNo())) {
            throw new IllegalArgumentException(
                    "사업자번호가 소속 업체 정보와 일치하지 않습니다. "
                            + "새 업체의 사업자번호를 함께 입력하거나 공백으로 전달해 삭제해주세요."
            );
        }

        // 검증 완료 후 변경사항 적용
        if (request.getUserPw() != null && !request.getUserPw().isBlank()) {
            account.setUserPw(passwordEncoder.encode(request.getUserPw()));
        }
        if (request.getUserName() != null && !request.getUserName().isBlank()) {
            account.setUserName(request.getUserName());
        }
        if (request.getPhoneNum() != null && !request.getPhoneNum().isBlank()) {
            account.setPhoneNum(request.getPhoneNum());
        }

        account.setCompanyId(companyId);
        account.setHubId(hubId);
        account.setCenterId(centerId);
        account.setBusinessNo(businessNo);

        accountRepository.flush();
        return new AccountResponse(account);
    }
    // 로그인
    public AccountResponse login(LoginRequest request) {
        if (request.getUserId() == null
                || request.getUserId().isBlank()
                || request.getUserPw() == null
                || request.getUserPw().isBlank()) {
            throw new IllegalArgumentException(
                    "아이디와 비밀번호를 입력해주세요."
            );
        }

        Account account = accountRepository
                .findByUserId(request.getUserId().trim())
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "아이디 또는 비밀번호가 일치하지 않습니다."
                        )
                );

        if (!passwordEncoder.matches(
                request.getUserPw(),
                account.getUserPw()
        )) {
            throw new IllegalArgumentException(
                    "아이디 또는 비밀번호가 일치하지 않습니다."
            );
        }

        return new AccountResponse(account);
    }

    // 관리자 회원 삭제
    @Transactional
    public void deleteAccount(Long accountId) {
        Long operatorId = requireAdminOperator(currentAccount());
        Account account = findAccount(accountId);
        requireManagedAccount(account, operatorId);

        if (account.getUserType() == Account.UserType.ADMIN) {
            throw new IllegalArgumentException(
                    "관리자 계정은 삭제할 수 없습니다."
            );
        }

        // 기존 Repository의 단건 조회 대신 모든 배정 차량을 해제
        // DB 직접 수정 전 영속성 컨텍스트 변경사항 반영
        entityManager.flush();

        entityManager.createNativeQuery(
                "UPDATE trucks SET assigned_account_id = NULL "
                        + "WHERE assigned_account_id = :accountId"
        )
                .setParameter("accountId", accountId)
                .executeUpdate();

        // 직접 수정된 차량 정보가 영속성 컨텍스트에 남지 않도록 초기화
        entityManager.clear();

        Account accountToDelete = findAccount(accountId);
        accountRepository.delete(accountToDelete);
        accountRepository.flush();
    }

    // 아이디 중복 확인: 사용 가능하면 true
    public boolean isUserIdAvailable(String userId) {
        if (userId == null || userId.isBlank()) {
            return false;
        }

        return !accountRepository.existsByUserId(userId.trim());
    }

    // 특정 업체 소속 컨테이너 기사·지선 기사 조회
    public List<AccountResponse> getDriversByCompany(Long companyId) {
        if (companyId == null) {
            throw new IllegalArgumentException("업체 ID를 지정해주세요.");
        }

        Account caller = currentAccount();
        if (caller.getUserType() == Account.UserType.ADMIN) {
            validateAffiliations(requireAdminOperator(caller), null, companyId, null, null);
        } else {
            requireCorporate(caller);
            if (!companyId.equals(caller.getCompanyId())) {
                throw new AccessDeniedException("자기 업체의 기사만 조회할 수 있습니다.");
            }
        }

        return Stream.concat(
                accountRepository.findByCompanyIdAndUserType(
                        companyId,
                        Account.UserType.CONTAINER_DRIVER
                ).stream(),
                accountRepository.findByCompanyIdAndUserType(
                        companyId,
                        Account.UserType.BRANCH_DRIVER
                ).stream()
        )
                .map(AccountResponse::new)
                .collect(Collectors.toList());
    }

    // Controller 이외의 호출에서도 동일한 권한 검사를 수행
    private Account currentAccount() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof AuthenticatedAccountPrincipal)) {
            throw new AccessDeniedException("로그인이 필요합니다.");
        }
        AuthenticatedAccountPrincipal principal = (AuthenticatedAccountPrincipal) auth.getPrincipal();
        return accountRepository.findById(principal.getId())
                .orElseThrow(() -> new AccessDeniedException("유효한 계정이 아닙니다."));
    }

    private Long requireAdminOperator(Account caller) {
        if (caller.getUserType() != Account.UserType.ADMIN
                || caller.getOperatingCompanyId() == null) {
            throw new AccessDeniedException("운영 계열사에 소속된 관리자만 처리할 수 있습니다.");
        }
        return caller.getOperatingCompanyId();
    }

    private void requireCorporate(Account caller) {
        if (caller.getUserType() != Account.UserType.CORPORATE_APPROVED
                || caller.getCompanyId() == null) {
            throw new AccessDeniedException("소속 업체가 있는 운송사 담당자만 처리할 수 있습니다.");
        }
    }

    private boolean isDriver(Account.UserType type) {
        return type == Account.UserType.CONTAINER_DRIVER
                || type == Account.UserType.BRANCH_DRIVER;
    }

    private void requireAccountRead(Account caller, Account target) {
        if (caller.getUserType() == Account.UserType.ADMIN) {
            requireManagedAccount(target, requireAdminOperator(caller));
            return;
        }
        if (caller.getAccountId().equals(target.getAccountId())) return;
        requireCorporate(caller);
        if (!isDriver(target.getUserType())
                || !caller.getCompanyId().equals(target.getCompanyId())) {
            throw new AccessDeniedException("자기 업체의 기사만 조회할 수 있습니다.");
        }
    }

    private void requireManagedAccount(Account target, Long operatorId) {
        if (!accountRepository.existsAccountInOperatingCompany(target.getAccountId(), operatorId)) {
            throw new AccessDeniedException("해당 계열사 소속 계정만 관리할 수 있습니다.");
        }
        // OR 조건으로 발견된 계정도 모든 소속 경로의 일관성을 재검증
        validateAffiliations(operatorId, target.getOperatingCompanyId(),
                target.getCompanyId(), target.getHubId(), target.getCenterId());
    }

    private boolean isConsistentMember(Account account, Long operatorId) {
        try {
            validateAffiliations(operatorId, account.getOperatingCompanyId(),
                    account.getCompanyId(), account.getHubId(), account.getCenterId());
            return true;
        } catch (AccessDeniedException | IllegalArgumentException exception) {
            return false;
        }
    }

    // 직접 소속/업체/허브/센터의 최종 소속을 모두 검증.
    // 하나라도 외부 업체 또는 다른 계열사이면 거절.
    private void validateAffiliations(Long operatorId, Long directOperatorId,
                                      Long companyId, Long hubId, Long centerId) {
        if (operatorId == null) throw new AccessDeniedException("관리자 소속 계열사가 없습니다.");
        if (directOperatorId != null && !operatorId.equals(directOperatorId)) {
            throw new AccessDeniedException("직접 소속 계열사가 일치하지 않습니다.");
        }
        if (companyId != null && !belongsToOperator(
                "SELECT COUNT(*) FROM companies WHERE company_id = :id "
                        + "AND operating_company_id = :operatorId", companyId, operatorId)) {
            throw new AccessDeniedException("같은 계열사의 업체만 지정할 수 있습니다.");
        }
        if (hubId != null) {
            if (!belongsToOperator(
                    "SELECT COUNT(*) FROM hubs h JOIN companies c ON c.company_id = h.company_id "
                            + "WHERE h.hub_id = :id AND c.operating_company_id = :operatorId",
                    hubId, operatorId)) {
                throw new AccessDeniedException("같은 계열사의 허브만 지정할 수 있습니다.");
            }
            if (companyId != null && ((Number) entityManager.createNativeQuery(
                    "SELECT COUNT(*) FROM hubs WHERE hub_id = :hubId AND company_id = :companyId")
                    .setParameter("hubId", hubId).setParameter("companyId", companyId)
                    .getSingleResult()).longValue() == 0) {
                throw new IllegalArgumentException("업체와 허브 운영 업체가 일치하지 않습니다.");
            }
        }
        if (centerId != null && !belongsToOperator(
                "SELECT COUNT(*) FROM logistics_centers WHERE center_id = :id "
                        + "AND center_type = 'DISTRIBUTION_CENTER' "
                        + "AND operating_company_id = :operatorId", centerId, operatorId)) {
            throw new AccessDeniedException("같은 계열사의 지역센터만 지정할 수 있습니다.");
        }
        // 현재 센터는 계열사에 직접 소속되고 운영 업체 연결 컬럼은 없음.
        // 회사+센터를 동시에 지정하면 고용 관계를 검증할 수 없으므로 허용하지 않음.
        if (companyId != null && centerId != null) {
            throw new IllegalArgumentException("센터 직원은 업체 소속 대신 센터 소속으로 지정해주세요.");
        }
        if (directOperatorId == null && companyId == null && hubId == null && centerId == null) {
            throw new IllegalArgumentException("계열사 소속을 확인할 수 있는 연결이 필요합니다.");
        }
    }

    private boolean belongsToOperator(String sql, Long id, Long operatorId) {
        Number result = (Number) entityManager.createNativeQuery(sql)
                .setParameter("id", id).setParameter("operatorId", operatorId)
                .getSingleResult();
        return result.longValue() > 0;
    }

    private long nativeCount(String sql, Long id) {
        return ((Number) entityManager.createNativeQuery(sql)
                .setParameter("id", id).getSingleResult()).longValue();
    }

    private Account findAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "존재하지 않는 계정입니다. ID: " + accountId
                        )
                );
    }
}
