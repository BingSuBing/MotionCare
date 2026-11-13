import { useNavigate } from 'react-router-dom';
import { Button, Card, PageHeading, Screen, TopBar } from '../components/ui';
import { SEAT_LABEL } from '../types';
import { captionFor, useSessionCtx } from '../session/context';

/** 아직 만들지 않은 화면 자리 (web-profile, web-home 브랜치에서 교체) */
export default function ComingSoon({ title }: { title: string }) {
  const { me, name } = useSessionCtx();
  const navigate = useNavigate();

  return (
    <Screen
      header={<TopBar onBack={() => navigate('../seat')} />}
      action={
        <Button variant="secondary" onClick={() => navigate('../seat')}>
          좌석 다시 고르기
        </Button>
      }
      caption={captionFor(name)}
    >
      <PageHeading title={title} sub="이 화면은 준비 중이에요." />
      {me && (
        <Card tone="tint">
          <p className="card-strong card-strong--flush">
            {name} · {SEAT_LABEL[me.seat]} 등록 완료
          </p>
        </Card>
      )}
    </Screen>
  );
}
