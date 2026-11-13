import { Routes, Route } from 'react-router-dom';
import UiPreview from './pages/UiPreview';
import { Notice } from './components/ui';

export default function App() {
  return (
    <Routes>
      {/* 다음 브랜치(feature/web-join)에서 /join 화면을 연결해요 */}
      <Route path="/dev/ui" element={<UiPreview />} />
      <Route path="*" element={<Notice title="편안타" body="차량 화면의 QR 코드를 찍어 주세요." />} />
    </Routes>
  );
}
