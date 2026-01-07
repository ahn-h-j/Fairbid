package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.조건;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HealthCheck 시나리오에 대한 Step Definitions.
 */
public class HealthCheckSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    public HealthCheckSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    @Before
    public void setUp() {
        // Given: 각 시나리오 시작 전 컨텍스트 초기화
        testContext.reset();
    }

    @조건("서버가 실행중이다")
    public void 서버가_실행중이다() {
        // Given: Spring Boot 테스트 서버가 자동으로 시작됨
        // 별도 작업 필요 없음
    }

    @만약("{string} 엔드포인트를 호출한다")
    public void 엔드포인트를_호출한다(String path) {
        // When: 지정된 엔드포인트로 GET 요청
        ResponseEntity<Map> response = testAdapter.get(path, Map.class);
        testContext.setLastResponse(response);
    }

    @그러면("응답 상태 코드는 {int}이다")
    public void 응답_상태_코드는_이다(int expectedStatusCode) {
        // Then: 응답 상태 코드 검증
        ResponseEntity<?> response = testContext.getLastResponse();
        assertThat(response.getStatusCode().value()).isEqualTo(expectedStatusCode);
    }

    @그러면("응답 본문의 {string} 값은 {string}이다")
    public void 응답_본문의_값은_이다(String key, String expectedValue) {
        // Then: 응답 본문 필드 검증
        ResponseEntity<Map> response = testContext.getLastResponse();
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get(key)).isEqualTo(expectedValue);
    }
}
