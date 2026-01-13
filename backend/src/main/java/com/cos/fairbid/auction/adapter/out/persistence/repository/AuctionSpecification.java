package com.cos.fairbid.auction.adapter.out.persistence.repository;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.application.port.in.AuctionSearchCondition;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

/**
 * 경매 목록 조회용 동적 쿼리 Specification
 */
public class AuctionSpecification {

    private AuctionSpecification() {
        // 유틸리티 클래스 - 인스턴스화 방지
    }

    /**
     * 검색 조건에 따른 Specification 생성
     *
     * @param condition 검색 조건
     * @return Specification
     */
    public static Specification<AuctionEntity> withCondition(AuctionSearchCondition condition) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // status 필터링
            if (condition.hasStatus()) {
                predicates.add(criteriaBuilder.equal(root.get("status"), condition.status()));
            }

            // keyword 검색 (title LIKE %keyword%)
            if (condition.hasKeyword()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        "%" + condition.keyword().toLowerCase() + "%"
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
