import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useAuction } from '../api/useAuction';
import { useTransactionByAuctionId } from '../api/useTransaction';
import { processPayment } from '../api/mutations';
import { useAuth } from '../contexts/AuthContext';
import Spinner from '../components/Spinner';
import Alert from '../components/Alert';
import { formatPrice, formatDate } from '../utils/formatters';

/**
 * 결제 확인 페이지
 * 낙찰된 경매의 결제를 처리한다.
 */
export default function PaymentPage() {
  const { auctionId } = useParams();
  const navigate = useNavigate();
  const { user } = useAuth();

  const { auction, isLoading: auctionLoading, error: auctionError } = useAuction(auctionId);
  const { transaction, isLoading: transactionLoading, error: transactionError } = useTransactionByAuctionId(auctionId);

  const [isProcessing, setIsProcessing] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  const isLoading = auctionLoading || transactionLoading;
  const error = auctionError || transactionError;

  /**
   * 결제 처리 핸들러
   * 결제 API 호출 후 완료 페이지로 이동한다.
   */
  const handlePayment = async () => {
    if (!transaction?.id) return;

    setIsProcessing(true);
    setErrorMessage(null);

    try {
      await processPayment(transaction.id);
      navigate(`/auctions/${auctionId}/payment/complete`);
    } catch (err) {
      setErrorMessage(err.message || '결제 처리에 실패했습니다.');
      setIsProcessing(false);
    }
  };

  // 로딩 상태
  if (isLoading) {
    return (
      <div className="flex justify-center py-24">
        <Spinner size="lg" />
      </div>
    );
  }

  // 에러 상태
  if (error || !auction || !transaction) {
    return (
      <div className="max-w-md mx-auto text-center py-24 animate-fade-in">
        <div className="w-16 h-16 mx-auto mb-4 bg-red-50 rounded-2xl flex items-center justify-center">
          <svg className="w-7 h-7 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
          </svg>
        </div>
        <p className="text-gray-700 font-semibold text-[15px]">결제 정보를 불러올 수 없습니다</p>
        <p className="text-sm text-gray-400 mt-1.5">{error?.message || '거래 정보를 찾을 수 없습니다.'}</p>
        <Link
          to={`/auctions/${auctionId}`}
          className="inline-block mt-6 px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold rounded-xl hover:bg-gray-800 transition-colors"
        >
          경매 상세로 돌아가기
        </Link>
      </div>
    );
  }

  // 권한 검증: 낙찰자 본인만 결제 가능
  const isWinner = user?.userId && String(transaction.buyerId) === String(user.userId);
  if (!isWinner) {
    return (
      <div className="max-w-md mx-auto text-center py-24 animate-fade-in">
        <div className="w-16 h-16 mx-auto mb-4 bg-yellow-50 rounded-2xl flex items-center justify-center">
          <svg className="w-7 h-7 text-yellow-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-2.5L13.732 4c-.77-.833-1.964-.833-2.732 0L4.082 16.5c-.77.833.192 2.5 1.732 2.5z" />
          </svg>
        </div>
        <p className="text-gray-700 font-semibold text-[15px]">결제 권한이 없습니다</p>
        <p className="text-sm text-gray-400 mt-1.5">낙찰자 본인만 결제를 진행할 수 있습니다.</p>
        <Link
          to={`/auctions/${auctionId}`}
          className="inline-block mt-6 px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold rounded-xl hover:bg-gray-800 transition-colors"
        >
          경매 상세로 돌아가기
        </Link>
      </div>
    );
  }

  // 이미 결제 완료된 경우
  if (transaction.status === 'PAID') {
    return (
      <div className="max-w-md mx-auto text-center py-24 animate-fade-in">
        <div className="w-16 h-16 mx-auto mb-4 bg-green-50 rounded-2xl flex items-center justify-center">
          <svg className="w-7 h-7 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <p className="text-gray-700 font-semibold text-[15px]">이미 결제가 완료되었습니다</p>
        <Link
          to={`/auctions/${auctionId}`}
          className="inline-block mt-6 px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold rounded-xl hover:bg-gray-800 transition-colors"
        >
          경매 상세로 돌아가기
        </Link>
      </div>
    );
  }

  // 결제 기한 만료 또는 노쇼 처리된 경우
  const isExpired = transaction.status === 'NO_SHOW' ||
    (transaction.paymentDeadline && new Date(transaction.paymentDeadline) < new Date());

  if (isExpired || transaction.status === 'NO_SHOW') {
    return (
      <div className="max-w-md mx-auto text-center py-24 animate-fade-in">
        <div className="w-16 h-16 mx-auto mb-4 bg-red-50 rounded-2xl flex items-center justify-center">
          <svg className="w-7 h-7 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <p className="text-gray-700 font-semibold text-[15px]">결제 기한이 만료되었습니다</p>
        <p className="text-sm text-gray-400 mt-1.5">결제 기한이 지나 더 이상 결제할 수 없습니다.</p>
        <Link
          to={`/auctions/${auctionId}`}
          className="inline-block mt-6 px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold rounded-xl hover:bg-gray-800 transition-colors"
        >
          경매 상세로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto space-y-5 animate-fade-in">
      {/* 헤더 */}
      <div className="text-center py-4">
        <h1 className="text-xl font-bold text-gray-900">결제 확인</h1>
        <p className="text-sm text-gray-500 mt-1">낙찰된 상품의 결제를 진행합니다</p>
      </div>

      {/* 상품 정보 카드 */}
      <div className="bg-white rounded-2xl p-6 ring-1 ring-black/[0.04] space-y-4">
        <h2 className="text-[15px] font-bold text-gray-900 flex items-center gap-2">
          <div className="w-7 h-7 bg-blue-50 rounded-lg flex items-center justify-center">
            <svg className="w-4 h-4 text-blue-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M20 7l-8-4-8 4m16 0l-8 4m8-4v10l-8 4m0-10L4 7m8 4v10M4 7v10l8 4" />
            </svg>
          </div>
          상품 정보
        </h2>

        {/* 상품 이미지 (있을 경우) */}
        {auction.imageUrl && (
          <div className="w-full h-48 bg-gray-100 rounded-xl overflow-hidden">
            <img
              src={auction.imageUrl}
              alt={auction.title}
              className="w-full h-full object-cover"
            />
          </div>
        )}

        {/* 상품명 */}
        <div className="space-y-1">
          <span className="text-[11px] text-gray-400 font-semibold uppercase tracking-wider">상품명</span>
          <p className="text-[15px] font-semibold text-gray-900">{auction.title}</p>
        </div>

        {/* 낙찰가 */}
        <div className="pt-4 border-t border-gray-100">
          <div className="flex justify-between items-center">
            <span className="text-[13px] text-gray-500 font-medium">낙찰가</span>
            <span className="text-xl font-bold text-gray-900">{formatPrice(transaction.finalPrice)}</span>
          </div>
        </div>

        {/* 결제 기한 */}
        <div className="flex justify-between items-center py-3 bg-yellow-50 rounded-xl px-4">
          <span className="text-[13px] text-yellow-700 font-medium">결제 기한</span>
          <span className="text-[13px] font-bold text-yellow-800">{formatDate(transaction.paymentDeadline)}</span>
        </div>
      </div>

      {/* 에러 메시지 */}
      {errorMessage && (
        <Alert type="error" message={errorMessage} onClose={() => setErrorMessage(null)} />
      )}

      {/* 결제 버튼 */}
      <button
        type="button"
        onClick={handlePayment}
        disabled={isProcessing}
        className="w-full py-4 bg-gradient-to-r from-blue-500 to-blue-600 text-white text-[15px] font-bold rounded-xl hover:from-blue-600 hover:to-blue-700 disabled:opacity-50 disabled:cursor-not-allowed transition-all shadow-lg shadow-blue-500/20 hover:shadow-blue-500/30 btn-press flex items-center justify-center gap-2"
      >
        {isProcessing ? (
          <>
            <Spinner size="sm" className="border-white border-t-transparent" />
            <span>결제 처리중...</span>
          </>
        ) : (
          <>
            <svg className="w-5 h-5" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z" />
            </svg>
            <span>결제하기</span>
          </>
        )}
      </button>

      {/* 경매 상세 링크 */}
      <div className="text-center">
        <Link
          to={`/auctions/${auctionId}`}
          className="text-sm text-gray-500 hover:text-gray-700 transition-colors"
        >
          경매 상세로 돌아가기
        </Link>
      </div>
    </div>
  );
}
