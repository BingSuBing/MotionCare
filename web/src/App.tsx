import { Routes, Route, useSearchParams } from 'react-router-dom';
import './firebase';
import { isSeat, SEAT_LABEL } from './types';

function Join() {
  const [params] = useSearchParams();
  const session = params.get('session');
  const seat = params.get('seat');
  if (!session || !isSeat(seat)) return <p>QR 코드를 다시 찍어 주세요.</p>;
  return (
    <div>
      <h1>MotionCare</h1>
      <p>세션: {session}</p>
      <p>좌석: {SEAT_LABEL[seat]}</p>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/join" element={<Join />} />
      <Route path="*" element={<p>차량 화면의 QR 코드를 찍어 주세요.</p>} />
    </Routes>
  );
}