package com.scargo.repository;

import com.scargo.entity.Gate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GateRepository extends JpaRepository<Gate, Integer> {

    // 게이트 코드(gate_code)로 조회 - gate_api.py/gate_watch_service.py 등
    // 클라이언트가 매번 바뀔 수 있는 gate_id(SERIAL) 숫자값 대신 사람이 읽기
    // 쉬운 코드로 게이트를 지정할 수 있게 함.
    Optional<Gate> findByGateCode(String gateCode);
}
