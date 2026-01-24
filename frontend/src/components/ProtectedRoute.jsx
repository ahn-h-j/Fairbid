import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth, AUTH_STATE } from '../contexts/AuthContext';
import Spinner from './Spinner';

/**
 * 인증 필요 라우트 가드 컴포넌트
 * 비로그인 시 /login으로, 온보딩 미완료 시 /onboarding으로 리다이렉트한다.
 *
 * @param {object} props
 * @param {boolean} [props.requireOnboarding=false] - true면 온보딩 완료도 필수
 */
export default function ProtectedRoute({ requireOnboarding = false }) {
  const { authState } = useAuth();
  const location = useLocation();

  // 초기 로딩 중에는 스피너 표시
  if (authState === AUTH_STATE.LOADING) {
    return (
      <div className="flex items-center justify-center min-h-[60vh]">
        <Spinner />
      </div>
    );
  }

  // 비로그인 → 로그인 페이지로 이동 (현재 전체 경로를 state로 전달)
  if (authState === AUTH_STATE.UNAUTHENTICATED) {
    const fullPath = location.pathname + location.search + location.hash;
    return <Navigate to="/login" state={{ from: fullPath }} replace />;
  }

  // 온보딩 필요 라우트인데 온보딩 미완료 → 온보딩 페이지로 이동
  if (requireOnboarding && authState === AUTH_STATE.ONBOARDING_REQUIRED) {
    const fullPath = location.pathname + location.search + location.hash;
    return <Navigate to="/onboarding" state={{ from: fullPath }} replace />;
  }

  return <Outlet />;
}
