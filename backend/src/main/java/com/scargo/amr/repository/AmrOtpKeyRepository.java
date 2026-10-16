package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrOtpKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AmrOtpKeyRepository extends JpaRepository<AmrOtpKey, Long> {
    Optional<AmrOtpKey> findFirstByActiveTrueOrderByKeyIdDesc();
    List<AmrOtpKey> findByActiveTrue();
}
