package com.cos.fairbid.cucumber.adapter;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Cucumber 시나리오 간 상태를 공유하기 위한 컨텍스트.
 * 시나리오 내에서 응답 데이터를 저장하고 검증에 활용한다.
 */
@Component
public class TestContext {

    private ResponseEntity<?> lastResponse;
    private Object lastRequestBody;

    public void setLastResponse(ResponseEntity<?> response) {
        this.lastResponse = response;
    }

    @SuppressWarnings("unchecked")
    public <T> ResponseEntity<T> getLastResponse() {
        return (ResponseEntity<T>) lastResponse;
    }

    public void setLastRequestBody(Object body) {
        this.lastRequestBody = body;
    }

    @SuppressWarnings("unchecked")
    public <T> T getLastRequestBody() {
        return (T) lastRequestBody;
    }

    public void reset() {
        this.lastResponse = null;
        this.lastRequestBody = null;
    }
}
