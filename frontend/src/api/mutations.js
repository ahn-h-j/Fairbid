import { apiRequest } from './client';

/**
 * 경매 등록 API 호출
 *
 * @param {object} auctionData - 경매 생성 데이터
 * @param {string} auctionData.title - 제목
 * @param {string} [auctionData.description] - 설명
 * @param {string} auctionData.category - 카테고리 코드
 * @param {number} auctionData.startPrice - 시작 가격
 * @param {number} [auctionData.instantBuyPrice] - 즉시 구매가
 * @param {string} auctionData.duration - 경매 기간 (HOURS_24/HOURS_48)
 * @returns {Promise<object>} 생성된 경매 응답
 */
export async function createAuction(auctionData) {
  return apiRequest('/auctions', {
    method: 'POST',
    body: JSON.stringify(auctionData),
  });
}

/**
 * 입찰 API 호출
 *
 * @param {string|number} auctionId - 경매 ID
 * @param {object} bidData - 입찰 데이터
 * @param {string} bidData.bidType - 입찰 타입 (ONE_TOUCH/DIRECT/INSTANT_BUY)
 * @param {number} [bidData.amount] - 입찰 금액 (DIRECT 타입 시 필수)
 * @returns {Promise<object>} 입찰 응답
 */
export async function placeBid(auctionId, bidData) {
  return apiRequest(`/auctions/${auctionId}/bids`, {
    method: 'POST',
    body: JSON.stringify(bidData),
  });
}

/**
 * 결제 처리 API 호출
 *
 * @param {string|number} transactionId - 거래 ID
 * @returns {Promise<object>} 결제 처리 결과
 */
export async function processPayment(transactionId) {
  return apiRequest(`/transactions/${transactionId}/payment`, {
    method: 'POST',
  });
}

/**
 * 거래 상세 조회 API 호출
 *
 * @param {string|number} transactionId - 거래 ID
 * @returns {Promise<object>} 거래 상세 정보
 */
export async function getTransaction(transactionId) {
  return apiRequest(`/transactions/${transactionId}`);
}

/**
 * 경매의 거래 정보 조회 API 호출
 *
 * @param {string|number} auctionId - 경매 ID
 * @returns {Promise<object>} 거래 상세 정보
 */
export async function getTransactionByAuctionId(auctionId) {
  return apiRequest(`/transactions/auction/${auctionId}`);
}
