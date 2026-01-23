import { useState } from 'react';
import { getCurrentUserId, setCurrentUserId } from '../api/client';

/**
 * 테스트용 사용자 전환 컴포넌트
 * 개발 환경에서 X-User-Id 헤더를 변경하여 다른 사용자로 테스트할 수 있다.
 */
export default function UserSelector() {
  const [userId, setUserId] = useState(getCurrentUserId());

  const handleChange = (e) => {
    const newId = parseInt(e.target.value, 10);
    if (!isNaN(newId) && newId > 0) {
      setUserId(newId);
      setCurrentUserId(newId);
    }
  };

  return (
    <div className="flex items-center gap-2">
      <span className="text-[11px] text-gray-400 font-medium">User</span>
      <input
        id="user-selector"
        type="number"
        min="1"
        value={userId}
        onChange={handleChange}
        className="w-12 px-2 py-1 text-[12px] font-semibold text-gray-600 bg-gray-50 border-0 rounded-lg text-center focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition-all"
        aria-label="테스트 사용자 ID"
      />
    </div>
  );
}
