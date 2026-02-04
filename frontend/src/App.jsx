import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import Layout from './components/Layout';
import LandingPage from './pages/LandingPage';
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
 */
export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <Routes>
          {/* 랜딩 페이지 (Layout 없이) */}
          <Route path="/" element={<LandingPage />} />

          <Route element={<Layout />}>
            {/* 공개 라우트 */}
            <Route path="/auctions" element={<AuctionListPage />} />
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
