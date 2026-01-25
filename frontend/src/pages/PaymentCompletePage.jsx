import { useParams, Link } from 'react-router-dom';
import { useAuction } from '../api/useAuction';
import { useTransactionByAuctionId } from '../api/useTransaction';
import Spinner from '../components/Spinner';
import { formatPrice } from '../utils/formatters';

/**
 * 결제 완료 페이지
 * 결제 성공 후 결과를 표시한다.
 */
export default function PaymentCompletePage() {
  const { auctionId } = useParams();

  const { auction, isLoading: auctionLoading, error: auctionError } = useAuction(auctionId);
  const { transaction, isLoading: transactionLoading, error: transactionError } = useTransactionByAuctionId(auctionId);

  const isLoading = auctionLoading || transactionLoading;
  const error = auctionError || transactionError;

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
        <p className="text-gray-700 font-semibold text-[15px]">정보를 불러올 수 없습니다</p>
        <p className="text-sm text-gray-400 mt-1.5">{error?.message || '거래 정보를 찾을 수 없습니다.'}</p>
        <Link
          to="/"
          className="inline-block mt-6 px-5 py-2.5 bg-gray-900 text-white text-sm font-semibold rounded-xl hover:bg-gray-800 transition-colors"
        >
          홈으로 돌아가기
        </Link>
      </div>
    );
  }

  return (
    <div className="max-w-lg mx-auto space-y-6 animate-fade-in py-8">
      {/* 성공 아이콘 및 메시지 */}
      <div className="text-center">
        <div className="w-20 h-20 mx-auto mb-4 bg-green-50 rounded-full flex items-center justify-center ring-4 ring-green-100 animate-bounce-in">
          <svg className="w-10 h-10 text-green-500" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M5 13l4 4L19 7" />
          </svg>
        </div>
        <h1 className="text-2xl font-bold text-gray-900">결제가 완료되었습니다</h1>
        <p className="text-sm text-gray-500 mt-2">상품 구매가 정상적으로 처리되었습니다</p>
      </div>

      {/* 결제 정보 카드 */}
      <div className="bg-white rounded-2xl p-6 ring-1 ring-black/[0.04] space-y-4">
        {/* 상품명 */}
        <div className="flex justify-between items-start">
          <span className="text-[13px] text-gray-500 font-medium">상품명</span>
          <span className="text-[14px] font-semibold text-gray-900 text-right max-w-[200px]">{auction.title}</span>
        </div>

        <div className="border-t border-gray-100" />

        {/* 결제 금액 */}
        <div className="flex justify-between items-center">
          <span className="text-[13px] text-gray-500 font-medium">결제 금액</span>
          <span className="text-xl font-bold text-blue-600">{formatPrice(transaction.finalPrice)}</span>
        </div>

        <div className="border-t border-gray-100" />

        {/* 거래 번호 */}
        <div className="flex justify-between items-center">
          <span className="text-[13px] text-gray-500 font-medium">거래 번호</span>
          <span className="text-[14px] font-mono text-gray-700">#{transaction.id}</span>
        </div>

        {/* 결제 상태 뱃지 */}
        <div className="flex justify-between items-center pt-2">
          <span className="text-[13px] text-gray-500 font-medium">결제 상태</span>
          <span className="inline-flex items-center gap-1.5 px-3 py-1.5 bg-green-50 text-green-700 rounded-full text-[12px] font-bold ring-1 ring-green-200/60">
            <span className="w-1.5 h-1.5 bg-green-500 rounded-full" />
            결제 완료
          </span>
        </div>
      </div>

      {/* 안내 메시지 */}
      <div className="bg-blue-50 rounded-xl p-4 flex items-start gap-3">
        <div className="w-8 h-8 bg-blue-100 rounded-lg flex items-center justify-center shrink-0">
          <svg className="w-4 h-4 text-blue-600" fill="none" stroke="currentColor" viewBox="0 0 24 24" aria-hidden="true">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13 16h-1v-4h-1m1-4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
          </svg>
        </div>
        <div>
          <p className="text-[13px] font-semibold text-blue-800">결제 완료 안내</p>
          <p className="text-[12px] text-blue-600 mt-0.5">판매자에게 결제 완료 알림이 전송되었습니다. 판매자와 직접 연락하여 상품 수령 방법을 조율해주세요.</p>
        </div>
      </div>

      {/* 버튼 */}
      <div className="space-y-3">
        <Link
          to={`/auctions/${auctionId}`}
          className="block w-full py-3.5 bg-gray-900 text-white text-center text-[14px] font-semibold rounded-xl hover:bg-gray-800 transition-colors btn-press"
        >
          경매 상세로 돌아가기
        </Link>
        <Link
          to="/"
          className="block w-full py-3.5 bg-gray-100 text-gray-700 text-center text-[14px] font-semibold rounded-xl hover:bg-gray-200 transition-colors btn-press"
        >
          경매 목록 보기
        </Link>
      </div>
    </div>
  );
}
