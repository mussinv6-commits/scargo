package com.scargo.amr.repository;

import com.scargo.amr.entity.AmrTelemetry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;

public interface AmrTelemetryRepository extends JpaRepository<AmrTelemetry, Long> {
    List<AmrTelemetry> findByRobotIdAndRecordedAtAfterOrderByRecordedAtAsc(Long robotId, OffsetDateTime after);

    // 26.10.15 KPI 가동률: 로봇별 (전체 기록 수, 작업 중 기록 수)
    @Query("select t.robotId, count(t), sum(case when t.state in :busy then 1 else 0 end) from AmrTelemetry t where t.recordedAt > :since group by t.robotId")
    List<Object[]> utilization(@Param("since") OffsetDateTime since, @Param("busy") Collection<String> busy);
}
