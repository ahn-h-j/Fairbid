import { BrowserRouter, Routes, Route } from 'react-router-dom';
import { AuthProvider } from './contexts/AuthContext';
import Layout from './components/Layout';
import AuctionListPage from './pages/AuctionListPage';
import AuctionDetailPage from './pages/AuctionDetailPage';
import AuctionCreatePage from './pages/AuctionCreatePage';
import LoginPage from './pages/LoginPage';
import AuthCallbackPage from './pages/AuthCallbackPage';
import OnboardingPage from './pages/OnboardingPage';
import MyPage from './pages/MyPage';
import ProtectedRoute from './components/ProtectedRoute';

/**
 * 앱 루트 컴포넌트
 * AuthProvider로 인증 상태를 전역 관리하고, React Router v7로 SPA 라우팅을 구성한다.
 */
export default function App() {
  return (
    <BrowserRouter>
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
            </Route>
          </Route>
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
