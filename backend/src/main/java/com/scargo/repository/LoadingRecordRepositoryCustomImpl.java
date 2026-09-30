package com.scargo.repository;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.scargo.dto.LoadingRecordSearchCondition;
import com.scargo.entity.LoadingRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;

import java.util.List;

import static com.scargo.entity.QLoadingRecord.loadingRecord;

@RequiredArgsConstructor
public class LoadingRecordRepositoryCustomImpl implements LoadingRecordRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public Page<LoadingRecord> searchLoadingRecords(LoadingRecordSearchCondition condition, Pageable pageable) {
        List<LoadingRecord> content = queryFactory
                .selectFrom(loadingRecord)
                .where(
                        vehicleNoContains(condition.getVehicleNo()),
                        containerNoContains(condition.getContainerNo())
                )
                .offset(pageable.getOffset())
                .limit(pageable.getPageSize())
                .orderBy(loadingRecord.recordId.desc())
                .fetch();

        Long total = queryFactory
                .select(loadingRecord.count())
                .from(loadingRecord)
                .where(
                        vehicleNoContains(condition.getVehicleNo()),
                        containerNoContains(condition.getContainerNo())
                )
                .fetchOne();

        return new PageImpl<>(content, pageable, total != null ? total : 0L);
    }

    // 차량번호 조건 검색 (null 또는 빈값 시 조건 생략)
    private BooleanExpression vehicleNoContains(String vehicleNo) {
        return StringUtils.hasText(vehicleNo) ? loadingRecord.truck.vehicleNo.contains(vehicleNo) : null;
    }

    // 컨테이너번호 조건 검색 (null 또는 빈값 시 조건 생략)
    private BooleanExpression containerNoContains(String containerNo) {
        return StringUtils.hasText(containerNo) ? loadingRecord.container.containerNo.contains(containerNo) : null;
    }
}