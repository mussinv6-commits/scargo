package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrSafetyAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AmrSafetyAlertRepository extends JpaRepository<AmrSafetyAlert, Long> {
    Page<AmrSafetyAlert> findAllByOrderByStartedAtDesc(Pageable pageable);

    /** 26.11.02: :q 가 null 일 때 PG 가 lower(bytea) 로 바인딩되며 500 나던 문제 → SpEL 로 null 분기 */
    @Query("""
        SELECT a FROM AmrSafetyAlert a
         WHERE (:#{#type == null} = true OR a.eventType = :type)
           AND (:#{#q == null} = true OR (
                  LOWER(a.title) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(a.message,'')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(a.equipment,'')) LIKE LOWER(CONCAT('%', :q, '%'))
               OR LOWER(COALESCE(a.zoneLabel,'')) LIKE LOWER(CONCAT('%', :q, '%'))
           ))
         ORDER BY a.startedAt DESC
        """)
    Page<AmrSafetyAlert> search(@Param("type") String type, @Param("q") String q, Pageable pageable);

    Optional<AmrSafetyAlert> findFirstByEventTypeAndActiveTrueOrderByStartedAtDesc(String eventType);

    Optional<AmrSafetyAlert> findFirstByEventTypeAndCodeAndActiveTrueOrderByStartedAtDesc(String eventType, String code);

    List<AmrSafetyAlert> findByEventTypeAndActiveTrue(String eventType);

    long countByEventType(String eventType);
}
