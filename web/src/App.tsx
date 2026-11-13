import { Routes, Route } from 'react-router-dom';
import { Notice } from './components/ui';
import UiPreview from './pages/UiPreview';
import JoinEntry from './pages/JoinEntry';
import NamePage from './pages/NamePage';
import WelcomePage from './pages/WelcomePage';
import SeatPage from './pages/SeatPage';
import SensitivityPage from './pages/SensitivityPage';
import ClimatePage from './pages/ClimatePage';
import CarePage from './pages/CarePage';
import DonePage from './pages/DonePage';
import ComingSoon from './pages/ComingSoon';
import SessionLayout, { RequireName, RequireSeat, StartRedirect } from './session/SessionLayout';

export default function App() {
  return (
    <Routes>
      {/* QR 주소 */}
      <Route path="/join" element={<JoinEntry />} />

      {/* 세션 화면 (docs/web-screens.md "경로") */}
      <Route path="/s/:sid" element={<SessionLayout />}>
        <Route index element={<StartRedirect />} />
        <Route path="name" element={<NamePage />} />
        <Route element={<RequireName />}>
          <Route path="welcome" element={<WelcomePage />} />
          <Route path="seat" element={<SeatPage />} />
        </Route>
        <Route element={<RequireSeat />}>
          <Route path="sensitivity" element={<SensitivityPage />} />
          <Route path="climate" element={<ClimatePage />} />
          <Route path="care" element={<CarePage />} />
          <Route path="done" element={<DonePage />} />
          {/* feature/web-home */}
          <Route path="home" element={<ComingSoon title="편안한 이동" />} />
          <Route path="discomfort" element={<ComingSoon title="어떤 점이 불편한가요?" />} />
          <Route path="sent/:requestId" element={<ComingSoon title="잘 전해드렸어요" />} />
        </Route>
      </Route>

      {/* 개발용 */}
      <Route path="/dev/ui" element={<UiPreview />} />

      <Route path="*" element={<Notice title="편안타" body="차량 화면의 QR 코드를 찍어 주세요." />} />
    </Routes>
  );
}
