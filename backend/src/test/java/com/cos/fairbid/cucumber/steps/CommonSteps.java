package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.Before;
import io.cucumber.java.ko.그러면;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import io.cucumber.java.ko.조건;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 공통적으로 사용되는 Step Definitions.
 */
public class CommonSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    public CommonSteps(TestAdapter testAdapter, TestContext testContext) {
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

    @그러면("^응답 본문의 \"([^\"]*)\" 값은 \"([^\"]*)\"이다$")
    public void 응답_본문의_값은_이다(String key, String expectedValue) {
        // Then: 응답 본문 필드 검증 (String)
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        Object actualValue = findValue(body, key);
        assertThat(actualValue).asString().isEqualTo(expectedValue);
    }

    @그러면("^응답 본문의 \"([^\"]*)\" 값은 ([0-9]+)이다$")
    public void 응답_본문의_값은_숫자이다(String key, long expectedValue) {
        // Then: 응답 본문 필드 검증 (Number)
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        Object actualValue = findValue(body, key);

        if (actualValue instanceof Number) {
             assertThat(((Number) actualValue).longValue()).isEqualTo(expectedValue);
        } else {
             // Fallback
             assertThat(actualValue).isEqualTo(expectedValue);
        }
    }

    @그러면("^응답 본문의 \"([^\"]*)\" 값은 (?:불리언 )?(true|false)이다$")
    public void 응답_본문의_값은_불리언이다(String key, String expectedValue) {
        // Then: 응답 본문 필드 검증 (Boolean)
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        Object actualValue = findValue(body, key);

        if ("true".equalsIgnoreCase(expectedValue) || "false".equalsIgnoreCase(expectedValue)) {
            boolean expected = Boolean.parseBoolean(expectedValue);
            assertThat(actualValue).isEqualTo(expected);
        } else {
            // String 비교로 fallback
            assertThat(actualValue).asString().isEqualTo(expectedValue);
        }
    }

    @그리고("응답 본문의 목록 크기는 {int}이다")
    public void 응답_본문의_목록_크기는_이다(int expectedSize) {
        // Then: 페이지 응답의 content 목록 크기 검증
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        List<?> content = extractContentList(body);
        assertThat(content).hasSize(expectedSize);
    }

    @그리고("응답 본문의 목록 크기는 {int} 이상이다")
    public void 응답_본문의_목록_크기는_이상이다(int minSize) {
        // Then: 페이지 응답의 content 목록 최소 크기 검증
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        List<?> content = extractContentList(body);
        assertThat(content).hasSizeGreaterThanOrEqualTo(minSize);
    }

    /**
     * 응답 본문에서 content 목록을 추출한다.
     * Page 응답 구조: { data: { content: [...] } }
     *
     * @throws AssertionError 응답 구조가 기대와 다를 경우
     */
    @SuppressWarnings("unchecked")
    private List<?> extractContentList(Map<String, Object> body) {
        // data 필드 검증
        assertThat(body.get("data"))
                .as("응답 body.data가 존재하고 Map 타입이어야 합니다")
                .isInstanceOf(Map.class);

        Map<String, Object> data = (Map<String, Object>) body.get("data");

        // content 필드 검증
        assertThat(data.get("content"))
                .as("응답 body.data.content가 존재하고 List 타입이어야 합니다")
                .isInstanceOf(List.class);

        return (List<?>) data.get("content");
    }

    @SuppressWarnings("unchecked")
    private Object findValue(Map<String, Object> body, String key) {
        // 1. 최상위 레벨 검색
        if (body.containsKey(key)) {
            return body.get(key);
        }

        // 2. data 객체 내부 검색
        if (body.containsKey("data") && body.get("data") instanceof Map) {
            Map<String, Object> data = (Map<String, Object>) body.get("data");
            if (data.containsKey(key)) {
                return data.get(key);
            }
        }

        // 3. error 객체 내부 검색
        if (body.containsKey("error") && body.get("error") instanceof Map) {
            Map<String, Object> error = (Map<String, Object>) body.get("error");
            // "errorCode"는 "code"로 매핑해서 검색
            String searchKey = "errorCode".equals(key) ? "code" : key;
            if (error.containsKey(searchKey)) {
                return error.get(searchKey);
            }
        }

        return null;
    }
}