package com.cos.fairbid.cucumber.steps;

import com.cos.fairbid.cucumber.adapter.TestAdapter;
import com.cos.fairbid.cucumber.adapter.TestContext;
import io.cucumber.java.ko.그리고;
import io.cucumber.java.ko.만약;
import org.springframework.http.ResponseEntity;

import java.util.Map;

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
}
