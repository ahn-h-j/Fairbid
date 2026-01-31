package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 알림 관련 Step Definitions
 */
public class NotificationSteps {

    private final TestAdapter testAdapter;
    private final TestContext testContext;

    public NotificationSteps(TestAdapter testAdapter, TestContext testContext) {
        this.testAdapter = testAdapter;
        this.testContext = testContext;
    }

    @그리고("인증된 사용자이다")
    public void 인증된_사용자이다() {
        // TestSecurityConfig에서 자동으로 인증 처리됨
    }

    @만약("알림 목록을 조회한다")
    public void 알림_목록을_조회한다() {
        ResponseEntity<Map> response = testAdapter.get("/api/v1/notifications", Map.class);
        testContext.setLastResponse(response);
    }

    @만약("읽지 않은 알림 개수를 조회한다")
    public void 읽지_않은_알림_개수를_조회한다() {
        ResponseEntity<Map> response = testAdapter.get("/api/v1/notifications/count", Map.class);
        testContext.setLastResponse(response);
    }

    @만약("{string} 알림을 읽음 처리한다")
    public void 알림을_읽음_처리한다(String notificationId) {
        ResponseEntity<Map> response = testAdapter.post(
                "/api/v1/notifications/" + notificationId + "/read",
                null,
                Map.class
        );
        testContext.setLastResponse(response);
    }

    @그리고("응답 본문의 목록 크기는 {int}이다")
    public void 응답_본문의_목록_크기는_이다(int expectedSize) {
        ResponseEntity<Map> response = testContext.getLastResponse();
        Map<String, Object> body = response.getBody();
        assertThat(body).isNotNull();

        // data 필드가 List인 경우 직접 검사
        Object data = body.get("data");
        if (data instanceof List) {
            assertThat((List<?>) data).hasSize(expectedSize);
        } else {
            // 기존 CommonSteps의 content 구조 사용
            throw new AssertionError("응답 data가 List 타입이 아닙니다: " + data);
        }
    }
}
