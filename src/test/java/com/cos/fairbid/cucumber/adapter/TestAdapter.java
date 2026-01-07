package com.cos.fairbid.cucumber.adapter;

import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * 테스트 코드에서 API를 호출하거나 내부 상태를 검증하기 위한 추상화 계층.
 * 비즈니스 로직과 테스트 코드의 결합도를 낮추고 재사용성을 높인다.
 */
public interface TestAdapter {

    /**
     * GET 요청을 수행한다.
     */
    <T> ResponseEntity<T> get(String path, Class<T> responseType);

    /**
     * POST 요청을 수행한다.
     */
    <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType);

    /**
     * PUT 요청을 수행한다.
     */
    <T> ResponseEntity<T> put(String path, Object body, Class<T> responseType);

    /**
     * DELETE 요청을 수행한다.
     */
    <T> ResponseEntity<T> delete(String path, Class<T> responseType);

    /**
     * Map 형태로 GET 요청 결과를 반환한다.
     */
    @SuppressWarnings("unchecked")
    default ResponseEntity<Map<String, Object>> getAsMap(String path) {
        return (ResponseEntity<Map<String, Object>>) (ResponseEntity<?>) get(path, Map.class);
    }
}
