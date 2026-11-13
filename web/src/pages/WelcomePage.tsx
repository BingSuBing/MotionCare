import { useNavigate } from 'react-router-dom';
import { Armchair, Car, Heart, SlidersHorizontal } from 'lucide-react';
import { Button, Card, CardTitle, PageHeading, Screen, TopBar } from '../components/ui';
import { captionFor, useSessionCtx } from '../session/context';

/** M01 편안타에 참여하기 */
export default function WelcomePage() {
  const { session, name } = useSessionCtx();
  const navigate = useNavigate();

  return (
    <Screen
      header={<TopBar step={1} onBack={() => navigate('../name')} />}
      action={<Button onClick={() => navigate('../seat')}>내 좌석 선택하기</Button>}
      caption={captionFor(name)}
    >
      <PageHeading title="편안타에 참여하기" sub="현재 차량의 탑승자 케어 설정에 참여하세요" />

      <img className="hero-image" src="/images/welcome.png" alt="뒷좌석에서 웃고 있는 아이들 일러스트" />

      <Card tone="tint">
        <CardTitle icon={<Car size={22} />}>{session.tripTitle || '함께하는 여정'}</CardTitle>
        <p className="card-text">
          {session.driverName ? `${session.driverName}님의 차량에서 초대했어요.` : '차량에서 초대했어요.'}
        </p>
        <p className="card-strong">차량 연결 확인됨</p>
      </Card>

      <ul className="feature-list">
        <li>
          <Armchair size={20} />
          내 좌석과 취향을 알려주세요
        </li>
        <li>
          <SlidersHorizontal size={20} />
          가족의 취향을 함께 조율해요
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
