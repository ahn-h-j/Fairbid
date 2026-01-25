import { useState, useCallback, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import { apiRequest } from '../api/client';
import useInfiniteScroll from '../hooks/useInfiniteScroll';
import Spinner from '../components/Spinner';

/** 판매 탭 상태 필터 목록 */
const SALE_FILTERS = [
  { value: '', label: '전체' },
  { value: 'ACTIVE', label: '진행중' },
  { value: 'PAYMENT_PENDING', label: '결제대기' },
  { value: 'COMPLETED', label: '거래완료' },
  { value: 'NO_BID', label: '유찰' },
];

/**
 * 마이페이지
 * 프로필 섹션 + 판매/구매 탭으로 구성된다.
 */
export default function MyPage() {
  const { user, updateAuthFromToken, logout } = useAuth();
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState('sales');
  const [saleFilter, setSaleFilter] = useState('');
  const [isEditingNickname, setIsEditingNickname] = useState(false);
  const [editNickname, setEditNickname] = useState('');
  const [nicknameError, setNicknameError] = useState('');
  const [showDeleteModal, setShowDeleteModal] = useState(false);
  const [deleteError, setDeleteError] = useState('');
  const [profile, setProfile] = useState(null);
  const [profileLoading, setProfileLoading] = useState(true);

  const nicknameInputRef = useRef(null);
  const observerRef = useRef(null);
  const loadMoreRef = useRef(null);

  // 프로필 정보 로드
  useEffect(() => {
    const loadProfile = async () => {
      try {
        const data = await apiRequest('/users/me');
        setProfile(data);
      } catch {
        // 프로필 로드 실패 시 기본값 사용
      } finally {
        setProfileLoading(false);
      }
    };
    loadProfile();
  }, []);

  // 판매 목록 무한스크롤
  const {
    items: salesItems,
    isLoading: salesLoading,
    isLoadingMore: salesLoadingMore,
    hasMore: salesHasMore,
    loadMore: salesLoadMore,
  } = useInfiniteScroll('/users/me/auctions', { status: saleFilter });

  // 구매 목록 무한스크롤
  const {
    items: bidsItems,
    isLoading: bidsLoading,
    isLoadingMore: bidsLoadingMore,
    hasMore: bidsHasMore,
    loadMore: bidsLoadMore,
  } = useInfiniteScroll('/users/me/bids');

  // Intersection Observer로 무한스크롤 트리거
  useEffect(() => {
    if (observerRef.current) observerRef.current.disconnect();

    const currentLoadMore = activeTab === 'sales' ? salesLoadMore : bidsLoadMore;
    const currentHasMore = activeTab === 'sales' ? salesHasMore : bidsHasMore;

    if (!currentHasMore) return;

    observerRef.current = new IntersectionObserver(
      (entries) => {
        if (entries[0].isIntersecting) {
          currentLoadMore();
        }
      },
      { threshold: 0.1 }
    );

    if (loadMoreRef.current) {
      observerRef.current.observe(loadMoreRef.current);
    }

    return () => {
      if (observerRef.current) observerRef.current.disconnect();
    };
  }, [activeTab, salesLoadMore, bidsLoadMore, salesHasMore, bidsHasMore]);

  /** 닉네임 수정 시작 */
  const startEditNickname = () => {
    setEditNickname(profile?.nickname || user?.nickname || '');
    setNicknameError('');
    setIsEditingNickname(true);
    setTimeout(() => nicknameInputRef.current?.focus(), 0);
  };

  /** 닉네임 수정 저장 */
  const saveNickname = async () => {
    const trimmed = editNickname.trim();
    if (trimmed.length < 2 || trimmed.length > 20) {
      setNicknameError('닉네임은 2~20자로 입력해주세요.');
      return;
    }

    try {
      const result = await apiRequest('/users/me', {
        method: 'PUT',
        body: JSON.stringify({ nickname: trimmed }),
      });

      // 새 JWT로 인증 상태 갱신
      if (result.accessToken) {
        updateAuthFromToken(result.accessToken);
      }
      setProfile((prev) => (prev ? { ...prev, nickname: trimmed } : prev));
      setIsEditingNickname(false);
    } catch (err) {
      if (err.code === 'NICKNAME_DUPLICATE') {
        setNicknameError('이미 사용 중인 닉네임입니다.');
      } else {
        setNicknameError(err.message || '수정에 실패했습니다.');
      }
    }
  };

  /** 닉네임 수정 키보드 핸들러 */
  const handleNicknameKeyDown = (e) => {
    if (e.key === 'Enter') saveNickname();
    if (e.key === 'Escape') setIsEditingNickname(false);
  };

  /** 회원 탈퇴 처리 */
  const handleDeleteAccount = async () => {
    setDeleteError('');
    try {
      await apiRequest('/users/me', { method: 'DELETE' });
      setShowDeleteModal(false);
      await logout();
      navigate('/', { replace: true });
    } catch (err) {
      // 탈퇴 실패 시 에러 메시지 표시, 모달 유지
      setDeleteError(err.message || '탈퇴에 실패했습니다. 잠시 후 다시 시도해주세요.');
    }
  };

  /** 가격 포맷팅 */
  const formatPrice = useCallback((price) => {
    return new Intl.NumberFormat('ko-KR').format(price);
  }, []);

  if (profileLoading) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Spinner />
      </div>
    );
  }

  return (
    <div className="max-w-2xl mx-auto space-y-6">
      {/* 프로필 섹션 */}
      <section className="bg-white rounded-2xl border border-gray-100 p-6 shadow-sm">
        <h2 className="text-lg font-bold text-gray-900 mb-4">프로필</h2>
        <dl className="space-y-3">
          {/* 닉네임 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">닉네임</dt>
            <dd className="flex items-center gap-2">
              {isEditingNickname ? (
                <div className="flex items-center gap-1.5">
                  <input
                    ref={nicknameInputRef}
                    type="text"
                    value={editNickname}
                    onChange={(e) => {
                      setEditNickname(e.target.value);
                      setNicknameError('');
                    }}
                    onKeyDown={handleNicknameKeyDown}
                    maxLength={20}
                    spellCheck={false}
                    className="w-32 px-2 py-1 text-sm border border-gray-200 rounded-lg focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                    aria-label="닉네임 수정"
                    aria-invalid={!!nicknameError}
                  />
                  <button
                    type="button"
                    onClick={saveNickname}
                    className="px-2 py-1 text-xs font-semibold text-blue-600 hover:bg-blue-50 rounded-lg transition-colors duration-200"
                    aria-label="닉네임 저장"
                  >
                    저장
                  </button>
                  <button
                    type="button"
                    onClick={() => setIsEditingNickname(false)}
                    className="px-2 py-1 text-xs font-semibold text-gray-500 hover:bg-gray-100 rounded-lg transition-colors duration-200"
                    aria-label="닉네임 수정 취소"
                  >
                    취소
                  </button>
                </div>
              ) : (
                <>
                  <span className="text-sm font-semibold text-gray-900">
                    {profile?.nickname || user?.nickname}
                  </span>
                  <button
                    type="button"
                    onClick={startEditNickname}
                    className="text-xs text-blue-500 hover:text-blue-700 transition-colors duration-200"
                    aria-label="닉네임 수정"
                  >
                    수정
                  </button>
                </>
              )}
            </dd>
          </div>
          {nicknameError && (
            <p className="text-xs text-red-600 text-right" role="alert">{nicknameError}</p>
          )}

          {/* 이메일 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">이메일</dt>
            <dd className="text-sm text-gray-900">{profile?.email || '-'}</dd>
          </div>

          {/* 전화번호 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">전화번호</dt>
            <dd className="text-sm text-gray-900">{profile?.phoneNumber || '-'}</dd>
          </div>

          {/* 경고 횟수 */}
          <div className="flex items-center justify-between">
            <dt className="text-sm text-gray-500">경고</dt>
            <dd className="text-sm font-semibold">
              <span className={profile?.warningCount > 0 ? 'text-red-600' : 'text-gray-900'}>
                {profile?.warningCount ?? 0}/3
              </span>
            </dd>
          </div>
        </dl>
      </section>

      {/* 판매/구매 탭 */}
      <section className="bg-white rounded-2xl border border-gray-100 shadow-sm overflow-hidden">
        {/* 탭 헤더 */}
        <div className="flex border-b border-gray-100" role="tablist" aria-label="마이페이지 탭">
          <button
            type="button"
            id="tab-sales"
            onClick={() => setActiveTab('sales')}
            className={`flex-1 py-3 text-sm font-semibold text-center transition-colors duration-200 ${
              activeTab === 'sales'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            aria-selected={activeTab === 'sales'}
            aria-controls="tabpanel-sales"
            role="tab"
          >
            판매
          </button>
          <button
            type="button"
            id="tab-bids"
            onClick={() => setActiveTab('bids')}
            className={`flex-1 py-3 text-sm font-semibold text-center transition-colors duration-200 ${
              activeTab === 'bids'
                ? 'text-blue-600 border-b-2 border-blue-600'
                : 'text-gray-500 hover:text-gray-700'
            }`}
            aria-selected={activeTab === 'bids'}
            aria-controls="tabpanel-bids"
            role="tab"
          >
            구매
          </button>
        </div>

        {/* 판매 탭 콘텐츠 */}
        {activeTab === 'sales' && (
          <div role="tabpanel" id="tabpanel-sales" aria-labelledby="tab-sales">
            {/* 상태 필터 */}
            <div className="flex gap-2 p-4 overflow-x-auto">
              {SALE_FILTERS.map((filter) => (
                <button
                  key={filter.value}
                  type="button"
                  onClick={() => setSaleFilter(filter.value)}
                  className={`px-3 py-1.5 text-xs font-semibold rounded-lg whitespace-nowrap transition-colors duration-200 ${
                    saleFilter === filter.value
                      ? 'bg-blue-500 text-white'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                  aria-label={`${filter.label} 필터`}
                >
                  {filter.label}
                </button>
              ))}
            </div>

            {/* 경매 리스트 */}
            <div className="divide-y divide-gray-50">
              {salesLoading ? (
                <div className="flex justify-center py-10"><Spinner /></div>
              ) : salesItems.length === 0 ? (
                <p className="text-center text-sm text-gray-400 py-10">등록한 경매가 없습니다.</p>
              ) : (
                salesItems.map((item) => (
                  <button
                    key={item.id || item.auctionId}
                    type="button"
                    onClick={() => navigate(`/auctions/${item.id || item.auctionId}`)}
                    className="w-full flex items-center justify-between px-4 py-3.5 hover:bg-gray-50/80 transition-colors duration-200 text-left"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-gray-900 truncate">{item.title}</p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        {item.createdAt ? new Intl.DateTimeFormat('ko-KR').format(new Date(item.createdAt)) : ''}
                      </p>
                    </div>
                    <div className="text-right ml-3 shrink-0">
                      <p className="text-sm font-bold text-gray-900">{formatPrice(item.currentPrice || item.startPrice || 0)}원</p>
                      <span className="inline-block mt-0.5 px-2 py-0.5 text-[10px] font-semibold rounded-full bg-blue-50 text-blue-600">
                        {item.statusLabel || item.status}
                      </span>
                    </div>
                  </button>
                ))
              )}
            </div>

            {salesLoadingMore && (
              <div className="flex justify-center py-4"><Spinner /></div>
            )}
          </div>
        )}

        {/* 구매 탭 콘텐츠 */}
        {activeTab === 'bids' && (
          <div role="tabpanel" id="tabpanel-bids" aria-labelledby="tab-bids">
            <div className="divide-y divide-gray-50">
              {bidsLoading ? (
                <div className="flex justify-center py-10"><Spinner /></div>
              ) : bidsItems.length === 0 ? (
                <p className="text-center text-sm text-gray-400 py-10">입찰한 경매가 없습니다.</p>
              ) : (
                bidsItems.map((item) => (
                  <button
                    key={item.id || item.auctionId}
                    type="button"
                    onClick={() => navigate(`/auctions/${item.id || item.auctionId}`)}
                    className="w-full flex items-center justify-between px-4 py-3.5 hover:bg-gray-50/80 transition-colors duration-200 text-left"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="text-sm font-semibold text-gray-900 truncate">{item.title}</p>
                      <p className="text-xs text-gray-400 mt-0.5">
                        내 입찰가: {formatPrice(item.myHighestBid || 0)}원
                      </p>
                    </div>
                    <div className="text-right ml-3 shrink-0">
                      <p className="text-sm font-bold text-gray-900">{formatPrice(item.currentPrice || 0)}원</p>
                      <span className="inline-block mt-0.5 px-2 py-0.5 text-[10px] font-semibold rounded-full bg-violet-50 text-violet-600">
                        {item.statusLabel || item.status}
                      </span>
                    </div>
                  </button>
                ))
              )}
            </div>

            {bidsLoadingMore && (
              <div className="flex justify-center py-4"><Spinner /></div>
            )}
          </div>
        )}

        {/* 무한스크롤 트리거 */}
        <div ref={loadMoreRef} className="h-1" />
      </section>

      {/* 회원 탈퇴 */}
      <div className="text-center pb-8">
        <button
          type="button"
          onClick={() => setShowDeleteModal(true)}
          className="text-xs text-gray-400 hover:text-red-500 transition-colors duration-200"
          aria-label="회원 탈퇴"
        >
          회원 탈퇴
        </button>
      </div>

      {/* 탈퇴 확인 모달 */}
      {showDeleteModal && (
        <div
          className="fixed inset-0 z-[100] flex items-center justify-center bg-black/40 px-4"
          role="dialog"
          aria-modal="true"
          aria-label="회원 탈퇴 확인"
        >
          <div className="bg-white rounded-2xl p-6 w-full max-w-sm shadow-xl">
            <h3 className="text-lg font-bold text-gray-900 mb-2">회원 탈퇴</h3>
            <p className="text-sm text-gray-500 mb-4">
              정말 탈퇴하시겠습니까? 탈퇴 후 계정을 복구할 수 없습니다.
            </p>
            {deleteError && (
              <p className="text-xs text-red-600 mb-4" role="alert" aria-live="polite">
                {deleteError}
              </p>
            )}
            <div className="flex gap-3">
              <button
                type="button"
                onClick={() => setShowDeleteModal(false)}
                className="flex-1 py-2.5 text-sm font-semibold text-gray-700 bg-gray-100 rounded-xl hover:bg-gray-200 transition-colors duration-200"
                aria-label="탈퇴 취소"
              >
                취소
              </button>
              <button
                type="button"
                onClick={handleDeleteAccount}
                className="flex-1 py-2.5 text-sm font-semibold text-white bg-red-500 rounded-xl hover:bg-red-600 transition-colors duration-200"
                aria-label="탈퇴 확인"
              >
                탈퇴하기
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
