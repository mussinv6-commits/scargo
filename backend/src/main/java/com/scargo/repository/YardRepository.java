package com.scargo.repository;

import com.scargo.entity.Yard;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface YardRepository extends JpaRepository<Yard, Long> {

    // 야드 이름으로 단건 조회
    Optional<Yard> findByYardName(String yardName);

    // 야드 이름 포함 여부 검색 (키워드 검색용)
    List<Yard> findByYardNameContaining(String yardName);

    // 야드 타입별 조회
    List<Yard> findByYardType(String yardType);

    // 사용 가능 여부별 조회
    List<Yard> findByIsAvailable(Boolean isAvailable);

    // 야드 상태별 조회 (AVAILABLE, MAINTENANCE, FULL 등)
    List<Yard> findByStatus(String status);

    // 사용 가능(isAvailable=true) 및 특정 상태인 야드 목록 조회
    List<Yard> findByIsAvailableTrueAndStatus(String status);
}