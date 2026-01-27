import { CATEGORIES, STATUSES, TRANSACTION_STATUSES } from './constants';

/**
 * 가격을 한국어 원화 형식으로 포맷
 * @param {number|null} price - 포맷할 가격
 * @returns {string} 포맷된 가격 문자열 (예: "1,000원")
 */
export function formatPrice(price) {
  if (price == null) return '-';
  return new Intl.NumberFormat('ko-KR').format(price) + '원';
}

/**
 * 카테고리 코드를 한글 라벨로 변환
 * @param {string} category - 카테고리 코드
 * @returns {string} 한글 카테고리명
 */
export function formatCategory(category) {
  return CATEGORIES[category] || category;
}

/**
 * 상태 코드를 한글 라벨로 변환
 * @param {string} status - 상태 코드
 * @returns {string} 한글 상태명
 */
export function formatStatus(status) {
  return STATUSES[status]?.label || status;
}

/**
 * 숫자를 2자리로 패딩
 * @param {number} n - 패딩할 숫자
 * @returns {string} 2자리 문자열
 */
export function padZero(n) {
  return n.toString().padStart(2, '0');
}

/**
 * 남은 시간을 HH:MM:SS 형식으로 포맷
 * @param {number} hours
 * @param {number} minutes
 * @param {number} seconds
 * @returns {string} "HH:MM:SS" 형식 문자열
 */
export function formatRemainingTime(hours, minutes, seconds) {
  return `${padZero(hours)}:${padZero(minutes)}:${padZero(seconds)}`;
}

/**
 * ISO 날짜 문자열을 한국어 날짜로 포맷
 * @param {string} dateStr - ISO 8601 날짜 문자열
 * @returns {string} 포맷된 날짜
 */
export function formatDate(dateStr) {
  if (!dateStr) return '-';
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(dateStr));
}

/**
 * 거래 상태 코드를 한글 라벨로 변환
 * @param {string} status - 거래 상태 코드
 * @returns {string} 한글 상태명
 */
export function formatTransactionStatus(status) {
  return TRANSACTION_STATUSES[status]?.label || status;
}

/**
 * 서버에서 반환한 날짜 문자열을 UTC Date 객체로 변환
 * 서버는 LocalDateTime을 타임존 없이 반환하므로, UTC로 해석한다.
 * @param {string} dateStr - 서버에서 반환한 날짜 문자열 (예: "2026-01-25T04:51:56")
 * @returns {Date|null} UTC로 해석된 Date 객체
 */
export function parseServerDate(dateStr) {
  if (!dateStr) return null;
  // 타임존 정보(Z 또는 +09:00 등)가 있는지 확인
  const hasTimezone = /([zZ]|[+-]\d{2}:\d{2})$/.test(dateStr);
  // 타임존 정보가 없으면 'Z'를 추가하여 UTC로 해석
  const normalized = hasTimezone ? dateStr : `${dateStr}Z`;
  return new Date(normalized);
}
