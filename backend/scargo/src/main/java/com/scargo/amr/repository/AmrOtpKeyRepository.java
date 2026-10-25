package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrOtpKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AmrOtpKeyRepository extends JpaRepository<AmrOtpKey, Long> {
    Optional<AmrOtpKey> findFirstByActiveTrueOrderByKeyIdDesc();
    List<AmrOtpKey> findByActiveTrue();
    // 26.10.22 허브별 OTP 키: hub_id NULL = 관리자용 전체 허브 키
    Optional<AmrOtpKey> findFirstByActiveTrueAndHubIdIsNullOrderByKeyIdDesc();
    Optional<AmrOtpKey> findFirstByActiveTrueAndHubIdOrderByKeyIdDesc(Long hubId);
    List<AmrOtpKey> findByActiveTrueAndHubId(Long hubId);
    List<AmrOtpKey> findByActiveTrueOrderByKeyIdDesc();
}
