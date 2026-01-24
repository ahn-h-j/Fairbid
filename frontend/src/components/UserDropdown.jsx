import { useState, useRef, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';

/**
 * 로그인 상태 헤더 드롭다운
 * 닉네임 클릭 시 마이페이지, 로그아웃 메뉴를 표시한다.
 */
export default function UserDropdown() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [isOpen, setIsOpen] = useState(false);
  const dropdownRef = useRef(null);

  // 외부 클릭 시 드롭다운 닫기
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target)) {
        setIsOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // ESC 키로 닫기
  useEffect(() => {
    const handleKeyDown = (e) => {
      if (e.key === 'Escape') setIsOpen(false);
    };
    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen]);

  /** 로그아웃 처리 */
  const handleLogout = async () => {
    setIsOpen(false);
    await logout();
    navigate('/', { replace: true });
  };

  return (
    <div className="relative" ref={dropdownRef}>
      <button
        type="button"
        onClick={() => setIsOpen((prev) => !prev)}
        className="flex items-center gap-1.5 px-3 py-1.5 text-[13px] font-semibold text-gray-700 hover:bg-gray-100/60 rounded-xl transition-colors duration-200"
        aria-label="사용자 메뉴"
        aria-expanded={isOpen}
        aria-haspopup="true"
      >
        <span className="max-w-[100px] truncate">{user?.nickname || '사용자'}</span>
        <svg
          className={`w-3.5 h-3.5 text-gray-400 transition-transform duration-200 ${isOpen ? 'rotate-180' : ''}`}
          fill="none"
          stroke="currentColor"
          viewBox="0 0 24 24"
          aria-hidden="true"
        >
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2.5} d="M19 9l-7 7-7-7" />
        </svg>
      </button>

      {/* 드롭다운 메뉴 */}
      {isOpen && (
        <div
          className="absolute right-0 mt-1.5 w-36 bg-white rounded-xl border border-gray-100 shadow-lg shadow-gray-200/60 py-1.5 z-50"
          role="menu"
        >
          <Link
            to="/mypage"
            onClick={() => setIsOpen(false)}
            className="block px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors duration-200"
            role="menuitem"
          >
            마이페이지
          </Link>
          <button
            type="button"
            onClick={handleLogout}
            className="w-full text-left px-4 py-2 text-sm text-gray-700 hover:bg-gray-50 transition-colors duration-200"
            role="menuitem"
          >
            로그아웃
          </button>
        </div>
      )}
    </div>
  );
}
