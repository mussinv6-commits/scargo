package com.scargo.repository;

import com.scargo.entity.LoadingLocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface LoadingLocationRepository extends JpaRepository<LoadingLocation, Long> {

    // 특정 야드 ID에 속한 모든 로딩 장소(섹터) 조회
    List<LoadingLocation> findByYardId(Long yardId);

    // 동일 야드 내에서 특정 섹터명이 이미 존재하는지 확인 (중복 등록 방지용)
    boolean existsByYardIdAndSector(Long yardId, String sector);

    // 특정 야드 내에서 가용 여부(isAvailable)에 따른 섹터 목록 조회
    List<LoadingLocation> findByYardIdAndIsAvailable(Long yardId, Boolean isAvailable);

    //특정 야드 내 가용 섹터를 알파벳/섹터명 순으로 정렬하여 조회 (드롭다운/옵션 목록용)
    List<LoadingLocation> findByYardIdAndIsAvailableOrderBySectorAsc(Long yardId, Boolean isAvailable);
}