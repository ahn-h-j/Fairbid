package com.cos.fairbid.notification.adapter.out.websocket;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.util.Map;

/**
 * 커스텀 Actuator 엔드포인트: /actuator/wsconnections
 *
 * 현재 서버의 WebSocket 커넥션 수와 서버 정보를 반환한다.
 * Step 5 테스트에서 서버별 커넥션 쏠림을 측정하기 위해 사용.
 *
 * 응답 예시:
 * {
 *   "serverIp": "172.31.15.244",
 *   "activeConnections": 100
 * }
 */
@Component
@Endpoint(id = "wsconnections")
public class WebSocketConnectionsEndpoint {

    private final WebSocketSessionTracker sessionTracker;

    public WebSocketConnectionsEndpoint(WebSocketSessionTracker sessionTracker) {
        this.sessionTracker = sessionTracker;
    }

    @ReadOperation
    public Map<String, Object> connections() {
        String serverIp;
        try {
            serverIp = InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            serverIp = "unknown";
        }

        return Map.of(
                "serverIp", serverIp,
                "activeConnections", sessionTracker.getActiveConnectionCount()
        );
    }
}
