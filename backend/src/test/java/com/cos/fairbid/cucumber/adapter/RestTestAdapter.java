package com.cos.fairbid.cucumber.adapter;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * TestRestTemplate을 사용한 TestAdapter 구현체.
 * 실제 HTTP 요청을 통해 API를 테스트한다.
 */
@Component
public class RestTestAdapter implements TestAdapter {

    private final TestRestTemplate restTemplate;

    public RestTestAdapter(TestRestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Override
    public <T> ResponseEntity<T> get(String path, Class<T> responseType) {
        return restTemplate.getForEntity(path, responseType);
    }

    @Override
    public <T> ResponseEntity<T> post(String path, Object body, Class<T> responseType) {
        return restTemplate.postForEntity(path, body, responseType);
    }

    @Override
    public <T> ResponseEntity<T> put(String path, Object body, Class<T> responseType) {
        return restTemplate.exchange(path, HttpMethod.PUT, new HttpEntity<>(body), responseType);
    }

    @Override
    public <T> ResponseEntity<T> delete(String path, Class<T> responseType) {
        return restTemplate.exchange(path, HttpMethod.DELETE, null, responseType);
    }
}
