package com.cos.fairbid.notification.application.service;

import com.cos.fairbid.notification.application.port.in.NotificationQueryUseCase;
import com.cos.fairbid.notification.application.port.out.NotificationStoragePort;
import com.cos.fairbid.notification.domain.InAppNotification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 알림 서비스
 * 알림 조회 및 읽음 처리 비즈니스 로직
 */
@Service
@RequiredArgsConstructor
public class NotificationService implements NotificationQueryUseCase {

    private final NotificationStoragePort notificationStoragePort;

    @Override
    public List<InAppNotification> getNotifications(Long userId) {
        return notificationStoragePort.findByUserId(userId);
    }

    @Override
    public int countUnread(Long userId) {
        return notificationStoragePort.countUnread(userId);
    }

    @Override
    public void markAsRead(Long userId, String notificationId) {
        notificationStoragePort.markAsRead(userId, notificationId);
    }
}
