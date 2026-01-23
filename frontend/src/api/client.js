const API_BASE = '/api/v1';

/**
 * 서버-클라이언트 시간 오프셋 (밀리초)
 * 모든 API 응답의 serverTime으로 갱신된다.
 */
let serverTimeOffset = 0;

/** 현재 테스트용 사용자 ID */
let currentUserId = 1;

/**
 * 서버 시간 기준의 현재 시각을 반환
 * @returns {Date} 서버 시간으로 보정된 현재 시각
 */
export function getServerTime() {
  return new Date(Date.now() + serverTimeOffset);
}

/**
 * 테스트용 사용자 ID 설정
 * @param {number} userId
 */
export function setCurrentUserId(userId) {
  currentUserId = userId;
}

/**
 * 현재 설정된 사용자 ID 반환
 * @returns {number}
 */
export function getCurrentUserId() {
  return currentUserId;
}

/**
 * API 에러 클래스
 * 서버에서 반환한 에러 코드와 메시지를 포함한다.
 */
export class ApiError extends Error {
  constructor(code, message) {
    super(message);
    this.code = code;
    this.name = 'ApiError';
  }
}

/**
 * API 요청 공통 래퍼
 * - X-User-Id 헤더 자동 주입
 * - serverTime 오프셋 자동 갱신
 * - 에러 시 ApiError throw
 *
 * @param {string} endpoint - API 엔드포인트 (예: "/auctions")
 * @param {RequestInit} options - fetch 옵션
 * @returns {Promise<any>} 응답 데이터 (data 필드)
 */
export async function apiRequest(endpoint, options = {}) {
  const url = `${API_BASE}${endpoint}`;
  const config = {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': currentUserId.toString(),
      ...options.headers,
    },
  };

  const response = await fetch(url, config);
  let data;
  try {
    data = await response.json();
  } catch {
    throw new ApiError('PARSE_ERROR', '서버 응답을 처리할 수 없습니다.');
  }

  // 서버 시간 오프셋 갱신
  if (data.serverTime) {
    serverTimeOffset = new Date(data.serverTime).getTime() - Date.now();
  }

  if (!data.success) {
    throw new ApiError(
      data.error?.code || 'UNKNOWN',
      data.error?.message || '알 수 없는 오류가 발생했습니다.'
    );
  }

  return data.data;
}

/**
 * SWR용 fetcher 함수
 * @param {string} endpoint - API 엔드포인트
 * @returns {Promise<any>}
 */
export const fetcher = (endpoint) => apiRequest(endpoint);
