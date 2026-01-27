package com.cos.fairbid.common.pagination;

import java.util.List;

/**
 * 커서 기반 페이지 결과
 * 무한스크롤 페이지네이션에 사용되는 공통 타입
 *
 * @param items      현재 페이지 항목들
 * @param nextCursor 다음 페이지 커서 (null이면 마지막 페이지)
 * @param hasNext    다음 페이지 존재 여부
 */
public record CursorPage<T>(
        List<T> items,
        Long nextCursor,
        boolean hasNext
) {
}
