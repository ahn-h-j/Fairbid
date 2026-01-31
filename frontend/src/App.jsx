import { useState } from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import Layout from './components/Layout';
import SplashScreen from './components/SplashScreen';
import AuctionListPage from './pages/AuctionListPage';
import AuctionDetailPage from './pages/AuctionDetailPage';
import AuctionCreatePage from './pages/AuctionCreatePage';
import LoginPage from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import OnboardingPage from './pages/OnboardingPage';
import MyPage from './pages/MyPage';
import TradeListPage from './pages/TradeListPage';
import TradeDetailPage from './pages/TradeDetailPage';
import ProtectedRoute from './components/ProtectedRoute';

// Admin pages
import AdminLayout from './pages/admin/AdminLayout';
import DashboardPage from './pages/admin/DashboardPage';
import AuctionManagePage from './pages/admin/AuctionManagePage';
import UserManagePage from './pages/admin/UserManagePage';

/**
 * 앱 루트 컴포넌트
 * AuthProvider로 인증 상태를 전역 관리하고, React Router v7로 SPA 라우팅을 구성한다.
 * 초기 접속 시 스플래시 화면을 표시한다.
 */
export default function App() {
  // 세션당 한 번만 스플래시 표시 (sessionStorage 사용)
  const [showSplash, setShowSplash] = useState(() => {
    return !sessionStorage.getItem('fairbid_splash_shown');
  });

  // 스플래시 완료 처리
  const handleSplashComplete = () => {
    sessionStorage.setItem('fairbid_splash_shown', 'true');
    setShowSplash(false);
  };

  return (
    <BrowserRouter>
      {/* 스플래시 화면 (세션당 최초 1회만 표시) */}
      {showSplash && <SplashScreen onComplete={handleSplashComplete} />}
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            {/* 공개 라우트 */}
            <Route path="/" element={<AuctionListPage />} />
            <Route path="/auctions/:id" element={<AuctionDetailPage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/auth/callback" element={<AuthCallbackPage />} />

            {/* 로그인 필요 라우트 */}
            <Route element={<ProtectedRoute />}>
              <Route path="/onboarding" element={<OnboardingPage />} />
              <Route path="/mypage" element={<MyPage />} />
            </Route>

            {/* 온보딩 완료 필요 라우트 */}
            <Route element={<ProtectedRoute requireOnboarding />}>
              <Route path="/auctions/create" element={<AuctionCreatePage />} />
              <Route path="/trades" element={<TradeListPage />} />
              <Route path="/trades/:tradeId" element={<TradeDetailPage />} />
            </Route>

            {/* 관리자 라우트 (ADMIN 역할 필요, AdminLayout에서 권한 체크) */}
            <Route path="/admin" element={<AdminLayout />}>
              <Route index element={<DashboardPage />} />
              <Route path="auctions" element={<AuctionManagePage />} />
              <Route path="users" element={<UserManagePage />} />
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
