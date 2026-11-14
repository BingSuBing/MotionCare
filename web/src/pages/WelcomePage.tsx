import { useNavigate } from 'react-router-dom';
import { Armchair, Car, Check, Heart, SlidersHorizontal } from 'lucide-react';
import { Button, Card, CardTitle, PageHeading, Screen, StatusPill, TopBar } from '../components/ui';
import { honorific, useSessionCtx } from '../session/context';

/** M01 초대 받기 (사진 없음, 연결 상태는 배지 하나로 간략하게) */
export default function WelcomePage() {
  const { session } = useSessionCtx();
  const navigate = useNavigate();
  const driver = session.driverName || '운전자';

  return (
    <Screen
      header={<TopBar onBack={() => navigate('../name')} />}
      action={<Button onClick={() => navigate('../seat')}>내 좌석 선택하기</Button>}
    >
      <PageHeading
        title={`${honorific(driver)}의 초대를 받았어요`}
        sub={`${session.carNumber ? `${session.carNumber}의 ` : ''}편안타에 참여할게요.`}
      />

      <Card tone="tint">
        <div className="row-between">
          <CardTitle icon={<Car size={22} />}>{session.tripTitle || '함께하는 여정'}</CardTitle>
          <StatusPill>
            <Check size={14} strokeWidth={3} />
            연결됨
          </StatusPill>
        </div>
        <p className="card-text">{driver}님의 차량에서 초대했어요.</p>
      </Card>

      <ul className="feature-list">
        <li>
          <Armchair size={20} />
          내 좌석과 취향을 알려주세요
        </li>
        <li>
          <SlidersHorizontal size={20} />
          멤버와 차량 환경을 함께 조율해요
        </li>
        <li>
          <Heart size={20} />
          불편할 땐 가볍게 요청해요
        </li>
      </ul>

      <p className="helper">취향은 차량의 운전자에게 공유됩니다. 차량 제어는 운전자가 확인 후 적용해요.</p>
    </Screen>
  );
}
