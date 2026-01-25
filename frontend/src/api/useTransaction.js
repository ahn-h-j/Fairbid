import useSWR from 'swr';
import { fetcher } from './client';

/**
 * 거래 상세 조회 SWR 훅
 *
 * @param {string|number|null} transactionId - 거래 ID (null이면 요청하지 않음)
 * @returns {{ transaction, error, isLoading, mutate }}
 */
export function useTransaction(transactionId) {
  const { data, error, isLoading, mutate } = useSWR(
    transactionId ? `/transactions/${transactionId}` : null,
    fetcher
  );

  return { transaction: data, error, isLoading, mutate };
}

/**
 * 경매 ID로 거래 정보 조회 SWR 훅
 *
 * @param {string|number|null} auctionId - 경매 ID (null이면 요청하지 않음)
 * @returns {{ transaction, error, isLoading, mutate }}
 */
export function useTransactionByAuctionId(auctionId) {
  const { data, error, isLoading, mutate } = useSWR(
    auctionId ? `/transactions/auction/${auctionId}` : null,
    fetcher
  );

  return { transaction: data, error, isLoading, mutate };
}
