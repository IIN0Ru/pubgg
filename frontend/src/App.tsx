import { useEffect } from 'react';
import { Navigate, Route, Routes, useLocation, useNavigationType } from 'react-router';
import { CardPage } from './pages/CardPage';
import { HomePage } from './pages/HomePage';
import { SearchPage, SharedPage } from './pages/SearchPage';

/** 아직 서버(Thymeleaf)가 그리는 화면: 화면 안 링크로 왔으면 그 주소를 서버에서 새로 연다 */
function ServerPage() {
  useEffect(() => {
    window.location.reload();
  }, []);
  return null;
}

/**
 * 링크를 눌러 다른 화면으로 넘어가면 맨 위부터 보이게.
 * 뒤로 가기(POP)는 브라우저가 위치를 되돌리고, 주소만 고치는 경우(REPLACE: 카드 꾸민 값)는 그대로 둔다.
 */
function ScrollToTop() {
  const { pathname, search } = useLocation();
  const type = useNavigationType();
  useEffect(() => {
    if (type === 'PUSH') window.scrollTo(0, 0);
  }, [pathname, search, type]);
  return null;
}

/**
 * 펍지지 React 화면. 주소마다 한 화면이고, Spring 은 어느 주소든 같은 껍데기(app.html)를 준다.
 * (/s/{id} 만은 링크 미리보기 때문에 서버가 <head> 를 채운다 — docs/architecture.md)
 * 2D 리플레이(/replay)는 아직 서버(Thymeleaf) 화면이다.
 */
export function App() {
  return (
    <>
      <ScrollToTop />
      <Routes>
        <Route path="/" element={<HomePage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/s/:id" element={<SharedPage />} />
        <Route path="/card" element={<CardPage />} />
        <Route path="/replay" element={<ServerPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  );
}
