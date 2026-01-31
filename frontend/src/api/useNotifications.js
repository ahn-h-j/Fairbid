import useSWR from 'swr';
import { apiRequest } from './client';

const fetcher = (url) => apiRequest(url);

/**
 * 내 알림 목록 조회
 */
export function useNotifications() {
  const { data, error, isLoading, mutate } = useSWR('/notifications', fetcher, {
    refreshInterval: 30000, // 30초마다 자동 새로고침
  });

  return {
    notifications: data || [],
    isLoading,
    isError: error,
    mutate,
  };
}

/**
 * 읽지 않은 알림 개수 조회
 */
export function useUnreadCount() {
  const { data, error, isLoading, mutate } = useSWR('/notifications/count', fetcher, {
    refreshInterval: 30000,
  });

  return {
    unreadCount: data?.unreadCount || 0,
    isLoading,
    isError: error,
    mutate,
  };
}

/**
 * 알림 읽음 처리
 */
export async function markAsRead(notificationId) {
  return apiRequest(`/notifications/${notificationId}/read`, {
    method: 'POST',
  });
}
