package com.cos.fairbid.auction.adapter.out.persistence.repository;

import com.cos.fairbid.auction.adapter.out.persistence.entity.AuctionEntity;
import com.cos.fairbid.auction.domain.AuctionStatus;
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
     * @param status  경매 상태 필터 (nullable)
     * @param keyword 검색어 - 상품명 (nullable)
     * @return Specification
     */
    public static Specification<AuctionEntity> withCondition(AuctionStatus status, String keyword) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();

            // status 필터링
            if (status != null) {
                predicates.add(criteriaBuilder.equal(root.get("status"), status));
            }

            // keyword 검색 (title LIKE %keyword%)
            if (keyword != null && !keyword.isBlank()) {
                predicates.add(criteriaBuilder.like(
                        criteriaBuilder.lower(root.get("title")),
                        "%" + keyword.toLowerCase() + "%"
                ));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
