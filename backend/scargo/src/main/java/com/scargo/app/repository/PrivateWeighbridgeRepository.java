package com.scargo.app.repository;

import com.scargo.app.entity.PrivateWeighbridge;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** 26.10.20: 통합 DB 에 private_weighbridges 가 없어 빈 결과를 돌려주는 대체 구현 (API 응답 형태는 유지).
 *  TODO(DB 설계): 민간계량소 테이블이 정해지면 JpaRepository 로 되돌린다 */
@Component
public class PrivateWeighbridgeRepository {
    public List<PrivateWeighbridge> findByStatusAndLatitudeBetweenAndLongitudeBetween(String status, BigDecimal a, BigDecimal b, BigDecimal c, BigDecimal d) { return List.of(); }
    public List<PrivateWeighbridge> search(String q, Pageable pageable) { return List.of(); }
    public Optional<PrivateWeighbridge> findById(Long id) { return Optional.empty(); }
}
