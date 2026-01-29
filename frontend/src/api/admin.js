import { apiRequest } from './client';

/**
 * 관리자 API 클라이언트
 * 관리자 전용 API 호출 함수를 제공한다.
 */

// ========== 통계 API ==========

/**
 * 통계 개요 조회
 * @param {number|null} days - 조회 기간 (7, 30, null=전체)
 * @returns {Promise<{
 *   totalAuctions: number,
 *   completedRate: number,
 *   avgBidCount: number,
 *   avgPriceIncreaseRate: number,
 *   extensionRate: number
 * }>}
 */
export function getStatsOverview(days = null) {
  const query = days ? `?days=${days}` : '';
  return apiRequest(`/admin/stats/overview${query}`);
}

/**
 * 일별 경매 통계 조회
 * @param {number|null} days - 조회 기간 (7, 30, null=전체)
 * @returns {Promise<{
 *   dailyStats: Array<{
 *     date: string,
 *     newAuctions: number,
 *     completedAuctions: number,
 *     bids: number
 *   }>
 * }>}
 */
export function getDailyAuctionStats(days = null) {
  const query = days ? `?days=${days}` : '';
  return apiRequest(`/admin/stats/auctions${query}`);
}

/**
 * 시간대별 입찰 패턴 조회
 * @param {number|null} days - 조회 기간 (7, 30, null=전체)
 * @returns {Promise<{
 *   hourlyBidCounts: Array<{ hour: number, count: number }>,
 *   peakHour: number,
 *   peakCount: number
 * }>}
 */
export function getTimePattern(days = null) {
  const query = days ? `?days=${days}` : '';
  return apiRequest(`/admin/stats/time-pattern${query}`);
}

// ========== 경매 관리 API ==========

/**
 * 관리자용 경매 목록 조회
 * @param {Object} params - 조회 파라미터
 * @param {string|null} params.status - 경매 상태 필터
 * @param {string|null} params.keyword - 검색어
 * @param {number} params.page - 페이지 번호 (0부터)
 * @param {number} params.size - 페이지 크기
 * @returns {Promise<{
 *   content: Array<{
 *     id: number,
 *     title: string,
 *     thumbnailUrl: string|null,
 *     currentPrice: number,
 *     startPrice: number,
 *     totalBidCount: number,
 *     status: string,
 *     scheduledEndTime: string,
 *     createdAt: string,
 *     extensionCount: number,
 *     sellerId: number,
 *     sellerNickname: string
 *   }>,
 *   totalPages: number,
 *   totalElements: number,
 *   number: number
 * }>}
 */
export function getAdminAuctionList({ status = null, keyword = null, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams();
  if (status) params.append('status', status);
  if (keyword) params.append('keyword', keyword);
  params.append('page', page.toString());
  params.append('size', size.toString());
  return apiRequest(`/admin/auctions?${params.toString()}`);
}

// ========== 유저 관리 API ==========

/**
 * 유저 목록 조회
 * @param {Object} params - 조회 파라미터
 * @param {string|null} params.keyword - 검색어 (닉네임 또는 이메일)
 * @param {number} params.page - 페이지 번호 (0부터)
 * @param {number} params.size - 페이지 크기
 * @returns {Promise<{
 *   content: Array<{
 *     id: number,
 *     email: string,
 *     nickname: string|null,
 *     phoneNumber: string|null,
 *     provider: string,
 *     role: string,
 *     warningCount: number,
 *     isActive: boolean,
 *     isBlocked: boolean,
 *     isOnboarded: boolean,
 *     createdAt: string
 *   }>,
 *   totalPages: number,
 *   totalElements: number,
 *   number: number
 * }>}
 */
export function getAdminUserList({ keyword = null, page = 0, size = 20 } = {}) {
  const params = new URLSearchParams();
  if (keyword) params.append('keyword', keyword);
  params.append('page', page.toString());
  params.append('size', size.toString());
  return apiRequest(`/admin/users?${params.toString()}`);
}
