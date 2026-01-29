/**
 * 로딩 스피너 컴포넌트
 * 테이블 로딩 등 비동기 작업 중 표시
 */
export default function LoadingSpinner() {
  return (
    <div className="flex items-center justify-center gap-2 text-gray-400">
      <svg className="animate-spin h-5 w-5" viewBox="0 0 24 24">
        <circle
          className="opacity-25"
          cx="12"
          cy="12"
          r="10"
          stroke="currentColor"
          strokeWidth="4"
          fill="none"
        />
        <path
          className="opacity-75"
          fill="currentColor"
          d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"
        />
      </svg>
      <span className="text-sm">로딩 중...</span>
    </div>
  );
}
