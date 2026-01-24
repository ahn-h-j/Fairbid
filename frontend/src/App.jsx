import { BrowserRouter, Routes, Route } from 'react-router-dom';
import Layout from './components/Layout';
import AuctionListPage from './pages/AuctionListPage';
import AuctionDetailPage from './pages/AuctionDetailPage';
import AuctionCreatePage from './pages/AuctionCreatePage';

/**
 * 앱 루트 컴포넌트
 * React Router v6로 SPA 라우팅을 구성한다.
 */
export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route path="/" element={<AuctionListPage />} />
          <Route path="/auctions/create" element={<AuctionCreatePage />} />
          <Route path="/auctions/:id" element={<AuctionDetailPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  );
}
