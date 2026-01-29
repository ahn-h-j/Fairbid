import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { getAdminAuctionList } from '../../api/admin';
import StatusBadge from '../../components/StatusBadge';
import LoadingSpinner from '../../components/LoadingSpinner';

/**
 * 경매 관리 페이지
 * 경매 목록 조회 및 관리
 */
export default function AuctionManagePage() {
  // 필터 상태
  const [status, setStatus] = useState('');
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');

  // 페이지네이션 상태
  const [page, setPage] = useState(0);
  const [pageData, setPageData] = useState(null);

  // 로딩 및 에러 상태
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  // 데이터 로드
  useEffect(() => {
    async function fetchAuctions() {
      setLoading(true);
      setError(null);

      try {
        const data = await getAdminAuctionList({
          status: status || null,
          keyword: searchKeyword || null,
          page,
          size: 15,
        });
        setPageData(data);
      } catch (err) {
        console.error('경매 목록 조회 실패:', err);
        setError(err.message || '경매 목록을 불러오는 데 실패했습니다.');
      } finally {
        setLoading(false);
      }
    }

    fetchAuctions();
  }, [status, searchKeyword, page]);

  // 검색 핸들러
  const handleSearch = (e) => {
    e.preventDefault();
    setSearchKeyword(keyword);
    setPage(0);
  };

  // 상태 변경 핸들러
  const handleStatusChange = (e) => {
    setStatus(e.target.value);
    setPage(0);
  };

  const auctions = pageData?.content || [];
  const totalPages = pageData?.totalPages || 0;
  const totalElements = pageData?.totalElements || 0;

  return (
    <div className="space-y-6">
      {/* 헤더 */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold text-gray-900">경매 관리</h1>
          <p className="text-sm text-gray-500 mt-1">
            총 {totalElements.toLocaleString()}건의 경매
          </p>
        </div>
      </div>

      {/* 검색/필터 */}
      <form onSubmit={handleSearch} className="flex gap-3">
        <input
          type="text"
          value={keyword}
          onChange={(e) => setKeyword(e.target.value)}
          placeholder="경매 제목 검색..."
          className="flex-1 px-4 py-2.5 bg-white rounded-xl ring-1 ring-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        />
        <select
          value={status}
          onChange={handleStatusChange}
          className="px-4 py-2.5 bg-white rounded-xl ring-1 ring-gray-200 text-sm focus:outline-none focus:ring-2 focus:ring-indigo-500"
        >
          <option value="">전체 상태</option>
          <option value="BIDDING">진행중</option>
          <option value="ENDED">낙찰</option>
          <option value="FAILED">유찰</option>
          <option value="CANCELLED">취소</option>
        </select>
        <button
          type="submit"
          className="px-5 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 transition-colors"
        >
          검색
        </button>
      </form>

      {/* 에러 표시 */}
      {error && (
        <div className="bg-red-50 border border-red-200 text-red-700 px-4 py-3 rounded-lg">
          {error}
        </div>
      )}

      {/* 테이블 */}
      <div className="bg-white rounded-2xl shadow-sm ring-1 ring-gray-100 overflow-hidden">
        <div className="overflow-x-auto">
          <table className="w-full min-w-[800px]">
            <thead className="bg-gray-50 border-b border-gray-100">
              <tr>
                <th className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-16">
                  ID
                </th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase whitespace-nowrap">
                  제목
                </th>
                <th className="px-3 py-3 text-left text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-24">
                  판매자
                </th>
                <th className="px-3 py-3 text-right text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-28">
                  현재가
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-16">
                  입찰
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-16">
                  연장
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-20">
                  상태
                </th>
                <th className="px-3 py-3 text-center text-xs font-semibold text-gray-500 uppercase whitespace-nowrap w-20">

                </th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {loading ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center">
                    <LoadingSpinner />
                  </td>
                </tr>
              ) : auctions.length === 0 ? (
                <tr>
                  <td colSpan={8} className="px-4 py-12 text-center text-gray-400 text-sm">
                    경매가 없습니다
                  </td>
                </tr>
              ) : (
                auctions.map((auction) => (
                  <tr key={auction.id} className="hover:bg-gray-50">
                    <td className="px-3 py-3 text-sm text-gray-500">{auction.id}</td>
                    <td className="px-3 py-3">
                      <span className="text-sm font-medium text-gray-900 line-clamp-1">
                        {auction.title}
                      </span>
                    </td>
                    <td className="px-3 py-3 text-sm text-gray-600 truncate max-w-[100px]">
                      {auction.sellerNickname}
                    </td>
                    <td className="px-3 py-3 text-sm font-semibold text-gray-900 text-right whitespace-nowrap">
                      {auction.currentPrice.toLocaleString()}원
                    </td>
                    <td className="px-3 py-3 text-sm text-gray-600 text-center">
                      {auction.totalBidCount}
                    </td>
                    <td className="px-3 py-3 text-sm text-center">
                      {auction.extensionCount > 0 ? (
                        <span className="text-amber-600 font-medium">{auction.extensionCount}</span>
                      ) : (
                        <span className="text-gray-400">-</span>
                      )}
                    </td>
                    <td className="px-3 py-3 text-center">
                      <StatusBadge status={auction.status} />
                    </td>
                    <td className="px-3 py-3 text-center">
                      <Link
                        to={`/auctions/${auction.id}`}
                        className="inline-block px-3 py-1.5 bg-indigo-50 text-indigo-600 hover:bg-indigo-100 text-sm font-medium rounded-lg transition-colors"
                      >
                        보기
                      </Link>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>

        {/* 페이지네이션 */}
        {totalPages > 1 && (
          <div className="flex items-center justify-center gap-2 p-4 border-t border-gray-100">
            <button
              onClick={() => setPage((p) => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-3 py-1.5 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              이전
            </button>
            <span className="text-sm text-gray-600">
              {page + 1} / {totalPages}
            </span>
            <button
              onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
              disabled={page >= totalPages - 1}
              className="px-3 py-1.5 text-sm rounded-lg bg-gray-100 hover:bg-gray-200 disabled:opacity-50 disabled:cursor-not-allowed"
            >
              다음
            </button>
          </div>
        )}
      </div>
    </div>
  );
}
