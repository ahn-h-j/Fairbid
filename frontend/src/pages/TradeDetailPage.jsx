import { useState, useEffect } from 'react';
import { useParams, Link } from 'react-router-dom';
import {
  useTrade,
  selectTradeMethod,
  completeTrade,
  proposeDirectTrade,
  acceptDirectTrade,
  counterProposeDirectTrade,
  submitAddress,
  confirmPayment,
  verifyPayment,
  rejectPayment,
  shipDelivery,
  confirmDelivery
} from '../api/useTrade';
import { apiRequest } from '../api/client';
import { useAuth } from '../contexts/AuthContext';
import Spinner from '../components/Spinner';
import Alert from '../components/Alert';
import { formatPrice, formatPhoneInput, formatPhone } from '../utils/formatters';

/**
 * 거래 상세 페이지
 * 상태에 따라 다른 UI 표시
 */
export default function TradeDetailPage() {
  const { tradeId } = useParams();
  const { user } = useAuth();
  const { trade, isLoading, isError, mutate } = useTrade(tradeId);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState(null);
  const [success, setSuccess] = useState(null);

  if (isLoading) {
    return (
      <div className="flex justify-center items-center min-h-[50vh]">
        <Spinner size="lg" />
      </div>
    );
  }

  if (isError || !trade) {
    return (
      <div className="max-w-2xl mx-auto text-center py-12">
        <p className="text-gray-500">거래를 찾을 수 없습니다.</p>
        <Link to="/trades" className="text-blue-600 hover:underline mt-2 inline-block">
          거래 목록으로 돌아가기
        </Link>
      </div>
    );
  }

  const isSeller = String(trade.sellerId) === String(user?.userId);
  const isBuyer = String(trade.buyerId) === String(user?.userId);

  // 공통 액션 래퍼
  const handleAction = async (action) => {
    setSubmitting(true);
    setError(null);
    setSuccess(null);
    try {
      await action();
      await mutate();
      setSuccess('처리가 완료되었습니다.');
    } catch (err) {
      setError(err.response?.data?.message || err.message || '처리에 실패했습니다.');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="max-w-2xl mx-auto animate-fade-in">
      {/* 페이지 헤더 */}
      <div className="mb-6">
        <Link to="/trades" className="text-[13px] text-gray-400 hover:text-gray-600 flex items-center gap-1 mb-2">
          <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 19l-7-7 7-7" />
          </svg>
          거래 목록
        </Link>
        <h1 className="text-[22px] font-bold text-gray-900 tracking-tight">거래 상세</h1>
      </div>

      {/* 알림 */}
      {error && <Alert type="error" message={error} onClose={() => setError(null)} className="mb-4" />}
      {success && <Alert type="success" message={success} onClose={() => setSuccess(null)} className="mb-4" />}

      {/* 거래 정보 카드 */}
      <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] mb-4">
        <div className="flex items-center justify-between mb-4">
          <TradeStatusBadge status={trade.status} />
          {trade.method && <TradeMethodBadge method={trade.method} />}
        </div>

        <div className="grid grid-cols-2 gap-4 text-[13px]">
          <div>
            <span className="text-gray-400">거래 금액</span>
            <p className="font-bold text-gray-900 text-[16px]">{formatPrice(trade.finalPrice)}</p>
          </div>
          <div>
            <span className="text-gray-400">내 역할</span>
            <p className="font-semibold text-gray-900">{isSeller ? '판매자' : '구매자'}</p>
          </div>
        </div>
      </div>

      {/* 상태별 액션 UI */}
      {trade.status === 'AWAITING_METHOD_SELECTION' && isBuyer && (
        <MethodSelectionUI
          tradeId={tradeId}
          onAction={handleAction}
          submitting={submitting}
        />
      )}

      {trade.status === 'AWAITING_METHOD_SELECTION' && isSeller && (
        <div className="bg-yellow-50 rounded-2xl p-5 ring-1 ring-yellow-200/50">
          <p className="text-[14px] text-yellow-800">
            <span className="font-semibold">구매자가 거래 방식을 선택 중입니다.</span>
            <br />
            <span className="text-[13px] text-yellow-600 mt-1 block">잠시만 기다려주세요.</span>
          </p>
        </div>
      )}

      {trade.status === 'AWAITING_ARRANGEMENT' && trade.method === 'DIRECT' && (
        <DirectTradeUI
          trade={trade}
          isSeller={isSeller}
          onAction={handleAction}
          submitting={submitting}
        />
      )}

      {trade.status === 'AWAITING_ARRANGEMENT' && trade.method === 'DELIVERY' && (
        <DeliveryUI
          trade={trade}
          isSeller={isSeller}
          onAction={handleAction}
          submitting={submitting}
        />
      )}

      {trade.status === 'ARRANGED' && (
        <ArrangedUI
          trade={trade}
          isSeller={isSeller}
          onAction={handleAction}
          submitting={submitting}
        />
      )}

      {trade.status === 'COMPLETED' && (
        <div className="bg-green-50 rounded-2xl p-5 ring-1 ring-green-200/50 text-center">
          <div className="text-3xl mb-2">🎉</div>
          <p className="text-[14px] font-semibold text-green-800">거래가 완료되었습니다!</p>
        </div>
      )}

      {trade.status === 'CANCELLED' && (
        <div className="bg-gray-50 rounded-2xl p-5 ring-1 ring-gray-200/50 text-center">
          <p className="text-[14px] text-gray-500">이 거래는 취소되었습니다.</p>
        </div>
      )}
    </div>
  );
}

// 상태 뱃지 컴포넌트
function TradeStatusBadge({ status }) {
  const statusConfig = {
    AWAITING_METHOD_SELECTION: { text: '방식 선택 대기', color: 'bg-yellow-100 text-yellow-700' },
    AWAITING_ARRANGEMENT: { text: '조율 중', color: 'bg-blue-100 text-blue-700' },
    ARRANGED: { text: '조율 완료', color: 'bg-purple-100 text-purple-700' },
    COMPLETED: { text: '거래 완료', color: 'bg-green-100 text-green-700' },
    CANCELLED: { text: '취소됨', color: 'bg-gray-100 text-gray-500' },
  };
  const config = statusConfig[status] || { text: status, color: 'bg-gray-100 text-gray-500' };
  return (
    <span className={`inline-flex items-center px-3 py-1.5 rounded-full text-[12px] font-semibold ${config.color}`}>
      {config.text}
    </span>
  );
}

function TradeMethodBadge({ method }) {
  const methodConfig = {
    DIRECT: { text: '직거래', icon: '🤝' },
    DELIVERY: { text: '택배', icon: '📦' },
  };
  const config = methodConfig[method] || { text: method, icon: '📋' };
  return (
    <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-gray-100 rounded-full text-[12px] font-medium text-gray-600">
      <span>{config.icon}</span>
      <span>{config.text}</span>
    </span>
  );
}

// 거래 방식 선택 UI (구매자)
function MethodSelectionUI({ tradeId, onAction, submitting }) {
  return (
    <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
      <h3 className="text-[14px] font-bold text-gray-900">거래 방식을 선택해주세요</h3>
      <div className="grid grid-cols-2 gap-3">
        <button
          onClick={() => onAction(() => selectTradeMethod(tradeId, 'DIRECT'))}
          disabled={submitting}
          className="p-4 bg-gray-50 rounded-xl hover:bg-blue-50 hover:ring-1 hover:ring-blue-200 transition-all text-center disabled:opacity-50"
        >
          <div className="text-2xl mb-2">🤝</div>
          <p className="text-[14px] font-semibold text-gray-900">직거래</p>
          <p className="text-[12px] text-gray-500 mt-1">직접 만나서 거래</p>
        </button>
        <button
          onClick={() => onAction(() => selectTradeMethod(tradeId, 'DELIVERY'))}
          disabled={submitting}
          className="p-4 bg-gray-50 rounded-xl hover:bg-blue-50 hover:ring-1 hover:ring-blue-200 transition-all text-center disabled:opacity-50"
        >
          <div className="text-2xl mb-2">📦</div>
          <p className="text-[14px] font-semibold text-gray-900">택배</p>
          <p className="text-[12px] text-gray-500 mt-1">택배로 배송받기</p>
        </button>
      </div>
    </div>
  );
}

// 직거래 UI
function DirectTradeUI({ trade, isSeller, onAction, submitting }) {
  const [date, setDate] = useState('');
  const [time, setTime] = useState('');
  // 역제안 모달 상태
  const [showCounterModal, setShowCounterModal] = useState(false);
  const [counterDate, setCounterDate] = useState('');
  const [counterTime, setCounterTime] = useState('');
  const directInfo = trade.directTradeInfo;

  if (!directInfo) {
    // 아직 제안이 없음 - 판매자가 첫 제안
    if (isSeller) {
      return (
        <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
          <h3 className="text-[14px] font-bold text-gray-900">만남 시간을 제안해주세요</h3>
          <p className="text-[13px] text-gray-500">위치: {directInfo?.location || '미정'}</p>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="block text-[12px] font-medium text-gray-600 mb-1">날짜</label>
              <input
                type="date"
                value={date}
                onChange={(e) => setDate(e.target.value)}
                min={new Date().toISOString().split('T')[0]}
                className="w-full px-3 py-2.5 bg-gray-50 rounded-lg text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
            </div>
            <div>
              <label className="block text-[12px] font-medium text-gray-600 mb-1">시간</label>
              <input
                type="time"
                value={time}
                onChange={(e) => setTime(e.target.value)}
                className="w-full px-3 py-2.5 bg-gray-50 rounded-lg text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
            </div>
          </div>
          <button
            onClick={() => onAction(() => proposeDirectTrade(trade.id, date, time))}
            disabled={submitting || !date || !time}
            className="w-full py-3 bg-blue-600 text-white text-[14px] font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? '제안 중...' : '시간 제안하기'}
          </button>
        </div>
      );
    } else {
      return (
        <div className="bg-yellow-50 rounded-2xl p-5 ring-1 ring-yellow-200/50">
          <p className="text-[14px] text-yellow-800">판매자가 만남 시간을 제안할 때까지 기다려주세요.</p>
        </div>
      );
    }
  }

  // 제안이 있음 - isSeller로 내 제안인지 판단
  const isMyProposal = isSeller === (directInfo.proposedBy === trade.sellerId);
  const statusText = directInfo.status === 'PROPOSED' ? '제안됨' : directInfo.status === 'COUNTER_PROPOSED' ? '역제안됨' : '수락됨';

  return (
    <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
      <h3 className="text-[14px] font-bold text-gray-900">직거래 일정</h3>
      <div className="bg-gray-50 rounded-xl p-4 space-y-2">
        <p className="text-[13px] text-gray-600">📍 {directInfo.location}</p>
        <p className="text-[13px] text-gray-600">📅 {directInfo.meetingDate}</p>
        <p className="text-[13px] text-gray-600">🕐 {directInfo.meetingTime}</p>
        <p className="text-[12px] text-gray-400">{isMyProposal ? '내가 제안함' : '상대방이 제안함'} · {statusText}</p>
      </div>

      {directInfo.status !== 'ACCEPTED' && !isMyProposal && (
        <>
          <div className="flex gap-3">
            <button
              onClick={() => onAction(() => acceptDirectTrade(trade.id))}
              disabled={submitting}
              className="flex-1 py-3 bg-green-600 text-white text-[14px] font-semibold rounded-xl hover:bg-green-700 disabled:opacity-50 transition-colors"
            >
              수락
            </button>
            <button
              onClick={() => {
                setCounterDate('');
                setCounterTime('');
                setShowCounterModal(true);
              }}
              disabled={submitting}
              className="flex-1 py-3 bg-gray-100 text-gray-700 text-[14px] font-semibold rounded-xl hover:bg-gray-200 disabled:opacity-50 transition-colors"
            >
              역제안
            </button>
          </div>

          {/* 역제안 모달 */}
          {showCounterModal && (
            <div className="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4">
              <div className="bg-white rounded-2xl w-full max-w-sm p-5 space-y-4 animate-fade-in">
                <h3 className="text-[16px] font-bold text-gray-900">다른 시간 제안하기</h3>
                <div className="space-y-3">
                  <div>
                    <label className="block text-[12px] font-medium text-gray-600 mb-1">날짜</label>
                    <input
                      type="date"
                      value={counterDate}
                      onChange={(e) => setCounterDate(e.target.value)}
                      min={new Date().toISOString().split('T')[0]}
                      className="w-full px-3 py-2.5 bg-gray-50 rounded-lg text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                    />
                  </div>
                  <div>
                    <label className="block text-[12px] font-medium text-gray-600 mb-1">시간</label>
                    <input
                      type="time"
                      value={counterTime}
                      onChange={(e) => setCounterTime(e.target.value)}
                      className="w-full px-3 py-2.5 bg-gray-50 rounded-lg text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                    />
                  </div>
                </div>
                <div className="flex gap-3 pt-2">
                  <button
                    onClick={() => setShowCounterModal(false)}
                    className="flex-1 py-2.5 bg-gray-100 text-gray-700 text-[14px] font-semibold rounded-xl hover:bg-gray-200 transition-colors"
                  >
                    취소
                  </button>
                  <button
                    onClick={() => {
                      if (counterDate && counterTime) {
                        setShowCounterModal(false);
                        onAction(() => counterProposeDirectTrade(trade.id, counterDate, counterTime));
                      }
                    }}
                    disabled={!counterDate || !counterTime}
                    className="flex-1 py-2.5 bg-blue-600 text-white text-[14px] font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
                  >
                    제안하기
                  </button>
                </div>
              </div>
            </div>
          )}
        </>
      )}

      {directInfo.status !== 'ACCEPTED' && isMyProposal && (
        <p className="text-[13px] text-center text-gray-500">상대방의 응답을 기다리는 중...</p>
      )}
    </div>
  );
}

// 택배 UI
function DeliveryUI({ trade, isSeller, onAction, submitting }) {
  const deliveryInfo = trade.deliveryInfo;
  const [savedAddress, setSavedAddress] = useState(null);
  const [useSavedAddress, setUseSavedAddress] = useState(false);
  const [addressForm, setAddressForm] = useState({
    recipientName: '',
    recipientPhone: '',
    postalCode: '',
    address: '',
    addressDetail: '',
  });
  const [shippingForm, setShippingForm] = useState({
    courierCompany: '',
    trackingNumber: '',
  });

  // 판매자 계좌 관련 상태
  const [savedBankAccount, setSavedBankAccount] = useState(null);
  const [bankAccountLoaded, setBankAccountLoaded] = useState(false);
  const [bankAccountForm, setBankAccountForm] = useState({
    bankName: '',
    accountNumber: '',
    accountHolder: '',
  });

  // 저장된 배송지 로드
  useEffect(() => {
    if (!isSeller && deliveryInfo?.status === 'AWAITING_ADDRESS') {
      apiRequest('/users/me')
        .then(data => {
          if (data.shippingAddress) {
            setSavedAddress(data.shippingAddress);
          }
        })
        .catch(() => {});
    }
  }, [isSeller, deliveryInfo?.status]);

  // 판매자: 저장된 계좌 로드 (입금 대기 상태일 때)
  useEffect(() => {
    if (isSeller && deliveryInfo?.status === 'AWAITING_PAYMENT') {
      apiRequest('/users/me')
        .then(data => {
          if (data.bankAccount) {
            setSavedBankAccount(data.bankAccount);
          }
          setBankAccountLoaded(true);
        })
        .catch(() => {
          setBankAccountLoaded(true);
        });
    }
  }, [isSeller, deliveryInfo?.status]);

  // 계좌 등록 처리
  const handleSubmitBankAccount = async () => {
    await apiRequest('/users/me/bank-account', {
      method: 'PUT',
      body: JSON.stringify(bankAccountForm),
    });
    // 로컬 상태 업데이트 (뷰 전환)
    setSavedBankAccount({ ...bankAccountForm });
  };

  // 저장된 배송지 사용 토글
  const handleUseSavedAddress = () => {
    if (savedAddress) {
      setUseSavedAddress(true);
      setAddressForm({
        recipientName: savedAddress.recipientName,
        recipientPhone: savedAddress.recipientPhone,
        postalCode: savedAddress.postalCode || '',
        address: savedAddress.address,
        addressDetail: savedAddress.addressDetail || '',
      });
    }
  };

  // 직접 입력 선택
  const handleEnterManually = () => {
    setUseSavedAddress(false);
    setAddressForm({
      recipientName: '',
      recipientPhone: '',
      postalCode: '',
      address: '',
      addressDetail: '',
    });
  };

  // 배송지 대기 중 (구매자가 입력해야 함)
  if (deliveryInfo?.status === 'AWAITING_ADDRESS') {
    if (!isSeller) {
      return (
        <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
          <h3 className="text-[14px] font-bold text-gray-900">배송지를 입력해주세요</h3>

          {/* 저장된 배송지 선택 옵션 */}
          {savedAddress && (
            <div className="space-y-2">
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleUseSavedAddress}
                  className={`flex-1 py-2.5 text-[13px] font-semibold rounded-lg transition-colors ${
                    useSavedAddress
                      ? 'bg-blue-100 text-blue-700 ring-1 ring-blue-300'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  등록된 배송지
                </button>
                <button
                  type="button"
                  onClick={handleEnterManually}
                  className={`flex-1 py-2.5 text-[13px] font-semibold rounded-lg transition-colors ${
                    !useSavedAddress
                      ? 'bg-blue-100 text-blue-700 ring-1 ring-blue-300'
                      : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
                  }`}
                >
                  직접 입력
                </button>
              </div>
              {useSavedAddress && (
                <div className="bg-blue-50 rounded-xl p-3 text-[13px] text-blue-700">
                  <p className="font-semibold">{savedAddress.recipientName} ({formatPhone(savedAddress.recipientPhone)})</p>
                  <p className="mt-0.5">{savedAddress.postalCode && `[${savedAddress.postalCode}] `}{savedAddress.address}</p>
                  {savedAddress.addressDetail && <p>{savedAddress.addressDetail}</p>}
                </div>
              )}
            </div>
          )}

          {/* 배송지 입력 폼 (직접 입력 시만 표시) */}
          {!useSavedAddress && (
            <div className="space-y-3">
              <input
                type="text"
                placeholder="수령인 이름"
                value={addressForm.recipientName}
                onChange={(e) => setAddressForm(prev => ({ ...prev, recipientName: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
              <input
                type="tel"
                placeholder="연락처 (010-0000-0000)"
                value={addressForm.recipientPhone}
                onChange={(e) => setAddressForm(prev => ({ ...prev, recipientPhone: formatPhoneInput(e.target.value) }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
              <input
                type="text"
                placeholder="우편번호"
                value={addressForm.postalCode}
                onChange={(e) => setAddressForm(prev => ({ ...prev, postalCode: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
              <input
                type="text"
                placeholder="주소"
                value={addressForm.address}
                onChange={(e) => setAddressForm(prev => ({ ...prev, address: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
              <input
                type="text"
                placeholder="상세주소 (선택)"
                value={addressForm.addressDetail}
                onChange={(e) => setAddressForm(prev => ({ ...prev, addressDetail: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
            </div>
          )}

          <button
            onClick={() => onAction(() => submitAddress(trade.id, addressForm))}
            disabled={submitting || !addressForm.recipientName || !addressForm.recipientPhone || !addressForm.address}
            className="w-full py-3 bg-blue-600 text-white text-[14px] font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
          >
            {submitting ? '등록 중...' : '배송지 등록'}
          </button>
        </div>
      );
    } else {
      return (
        <div className="bg-yellow-50 rounded-2xl p-5 ring-1 ring-yellow-200/50">
          <p className="text-[14px] text-yellow-800">구매자가 배송지를 입력 중입니다.</p>
        </div>
      );
    }
  }

  // 입금 대기 (배송지 입력 완료)
  if (deliveryInfo?.status === 'AWAITING_PAYMENT') {
    if (!isSeller) {
      // 구매자: 판매자 계좌 정보 확인 + 입금 완료 버튼
      const bankAccount = trade.sellerBankAccount;
      return (
        <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
          <h3 className="text-[14px] font-bold text-gray-900">판매자에게 입금해주세요</h3>

          {/* 거래 금액 */}
          <div className="bg-blue-50 rounded-xl p-4 text-center">
            <p className="text-[12px] text-blue-600 mb-1">입금 금액</p>
            <p className="text-[20px] font-bold text-blue-700">{formatPrice(trade.finalPrice)}</p>
          </div>

          {/* 판매자 계좌 정보 */}
          {bankAccount ? (
            <div className="bg-gray-50 rounded-xl p-4 space-y-1">
              <p className="text-[12px] text-gray-500 mb-2">판매자 계좌</p>
              <p className="text-[14px] font-semibold text-gray-900">{bankAccount.bankName}</p>
              <p className="text-[14px] text-gray-700">{bankAccount.accountNumber}</p>
              <p className="text-[13px] text-gray-500">{bankAccount.accountHolder}</p>
            </div>
          ) : (
            <div className="bg-yellow-50 rounded-xl p-4">
              <p className="text-[13px] text-yellow-700">판매자가 아직 계좌를 등록하지 않았습니다. 잠시 후 다시 확인해주세요.</p>
            </div>
          )}

          {deliveryInfo.paymentConfirmed ? (
            deliveryInfo.paymentVerified ? (
              <div className="bg-green-50 rounded-xl p-4 text-center">
                <p className="text-[14px] font-semibold text-green-700">입금 확인 완료</p>
                <p className="text-[12px] text-green-600 mt-1">판매자가 입금을 확인했습니다. 곧 발송될 예정입니다.</p>
              </div>
            ) : (
              <div className="bg-yellow-50 rounded-xl p-4 text-center">
                <p className="text-[14px] font-semibold text-yellow-700">입금 완료 처리됨</p>
                <p className="text-[12px] text-yellow-600 mt-1">판매자의 입금 확인을 기다리는 중...</p>
              </div>
            )
          ) : (
            <button
              onClick={() => onAction(() => confirmPayment(trade.id))}
              disabled={submitting || !bankAccount}
              className="w-full py-3 bg-blue-600 text-white text-[14px] font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {submitting ? '처리 중...' : '입금 완료'}
            </button>
          )}
        </div>
      );
    } else {
      // 판매자: 계좌 등록 + 입금 확인 후 송장 입력

      // 계좌 정보 로딩 중
      if (!bankAccountLoaded) {
        return (
          <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] flex justify-center py-8">
            <Spinner size="md" />
          </div>
        );
      }

      // 계좌 미등록 → 계좌 입력 폼
      if (!savedBankAccount) {
        return (
          <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
            <h3 className="text-[14px] font-bold text-gray-900">입금받을 계좌를 등록해주세요</h3>
            <p className="text-[13px] text-gray-500">구매자가 이 계좌로 입금하게 됩니다.</p>

            <div className="space-y-3">
              <input
                type="text"
                placeholder="은행명 (예: 카카오뱅크)"
                value={bankAccountForm.bankName}
                onChange={(e) => setBankAccountForm(prev => ({ ...prev, bankName: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
              <input
                type="text"
                placeholder="계좌번호"
                value={bankAccountForm.accountNumber}
                onChange={(e) => setBankAccountForm(prev => ({ ...prev, accountNumber: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
              <input
                type="text"
                placeholder="예금주"
                value={bankAccountForm.accountHolder}
                onChange={(e) => setBankAccountForm(prev => ({ ...prev, accountHolder: e.target.value }))}
                className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
              />
            </div>

            <button
              onClick={() => onAction(handleSubmitBankAccount)}
              disabled={submitting || !bankAccountForm.bankName || !bankAccountForm.accountNumber || !bankAccountForm.accountHolder}
              className="w-full py-3 bg-blue-600 text-white text-[14px] font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              {submitting ? '등록 중...' : '계좌 등록'}
            </button>
          </div>
        );
      }

      // 계좌 등록됨 → 배송 정보 + 입금 대기/송장 입력
      return (
        <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
          <h3 className="text-[14px] font-bold text-gray-900">배송 정보</h3>
          <div className="bg-gray-50 rounded-xl p-4 space-y-1">
            <p className="text-[13px] text-gray-600">📍 {deliveryInfo.address} {deliveryInfo.addressDetail}</p>
            <p className="text-[13px] text-gray-600">👤 {deliveryInfo.recipientName}</p>
            <p className="text-[13px] text-gray-600">📞 {deliveryInfo.recipientPhone}</p>
          </div>

          {deliveryInfo.paymentConfirmed && deliveryInfo.paymentVerified ? (
            /* 입금 확인 완료 → 송장 입력 가능 */
            <>
              <div className="bg-green-50 rounded-xl p-3">
                <p className="text-[13px] font-semibold text-green-700">입금이 확인되었습니다. 송장을 입력해주세요.</p>
              </div>
              <h4 className="text-[13px] font-semibold text-gray-700 pt-2">송장 정보 입력</h4>
              <div className="space-y-3">
                <input
                  type="text"
                  placeholder="택배사"
                  value={shippingForm.courierCompany}
                  onChange={(e) => setShippingForm(prev => ({ ...prev, courierCompany: e.target.value }))}
                  className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                />
                <input
                  type="text"
                  placeholder="송장번호"
                  value={shippingForm.trackingNumber}
                  onChange={(e) => setShippingForm(prev => ({ ...prev, trackingNumber: e.target.value }))}
                  className="w-full px-4 py-3 bg-gray-50 rounded-xl text-[14px] focus:outline-none focus:ring-2 focus:ring-blue-500/40"
                />
              </div>
              <button
                onClick={() => onAction(() => shipDelivery(trade.id, shippingForm))}
                disabled={submitting || !shippingForm.courierCompany || !shippingForm.trackingNumber}
                className="w-full py-3 bg-blue-600 text-white text-[14px] font-semibold rounded-xl hover:bg-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
              >
                {submitting ? '등록 중...' : '발송 완료'}
              </button>
            </>
          ) : deliveryInfo.paymentConfirmed && !deliveryInfo.paymentVerified ? (
            /* 구매자가 입금 완료 알림 → 판매자가 확인/거절 선택 */
            <>
              <div className="bg-blue-50 rounded-xl p-4">
                <p className="text-[14px] font-semibold text-blue-800">구매자가 입금을 완료했다고 합니다.</p>
                <p className="text-[13px] text-blue-600 mt-1">계좌를 확인하고 입금 여부를 확인해주세요.</p>
              </div>
              <div className="flex gap-3">
                <button
                  onClick={() => onAction(() => verifyPayment(trade.id))}
                  disabled={submitting}
                  className="flex-1 py-3 bg-green-600 text-white text-[14px] font-semibold rounded-xl hover:bg-green-700 disabled:opacity-50 transition-colors"
                >
                  {submitting ? '처리 중...' : '입금 확인'}
                </button>
                <button
                  onClick={() => onAction(() => rejectPayment(trade.id))}
                  disabled={submitting}
                  className="flex-1 py-3 bg-red-100 text-red-700 text-[14px] font-semibold rounded-xl hover:bg-red-200 disabled:opacity-50 transition-colors"
                >
                  {submitting ? '처리 중...' : '미입금'}
                </button>
              </div>
            </>
          ) : (
            /* 아직 구매자가 입금 완료를 누르지 않음 */
            <div className="bg-yellow-50 rounded-xl p-4">
              <p className="text-[14px] text-yellow-800">구매자의 입금을 기다리는 중입니다.</p>
              <p className="text-[13px] text-yellow-600 mt-1">입금이 확인되면 알림을 보내드립니다.</p>
            </div>
          )}
        </div>
      );
    }
  }

  // 발송 완료
  if (deliveryInfo?.status === 'SHIPPED') {
    return (
      <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
        <h3 className="text-[14px] font-bold text-gray-900">배송 정보</h3>
        <div className="bg-blue-50 rounded-xl p-4 space-y-1">
          <p className="text-[13px] text-blue-700">📦 {deliveryInfo.courierCompany}</p>
          <p className="text-[13px] text-blue-700">🔢 {deliveryInfo.trackingNumber}</p>
        </div>
        {!isSeller && (
          <button
            onClick={() => onAction(() => confirmDelivery(trade.id))}
            disabled={submitting}
            className="w-full py-3 bg-green-600 text-white text-[14px] font-semibold rounded-xl hover:bg-green-700 disabled:opacity-50 transition-colors"
          >
            {submitting ? '확인 중...' : '수령 확인'}
          </button>
        )}
      </div>
    );
  }

  return null;
}

// 조율 완료 UI
function ArrangedUI({ trade, isSeller, onAction, submitting }) {
  const isBuyer = !isSeller;

  return (
    <div className="bg-white rounded-2xl p-5 ring-1 ring-black/[0.04] space-y-4">
      <h3 className="text-[14px] font-bold text-gray-900">거래 조율이 완료되었습니다</h3>

      {/* 직거래 정보 */}
      {trade.method === 'DIRECT' && trade.directTradeInfo && (
        <div className="bg-gray-50 rounded-xl p-4 space-y-1">
          <p className="text-[13px] text-gray-600">📍 {trade.directTradeInfo.location}</p>
          <p className="text-[13px] text-gray-600">📅 {trade.directTradeInfo.meetingDate}</p>
          <p className="text-[13px] text-gray-600">🕐 {trade.directTradeInfo.meetingTime}</p>
        </div>
      )}

      {/* 택배 정보 */}
      {trade.method === 'DELIVERY' && trade.deliveryInfo && (
        <div className="space-y-3">
          <div className="bg-blue-50 rounded-xl p-4 space-y-1">
            <p className="text-[13px] font-semibold text-blue-800">배송 정보</p>
            <p className="text-[13px] text-blue-700">📦 {trade.deliveryInfo.courierCompany}</p>
            <p className="text-[13px] text-blue-700">🔢 {trade.deliveryInfo.trackingNumber}</p>
          </div>
          <div className="bg-gray-50 rounded-xl p-4 space-y-1">
            <p className="text-[13px] font-semibold text-gray-700">배송지</p>
            <p className="text-[13px] text-gray-600">👤 {trade.deliveryInfo.recipientName} ({formatPhone(trade.deliveryInfo.recipientPhone)})</p>
            <p className="text-[13px] text-gray-600">📍 {trade.deliveryInfo.address} {trade.deliveryInfo.addressDetail}</p>
          </div>
        </div>
      )}

      {/* 구매자만 수령 확인 가능 */}
      {isBuyer ? (
        <button
          onClick={() => onAction(() => completeTrade(trade.id))}
          disabled={submitting}
          className="w-full py-3 bg-green-600 text-white text-[14px] font-semibold rounded-xl hover:bg-green-700 disabled:opacity-50 transition-colors"
        >
          {submitting ? '완료 중...' : '수령 확인'}
        </button>
      ) : (
        <p className="text-[13px] text-center text-gray-500">구매자의 수령 확인을 기다리는 중...</p>
      )}
    </div>
  );
}
