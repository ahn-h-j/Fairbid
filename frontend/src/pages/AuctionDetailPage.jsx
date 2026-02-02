import { useState, useCallback, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useAuction } from '../api/useAuction';
import { placeBid } from '../api/mutations';
import { apiRequest } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import { useWebSocket } from '../hooks/useWebSocket';
import Timer from '../components/Timer';
import StatusBadge from '../components/StatusBadge';
import CategoryBadge from '../components/CategoryBadge';
import ImageGallery from '../components/ImageGallery';
import Alert from '../components/Alert';
import Spinner from '../components/Spinner';
import { BID_TYPES } from '../utils/constants';
import { formatPrice } from '../utils/formatters';

/**
 * 경매 상세 페이지
 * WebSocket으로 실시간 입찰 업데이트를 수신하고,
 * 원터치/직접/즉시구매 입찰을 처리한다.
 */
export default function AuctionDetailPage() {
  const { id: auctionId } = useParams();
  const { user } = useAuth();
  const { auction, isLoading, error, mutate } = useAuction(auctionId);

  const [bidAmount, setBidAmount] = useState('');
  const [bidLoading, setBidLoading] = useState(false);
  const [bidMessage, setBidMessage] = useState(null);
  const [extensionNotice, setExtensionNotice] = useState(false);

  // 입찰 여부 추적 (localStorage 기반으로 새로고침해도 유지)
  const bidStorageKey = `bid_${auctionId}`;
  const [hasBid, setHasBid] = useState(() => localStorage.getItem(bidStorageKey) === 'true');

  // 이전에 1순위였는지 추적 (WebSocket으로 밀렸을 때 감지용)
  const [wasFirstRank, setWasFirstRank] = useState(false);

  // 입찰 메시지 자동 해제 (3초)
  useEffect(() => {
    if (!bidMessage) return;
    const timer = setTimeout(() => setBidMessage(null), 3000);
    return () => clearTimeout(timer);
  }, [bidMessage]);

  // 연장 알림 자동 해제 (3초)
  useEffect(() => {
    if (!extensionNotice) return;
    const timer = setTimeout(() => setExtensionNotice(false), 3000);
    return () => clearTimeout(timer);
  }, [extensionNotice]);

  // 1순위 상태 추적 (API 응답 기준)
  useEffect(() => {
    if (auction?.userBidRank === 1) {
      setWasFirstRank(true);
    }
  }, [auction?.userBidRank]);

  // WebSocket: 입찰 업데이트 수신 시 SWR 캐시 직접 업데이트
  const handleBidUpdate = useCallback((msg) => {
    mutate((prev) => {
      if (!prev) return prev;

      const instantBuyEnabled = prev.instantBuyPrice
        ? msg.currentPrice < prev.instantBuyPrice * 0.9
        : false;

      // 현재 사용자의 입찰 순위 계산 (1순위 / 1순위 아님)
      let userBidRank = null;
      if (user?.userId && msg.topBidderId) {
        const isFirstRank = String(msg.topBidderId) === String(user.userId);
        userBidRank = isFirstRank ? 1 : null;

        // 이전에 1순위였으면 wasFirstRank 업데이트
        if (prev.userBidRank === 1 && !isFirstRank) {
          setWasFirstRank(true);
        }
      }

      return {
        ...prev,
        currentPrice: msg.currentPrice,
        scheduledEndTime: msg.scheduledEndTime,
        nextMinBidPrice: msg.nextMinBidPrice,
        bidIncrement: msg.bidIncrement,
        totalBidCount: msg.totalBidCount,
        instantBuyEnabled,
        userBidRank,
      };
    }, { revalidate: false });

    if (msg.extended) {
      setExtensionNotice(true);
    }
  }, [mutate, user?.userId]);

  // WebSocket: 경매 종료 수신 - 서버에서 최신 데이터(winnerId, userWinningRank 등) 다시 가져오기
  const handleAuctionClosed = useCallback(() => {
    // 백엔드 처리 완료 후 최신 데이터 fetch (약간의 딜레이)
    setTimeout(() => {
      mutate();
    }, 500);
  }, [mutate]);

  useWebSocket(auctionId, {
    onBidUpdate: handleBidUpdate,
    onAuctionClosed: handleAuctionClosed,
  });

  // 입찰 처리 공통 로직
  const handlePlaceBid = async (bidType, amount) => {
    setBidLoading(true);
    setBidMessage(null);

    try {
      const bidData = { bidType };
      if (bidType === BID_TYPES.DIRECT) {
        bidData.amount = amount;
      }
      await placeBid(auctionId, bidData);
      setBidMessage({ type: 'success', message: '입찰이 완료되었습니다!' });
      setBidAmount('');

      // 입찰 성공 시 1순위로 낙관적 업데이트 (내가 입찰하면 1순위가 됨)
      setHasBid(true);
      setWasFirstRank(true);
      localStorage.setItem(bidStorageKey, 'true');
      mutate((prev) => prev ? { ...prev, userBidRank: 1 } : prev, { revalidate: false });

      // 즉시 구매 성공 시 상태를 낙관적으로 업데이트 (1시간 대기 상태)
      if (bidType === BID_TYPES.INSTANT_BUY) {
        mutate((prev) => prev ? { ...prev, status: 'INSTANT_BUY_PENDING', instantBuyEnabled: false } : prev, { revalidate: false });
      }
    } catch (err) {
      setBidMessage({ type: 'error', message: err.message || '입찰에 실패했습니다.' });
    } finally {
      setBidLoading(false);
    }
  };

  const handleOneTouchBid = () => handlePlaceBid(BID_TYPES.ONE_TOUCH);

  const handleDirectBid = (e) => {
    e.preventDefault();
    const amount = parseInt(bidAmount, 10);
    if (!amount || amount < (auction?.nextMinBidPrice || 0)) {
      setBidMessage({ type: 'error', message: `최소 ${formatPrice(auction?.nextMinBidPrice)} 이상 입력해주세요.` });
      return;
    }
    handlePlaceBid(BID_TYPES.DIRECT, amount);
  };

  const handleInstantBuy = () => handlePlaceBid(BID_TYPES.INSTANT_BUY);

  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner size="lg" />
      </div>
    );
  }

  if (error || !auction) {
    return (
      <div className="text-center py-24 animate-fade-in">
        <div className="w-16 h-16 mx-auto mb-4 bg-red-50 rounded-2xl flex items-center justify-center">
          <svg className="w-7 h-7 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
          </svg>
        </div>
        <p className="text-gray-700 font-semibold text-[15px]">경매 정보를 불러올 수 없습니다</p>
        <p className="text-sm text-gray-400 mt-1.5">{error?.message || '경매를 찾을 수 없습니다.'}</p>
      </div>
    );
  }

  const isBidding = auction.status === 'BIDDING' || auction.status === 'INSTANT_BUY_PENDING';
  const isInstantBuyPending = auction.status === 'INSTANT_BUY_PENDING';

  return (
    <div className="max-w-2xl mx-auto space-y-5 animate-fade-in">
      {/* 즉시 구매 대기 알림 */}
      {isInstantBuyPending ? (
        <div className="flex items-center gap-3 px-5 py-3.5 bg-blue-50 rounded-2xl ring-1 ring-blue-200/60 animate-slide-up">
          <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center shrink-0">
            <svg className="w-4 h-4 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <div>
            <p className="text-[13px] font-semibold text-blue-800">즉시 구매가 요청되었습니다</p>
            <p className="text-[11px] text-blue-600 mt-0.5">남은 시간 내에 더 높은 입찰이 가능합니다</p>
          </div>
        </div>
      ) : null}

      {/* 상품 이미지 갤러리 */}
      <div className="bg-white rounded-2xl p-4 ring-1 ring-black/[0.04]">
        <ImageGallery images={auction.imageUrls} alt={auction.title} />
      </div>

      {/* 경매 정보 헤더 */}
      <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04]">
        <div className="flex items-center gap-2 mb-3">
          <CategoryBadge category={auction.category} />
          <StatusBadge status={auction.status} />
        </div>
        <h1 className="text-lg sm:text-xl font-bold text-gray-900 leading-snug mb-1.5">{auction.title}</h1>
        {auction.description ? (
          <p className="text-[13px] text-gray-500 leading-relaxed">{auction.description}</p>
        ) : null}
      </div>

      {/* 타이머 + 현재가 */}
      <div className="bg-white rounded-2xl p-6 sm:p-8 ring-1 ring-black/[0.04] relative overflow-hidden">
        {/* 배경 장식 */}
        <div className="absolute -top-20 -right-20 w-40 h-40 bg-gradient-to-br from-blue-50 to-violet-50 rounded-full blur-2xl pointer-events-none" />

        {/* 타이머 */}
        <div className="relative text-center mb-6">
          <p className="text-[11px] text-gray-400 font-bold uppercase tracking-widest mb-3">남은 시간</p>
          <Timer endTime={auction.scheduledEndTime} />
        </div>

        {/* 연장 알림 */}
        {extensionNotice ? (
          <div className="mb-6 flex justify-center animate-slide-up">
            <div className="inline-flex items-center gap-2 px-4 py-2 bg-orange-50 text-orange-700 rounded-full text-[12px] font-semibold ring-1 ring-orange-200/60">
              <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
              경매가 연장되었습니다!
            </div>
          </div>
        ) : null}

        {/* 현재가 */}
        <div className="relative text-center pt-5 border-t border-gray-100">
          <p className="text-[11px] text-gray-400 font-bold uppercase tracking-widest mb-1.5">현재가</p>
          <p className="text-3xl sm:text-4xl font-extrabold text-gray-900 tabular-nums tracking-tight">
            {formatPrice(auction.currentPrice ?? auction.startPrice)}
          </p>
        </div>
      </div>

      {/* 가격 상세 정보 */}
      <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04]">
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          <InfoItem label="시작가" value={formatPrice(auction.startPrice)} />
          <InfoItem label="즉시 구매가" value={formatPrice(auction.instantBuyPrice)} />
          <InfoItem label="입찰 단위" value={formatPrice(auction.bidIncrement)} />
          <InfoItem label="입찰 수" value={`${auction.totalBidCount || 0}회`} />
        </div>

        <div className="mt-4 pt-3 border-t border-gray-50 flex items-center gap-4 text-[11px] text-gray-300 font-medium">
          <span>연장 {auction.extensionCount || 0}회</span>
          <span>경매 #{auction.id}</span>
        </div>
      </div>

      {/* 입찰 순위 표시 (진행 중인 경매에서 입찰한 사용자) */}
      {isBidding && user?.userId && (auction.userBidRank === 1 || hasBid || wasFirstRank) && (
        <div className={`flex items-center gap-3 px-5 py-3.5 rounded-2xl ring-1 animate-slide-up ${
          auction.userBidRank === 1
            ? 'bg-green-50 ring-green-200/60'
            : 'bg-gray-50 ring-gray-200/60'
        }`}>
          <div className={`w-8 h-8 rounded-lg flex items-center justify-center shrink-0 ${
            auction.userBidRank === 1 ? 'bg-green-100' : 'bg-gray-100'
          }`}>
            <span className="text-lg">
              {auction.userBidRank === 1 ? '🏆' : '📉'}
            </span>
          </div>
          <div>
            <p className={`text-[13px] font-semibold ${
              auction.userBidRank === 1 ? 'text-green-800' : 'text-gray-700'
            }`}>
              {auction.userBidRank === 1
                ? '회원님이 현재 1순위입니다'
                : '1순위가 아닙니다'}
            </p>
            <p className={`text-[11px] mt-0.5 ${
              auction.userBidRank === 1 ? 'text-green-600' : 'text-gray-500'
            }`}>
              {auction.userBidRank === 1
                ? '다른 입찰자가 나타나면 알림을 받습니다'
                : '더 높은 금액으로 입찰하여 1순위를 노려보세요'}
            </p>
          </div>
        </div>
      )}

      {/* 입찰 섹션 */}
      {isBidding ? (
        <div className="bg-white rounded-2xl p-5 sm:p-6 ring-1 ring-black/[0.04] space-y-4 animate-slide-up">
          <h2 className="text-[15px] font-bold text-gray-900 flex items-center gap-2">
            <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center">
              <svg className="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 7h8m0 0v8m0-8l-8 8-4-4-6 6" />
              </svg>
            </div>
            입찰하기
          </h2>

          {/* 입찰 메시지 */}
          {bidMessage ? (
            <Alert type={bidMessage.type} message={bidMessage.message} onClose={() => setBidMessage(null)} />
          ) : null}

          {/* 원터치 입찰 */}
          <button
            type="button"
            onClick={handleOneTouchBid}
            disabled={bidLoading}
            className="w-full py-3.5 bg-gradient-to-r from-blue-500 to-blue-600 text-white text-[14px] font-semibold rounded-xl hover:from-blue-600 hover:to-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-blue-500/20 hover:shadow-blue-500/30 btn-press"
          >
            {bidLoading ? '입찰 중…' : `원터치 입찰 (${formatPrice(auction.nextMinBidPrice)})`}
          </button>

          {/* 직접 입찰 */}
          <form onSubmit={handleDirectBid} className="flex gap-2">
            <div className="relative flex-1">
              <input
                type="number"
                value={bidAmount}
                onChange={(e) => setBidAmount(e.target.value)}
                placeholder={`최소 ${formatPrice(auction.nextMinBidPrice)}`}
                min={auction.nextMinBidPrice}
                className="w-full pl-4 pr-10 py-3 bg-gray-50 border-0 rounded-xl text-sm text-gray-900 placeholder-gray-400 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-500/40 input-glow transition-all"
                disabled={bidLoading}
                inputMode="numeric"
                aria-label="직접 입찰 금액"
              />
              <span className="absolute right-4 top-1/2 -translate-y-1/2 text-xs text-gray-400 font-medium">원</span>
            </div>
            <button
              type="submit"
              disabled={bidLoading || !bidAmount}
              className="px-6 py-3 bg-gray-900 text-white text-[13px] font-semibold rounded-xl hover:bg-gray-800 disabled:opacity-30 disabled:cursor-not-allowed transition-all btn-press shadow-sm"
            >
              입찰
            </button>
          </form>

          <p className="text-[11px] text-gray-400 text-center font-medium">
            최소 입찰가 {formatPrice(auction.nextMinBidPrice)} · 입찰 단위 {formatPrice(auction.bidIncrement)}
          </p>

          {/* 즉시 구매 버튼 */}
          {auction.instantBuyPrice ? (
            <div className="pt-3 border-t border-gray-100">
              <button
                type="button"
                onClick={handleInstantBuy}
                disabled={bidLoading || !auction.instantBuyEnabled}
                className={`w-full py-3.5 text-[14px] font-semibold rounded-xl transition-all btn-press ${
                  auction.instantBuyEnabled
                    ? 'bg-gradient-to-r from-orange-500 to-amber-500 text-white shadow-lg shadow-orange-500/20 hover:shadow-orange-500/30 hover:from-orange-600 hover:to-amber-600'
                    : 'bg-gray-100 text-gray-400 cursor-not-allowed shadow-none'
                }`}
              >
                {auction.instantBuyEnabled
                  ? `즉시 구매 (${formatPrice(auction.instantBuyPrice)})`
                  : `즉시 구매 불가 (현재가 90% 이상)`
                }
              </button>
            </div>
          ) : null}
        </div>
      ) : (
        /* 종료된 경매 - 거래 안내 UI */
        <EndedAuctionSection
          auction={auction}
          user={user}
        />
      )}
      {/* 테스트 도구 (관리자 전용) */}
      {user?.role === 'ADMIN' && <TestTools auctionId={auctionId} mutate={mutate} />}
    </div>
  );
}

/** 테스트 도구 패널 (개발용) */
function TestTools({ auctionId, mutate }) {
  const [seconds, setSeconds] = useState(300);
  const [testLoading, setTestLoading] = useState(false);
  const [testMessage, setTestMessage] = useState(null);

  const callTestApi = async (endpoint) => {
    setTestLoading(true);
    setTestMessage(null);
    try {
      const result = await apiRequest(endpoint, { method: 'POST' });
      setTestMessage({ type: 'success', message: result.message });
      mutate(); // 데이터 재조회
    } catch (err) {
      setTestMessage({ type: 'error', message: err.message });
    } finally {
      setTestLoading(false);
    }
  };

  return (
    <div className="bg-amber-50/50 rounded-2xl p-5 ring-1 ring-amber-200/60">
      <h3 className="text-[13px] font-bold text-amber-800 flex items-center gap-2 mb-3">
        <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.066 2.573c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.573 1.066c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.066-2.573c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z" />
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
        </svg>
        테스트 도구
      </h3>

      {testMessage ? (
        <div className={`mb-3 px-3 py-2 rounded-lg text-[12px] font-medium ${
          testMessage.type === 'success' ? 'bg-green-100 text-green-700' : 'bg-red-100 text-red-700'
        }`}>
          {testMessage.message}
        </div>
      ) : null}

      <div className="grid grid-cols-2 gap-2">
        <button
          type="button"
          onClick={() => callTestApi(`/test/auctions/${auctionId}/set-ending-soon`)}
          disabled={testLoading}
          className="px-3 py-2.5 bg-white text-amber-800 text-[12px] font-semibold rounded-xl ring-1 ring-amber-200 hover:bg-amber-100 disabled:opacity-50 transition-colors btn-press"
        >
          5분 후 마감
        </button>
        <button
          type="button"
          onClick={() => callTestApi(`/test/auctions/${auctionId}/force-close`)}
          disabled={testLoading}
          className="px-3 py-2.5 bg-white text-red-700 text-[12px] font-semibold rounded-xl ring-1 ring-red-200 hover:bg-red-50 disabled:opacity-50 transition-colors btn-press"
        >
          강제 종료
        </button>
        <button
          type="button"
          onClick={() => callTestApi(`/test/auctions/${auctionId}/refresh-cache`)}
          disabled={testLoading}
          className="px-3 py-2.5 bg-white text-blue-700 text-[12px] font-semibold rounded-xl ring-1 ring-blue-200 hover:bg-blue-50 disabled:opacity-50 transition-colors btn-press"
        >
          캐시 새로고침
        </button>
        <div className="flex gap-1.5">
          <input
            type="number"
            value={seconds}
            onChange={(e) => setSeconds(parseInt(e.target.value, 10) || 0)}
            className="w-16 px-2 py-2.5 bg-white text-[12px] text-center rounded-xl ring-1 ring-amber-200 focus:outline-none focus:ring-2 focus:ring-amber-400"
            min={1}
            aria-label="종료까지 초"
          />
          <button
            type="button"
            onClick={() => callTestApi(`/test/auctions/${auctionId}/set-end-time?seconds=${seconds}`)}
            disabled={testLoading}
            className="flex-1 px-2 py-2.5 bg-white text-amber-800 text-[12px] font-semibold rounded-xl ring-1 ring-amber-200 hover:bg-amber-100 disabled:opacity-50 transition-colors btn-press"
          >
            {seconds}초 후
          </button>
        </div>
      </div>
    </div>
  );
}

/** 정보 항목 (가격 상세용) */
function InfoItem({ label, value }) {
  return (
    <div className="flex flex-col gap-0.5">
      <span className="text-[11px] text-gray-400 font-semibold uppercase tracking-wider">{label}</span>
      <span className="text-[14px] font-bold text-gray-900 tabular-nums">{value}</span>
    </div>
  );
}

/**
 * 종료된 경매 섹션 컴포넌트
 * 사용자 역할에 따라 다른 메시지 표시:
 * - 1순위 낙찰자: "축하합니다! 낙찰되었습니다"
 * - 2순위 낙찰자: "2순위 낙찰자입니다"
 * - 판매자: "경매가 종료되었습니다" + 낙찰 정보
 * - 그 외: "아쉽게도 낙찰되지 않았습니다" 또는 "이 경매는 종료되었습니다"
 */
function EndedAuctionSection({ auction, user }) {
  const isLoggedIn = !!user?.userId;
  const isSeller = isLoggedIn && String(auction.sellerId) === String(user.userId);
  const hasFinalPrice = !!auction.finalPrice;

  // API에서 반환하는 userWinningRank, userWinningStatus 사용
  const userWinningRank = auction.userWinningRank;
  const userWinningStatus = auction.userWinningStatus;

  // 1순위 노쇼된 경우
  if (userWinningRank === 1 && userWinningStatus === 'NO_SHOW') {
    return (
      <div className="bg-gradient-to-br from-red-50 to-orange-50 rounded-2xl p-6 ring-1 ring-red-200/60 animate-fade-in">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-3 bg-red-100 rounded-2xl ring-1 ring-red-300/60 flex items-center justify-center">
            <span className="text-3xl">⚠️</span>
          </div>
          <p className="text-red-700 font-bold text-[18px]">노쇼 처리되었습니다</p>
          <p className="text-[13px] text-red-600 mt-1">
            응답 기한이 만료되어 낙찰 권한이 2순위에게 넘어갔습니다.
          </p>
          <p className="text-[12px] text-red-500 mt-2">
            경고 1회가 부여되었습니다.
          </p>
        </div>
      </div>
    );
  }

  // 1순위 낙찰자 (정상)
  if (userWinningRank === 1) {
    return (
      <div className="bg-gradient-to-br from-green-50 to-emerald-50 rounded-2xl p-6 ring-1 ring-green-200/60 animate-fade-in">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-3 bg-green-100 rounded-2xl ring-1 ring-green-300/60 flex items-center justify-center">
            <span className="text-3xl">🎉</span>
          </div>
          <p className="text-green-700 font-bold text-[18px]">축하합니다! 낙찰되었습니다</p>
          <p className="text-[14px] text-green-600 mt-1 font-semibold">
            낙찰가: {formatPrice(auction.finalPrice)}
          </p>
          <Link
            to="/trades"
            className="inline-block mt-4 px-6 py-3 bg-gradient-to-r from-green-500 to-emerald-500 text-white text-[14px] font-semibold rounded-xl hover:from-green-600 hover:to-emerald-600 transition-all shadow-lg shadow-green-500/20 hover:shadow-green-500/30 btn-press"
          >
            거래 진행하기
          </Link>
        </div>
      </div>
    );
  }

  // 2순위 승계됨 (PENDING_RESPONSE or RESPONDED)
  if (userWinningRank === 2 && (userWinningStatus === 'PENDING_RESPONSE' || userWinningStatus === 'RESPONDED')) {
    return (
      <div className="bg-gradient-to-br from-green-50 to-emerald-50 rounded-2xl p-6 ring-1 ring-green-200/60 animate-fade-in">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-3 bg-green-100 rounded-2xl ring-1 ring-green-300/60 flex items-center justify-center">
            <span className="text-3xl">🎉</span>
          </div>
          <p className="text-green-700 font-bold text-[18px]">축하합니다! 2순위로 낙찰되었습니다</p>
          <p className="text-[13px] text-green-600 mt-1">
            1순위 낙찰자가 응답하지 않아 낙찰 권한이 승계되었습니다.
          </p>
          <p className="text-[14px] text-green-600 mt-1 font-semibold">
            낙찰가: {formatPrice(auction.finalPrice)}
          </p>
          <Link
            to="/trades"
            className="inline-block mt-4 px-6 py-3 bg-gradient-to-r from-green-500 to-emerald-500 text-white text-[14px] font-semibold rounded-xl hover:from-green-600 hover:to-emerald-600 transition-all shadow-lg shadow-green-500/20 hover:shadow-green-500/30 btn-press"
          >
            거래 진행하기
          </Link>
        </div>
      </div>
    );
  }

  // 2순위 대기 중 (STANDBY)
  if (userWinningRank === 2 && userWinningStatus === 'STANDBY') {
    return (
      <div className="bg-gradient-to-br from-amber-50 to-yellow-50 rounded-2xl p-6 ring-1 ring-amber-200/60 animate-fade-in">
        <div className="text-center">
          <div className="w-16 h-16 mx-auto mb-3 bg-amber-100 rounded-2xl ring-1 ring-amber-300/60 flex items-center justify-center">
            <span className="text-3xl">⏳</span>
          </div>
          <p className="text-amber-700 font-bold text-[18px]">2순위 대기 중입니다</p>
          <p className="text-[13px] text-amber-600 mt-1">
            1순위 낙찰자가 거래를 진행하지 않으면 낙찰 기회가 주어집니다.
          </p>
          <p className="text-[12px] text-amber-500 mt-2">
            알림을 확인해주세요!
          </p>
        </div>
      </div>
    );
  }

  // 2순위 실패 (FAILED)
  if (userWinningRank === 2 && userWinningStatus === 'FAILED') {
    return (
      <div className="bg-gray-50 rounded-2xl p-6 ring-1 ring-black/[0.04] animate-fade-in">
        <div className="text-center">
          <div className="w-14 h-14 mx-auto mb-3 bg-gray-100 rounded-2xl ring-1 ring-gray-200/60 flex items-center justify-center">
            <span className="text-2xl">😢</span>
          </div>
          <p className="text-gray-600 font-bold text-[16px]">거래가 성사되지 않았습니다</p>
          <p className="text-[13px] text-gray-400 mt-1">
            이 경매는 유찰되었습니다.
          </p>
        </div>
      </div>
    );
  }

  // 판매자
  if (isSeller) {
    return (
      <div className="bg-white rounded-2xl p-6 ring-1 ring-black/[0.04] animate-fade-in">
        <div className="text-center">
          <div className="w-14 h-14 mx-auto mb-3 bg-blue-50 rounded-2xl ring-1 ring-blue-200/60 flex items-center justify-center">
            <svg className="w-6 h-6 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <p className="text-gray-700 font-bold text-[16px]">경매가 종료되었습니다</p>
          <p className="text-[13px] text-gray-500 mt-1">
            {hasFinalPrice ? `낙찰가: ${formatPrice(auction.finalPrice)}` : '낙찰자가 없습니다 (유찰)'}
          </p>
          {hasFinalPrice && (
            <Link
              to="/trades"
              className="inline-block mt-4 px-6 py-3 bg-gradient-to-r from-blue-500 to-blue-600 text-white text-[14px] font-semibold rounded-xl hover:from-blue-600 hover:to-blue-700 transition-all shadow-lg shadow-blue-500/20 hover:shadow-blue-500/30 btn-press"
            >
              거래 진행하기
            </Link>
          )}
        </div>
      </div>
    );
  }

  // 로그인한 사용자 (입찰했으나 낙찰 실패)
  if (isLoggedIn && hasFinalPrice) {
    return (
      <div className="bg-gray-50 rounded-2xl p-6 ring-1 ring-black/[0.04] animate-fade-in">
        <div className="text-center">
          <div className="w-14 h-14 mx-auto mb-3 bg-gray-100 rounded-2xl ring-1 ring-gray-200/60 flex items-center justify-center">
            <span className="text-2xl">😢</span>
          </div>
          <p className="text-gray-600 font-bold text-[16px]">아쉽게도 낙찰되지 않았습니다</p>
          <p className="text-[13px] text-gray-400 mt-1">
            최종 낙찰가: {formatPrice(auction.finalPrice)}
          </p>
          <Link
            to="/"
            className="inline-block mt-4 px-5 py-2.5 bg-gray-200 text-gray-700 text-[13px] font-semibold rounded-xl hover:bg-gray-300 transition-colors"
          >
            다른 경매 둘러보기
          </Link>
        </div>
      </div>
    );
  }

  // 비로그인 또는 유찰된 경매
  return (
    <div className="bg-gray-50 rounded-2xl p-8 text-center ring-1 ring-black/[0.04] animate-fade-in">
      <div className="w-14 h-14 mx-auto mb-3 bg-white rounded-2xl ring-1 ring-gray-200/60 flex items-center justify-center shadow-sm">
        <svg className="w-6 h-6 text-gray-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
        </svg>
      </div>
      <p className="text-gray-600 font-semibold text-[15px]">이 경매는 종료되었습니다</p>
      {hasFinalPrice && (
        <p className="text-[12px] text-gray-400 mt-1">최종 낙찰가: {formatPrice(auction.finalPrice)}</p>
      )}
    </div>
  );
}
