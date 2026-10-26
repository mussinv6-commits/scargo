package com.scargo.repository;

import com.scargo.dto.LoadingRecordSearchCondition;
import com.scargo.entity.LoadingRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

// 26.10.20: QueryDSL(QLoadingRecord 생성 클래스) → JPQL 로 교체.
//   IDE(Eclipse/IntelliJ)에서 Q클래스 생성 경로를 인식하지 못해 컴파일 오류가 나던 문제 제거. 동작(조건/정렬/페이징)은 동일
@RequiredArgsConstructor
public class LoadingRecordRepositoryCustomImpl implements LoadingRecordRepositoryCustom {

    private final EntityManager em;

    @Override
    public Page<LoadingRecord> searchLoadingRecords(LoadingRecordSearchCondition condition, Pageable pageable) {
        String vehicleNo = condition.getVehicleNo();
        String containerNo = condition.getContainerNo();
        StringBuilder where = new StringBuilder(" where 1=1");
        // 차량번호/컨테이너번호 조건 검색 (null 또는 빈값 시 조건 생략)
        if (StringUtils.hasText(vehicleNo)) where.append(" and lr.truck.vehicleNo like :vehicleNo");
        if (StringUtils.hasText(containerNo)) where.append(" and lr.container.containerNo like :containerNo");

        TypedQuery<LoadingRecord> q = em.createQuery(
                "select lr from LoadingRecord lr" + where + " order by lr.recordId desc", LoadingRecord.class);
        TypedQuery<Long> cq = em.createQuery(
                "select count(lr) from LoadingRecord lr" + where, Long.class);
        if (StringUtils.hasText(vehicleNo)) { q.setParameter("vehicleNo", "%" + vehicleNo + "%"); cq.setParameter("vehicleNo", "%" + vehicleNo + "%"); }
        if (StringUtils.hasText(containerNo)) { q.setParameter("containerNo", "%" + containerNo + "%"); cq.setParameter("containerNo", "%" + containerNo + "%"); }

        List<LoadingRecord> content = q.setFirstResult((int) pageable.getOffset())
                .setMaxResults(pageable.getPageSize()).getResultList();
        return new PageImpl<>(content, pageable, cq.getSingleResult());
    }
}
