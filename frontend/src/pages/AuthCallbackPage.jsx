import { useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../contexts/AuthContext';
import Spinner from '../components/Spinner';

/**
 * OAuth 콜백 처리 페이지
 * 서버에서 리다이렉트된 후 Refresh Token 쿠키로 Access Token을 발급받고,
 * 온보딩 상태에 따라 적절한 페이지로 이동한다.
 */
export default function AuthCallbackPage() {
  const { restoreSession } = useAuth();
  const navigate = useNavigate();
  const calledRef = useRef(false);

  useEffect(() => {
    // StrictMode 중복 실행 방지
    if (calledRef.current) return;
    calledRef.current = true;

    const handleCallback = async () => {
      const token = await restoreSession();

      if (!token) {
        navigate('/login', { replace: true });
        return;
      }

      // JWT에서 onboarded 확인
      try {
        const payload = JSON.parse(atob(token.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')));

        if (!payload.onboarded) {
          navigate('/onboarding', { replace: true });
        } else {
          const redirectPath = localStorage.getItem('redirectAfterLogin') || '/';
          localStorage.removeItem('redirectAfterLogin');
          navigate(redirectPath, { replace: true });
        }
      } catch {
        // JWT 파싱 실패 시 홈으로 이동
        navigate('/', { replace: true });
      }
    };

    handleCallback();
  }, [restoreSession, navigate]);

  return (
    <div className="flex flex-col items-center justify-center min-h-[70vh] gap-4">
      <Spinner />
      <p className="text-sm text-gray-500">로그인 처리 중…</p>
    </div>
  );
}
