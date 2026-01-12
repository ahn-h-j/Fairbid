/**
 * FairBid API 클라이언트
 * 백엔드 API 호출을 위한 래퍼 함수들
 */

const API_BASE = '/api/v1';

// 서버 시간과의 오프셋 (밀리초)
let serverTimeOffset = 0;

/**
 * 서버 시간 기준 현재 시간 반환
 * @returns {Date} 서버 시간 기준 현재 시간
 */
function getServerTime() {
    return new Date(Date.now() + serverTimeOffset);
}

/**
 * API 응답에서 서버 시간 오프셋 계산
 * @param {string} serverTimeStr - 서버 시간 문자열 (ISO 8601)
 */
function updateServerTimeOffset(serverTimeStr) {
    if (serverTimeStr) {
        const serverTime = new Date(serverTimeStr).getTime();
        serverTimeOffset = serverTime - Date.now();
    }
}

/**
 * API 요청 공통 함수
 * @param {string} endpoint - API 엔드포인트
 * @param {object} options - fetch 옵션
 * @returns {Promise<object>} API 응답 데이터
 */
async function apiRequest(endpoint, options = {}) {
    const url = `${API_BASE}${endpoint}`;

    const defaultHeaders = {
        'Content-Type': 'application/json',
    };

    const config = {
        ...options,
        headers: {
            ...defaultHeaders,
            ...options.headers,
        },
    };

    try {
        const response = await fetch(url, config);

        // JSON 파싱 시도
        let data;
        try {
            data = await response.json();
        } catch (parseError) {
            console.error('JSON 파싱 실패:', parseError);
            throw new ApiError('PARSE_ERROR', '서버 응답을 처리할 수 없습니다.');
        }

        // 서버 시간 오프셋 업데이트
        updateServerTimeOffset(data.serverTime);

        if (!data.success) {
            throw new ApiError(data.error.code, data.error.message);
        }

        return data.data;
    } catch (error) {
        if (error instanceof ApiError) {
            throw error;
        }
        throw new ApiError('NETWORK_ERROR', '네트워크 오류가 발생했습니다.');
    }
}

/**
 * API 에러 클래스
 */
class ApiError extends Error {
    constructor(code, message) {
        super(message);
        this.code = code;
        this.name = 'ApiError';
    }
}

// =====================================================
// 경매 API
// =====================================================

/**
 * 경매 상세 조회
 * @param {number} auctionId - 경매 ID
 * @returns {Promise<object>} 경매 상세 정보
 */
async function getAuctionDetail(auctionId) {
    return apiRequest(`/auctions/${auctionId}`);
}

/**
 * 경매 등록
 * @param {object} auctionData - 경매 등록 데이터
 * @returns {Promise<object>} 생성된 경매 정보
 */
async function createAuction(auctionData) {
    return apiRequest('/auctions', {
        method: 'POST',
        body: JSON.stringify(auctionData),
    });
}

// =====================================================
// 입찰 API
// =====================================================

/**
 * 입찰하기
 * @param {number} auctionId - 경매 ID
 * @param {object} bidData - 입찰 데이터 { amount, bidType }
 * @returns {Promise<object>} 입찰 결과
 */
async function placeBid(auctionId, bidData) {
    return apiRequest(`/auctions/${auctionId}/bids`, {
        method: 'POST',
        body: JSON.stringify(bidData),
    });
}

// =====================================================
// 유틸리티
// =====================================================

/**
 * 가격 포맷팅 (원화)
 * @param {number} price - 가격
 * @returns {string} 포맷팅된 가격 문자열
 */
function formatPrice(price) {
    return new Intl.NumberFormat('ko-KR').format(price) + '원';
}

/**
 * 남은 시간 계산
 * @param {string} endTimeStr - 종료 시간 문자열 (ISO 8601)
 * @returns {object} { hours, minutes, seconds, isExpired }
 */
function calculateRemainingTime(endTimeStr) {
    const endTime = new Date(endTimeStr).getTime();
    const now = getServerTime().getTime();
    const diff = endTime - now;

    if (diff <= 0) {
        return { hours: 0, minutes: 0, seconds: 0, isExpired: true };
    }

    const hours = Math.floor(diff / (1000 * 60 * 60));
    const minutes = Math.floor((diff % (1000 * 60 * 60)) / (1000 * 60));
    const seconds = Math.floor((diff % (1000 * 60)) / 1000);

    return { hours, minutes, seconds, isExpired: false };
}

/**
 * 남은 시간 포맷팅
 * @param {string} endTimeStr - 종료 시간 문자열
 * @returns {string} 포맷팅된 남은 시간
 */
function formatRemainingTime(endTimeStr) {
    const { hours, minutes, seconds, isExpired } = calculateRemainingTime(endTimeStr);

    if (isExpired) {
        return '종료됨';
    }

    const pad = (n) => n.toString().padStart(2, '0');
    return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`;
}
