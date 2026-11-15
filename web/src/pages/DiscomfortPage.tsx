import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { Coffee, Frown, Heart, Snowflake, Sun, Wind } from 'lucide-react';
import { Button, Card, CardTitle, OptionRow, PageHeading, Screen, TopBar } from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { sendDiscomfort } from '../lib/requests';
import { DISCOMFORT_OPTIONS, type Discomfort } from '../types';
import { honorific, useSessionCtx } from '../session/context';

const DISCOMFORT_ICON: Record<Discomfort, ReactNode> = {
  nausea: <Frown size={22} />,
  cold: <Snowflake size={22} />,
  hot: <Sun size={22} />,
  windy: <Wind size={22} />,
  rest: <Coffee size={22} />,
};

/** 버튼 문구 "메스꺼움 요청 보내기" */
const NOUN: Record<Discomfort, string> = {
  nausea: '메스꺼움',
  cold: '추위',
  hot: '더위',
  windy: '바람 세기',
  rest: '휴식',
};

const ADVICE: Record<Discomfort, { title: string; body: string }> = {
  nausea: {
    title: '화면을 잠깐 쉬어주세요',
    body: '시선을 편안히 두고 쉬어보세요. 증상이 계속되면 안전한 장소에서 충분히 쉬는 것이 좋아요.',
  },
  cold: { title: '운전자에게 온도 조정을 제안할게요', body: '담요나 겉옷이 있다면 함께 써 보세요.' },
  hot: { title: '운전자에게 온도 조정을 제안할게요', body: '물을 조금 마시면 도움이 될 수 있어요.' },
  windy: { title: '운전자에게 바람 조절을 부탁할게요', body: '송풍구 방향을 살짝 돌려 보면 바로 편해질 수 있어요.' },
  rest: { title: '가까운 휴식 장소를 함께 찾아볼게요', body: '운전자가 안전한 곳을 확인하고 알려드려요.' },
};

/** M08 어떤 점이 불편한가요? */
export default function DiscomfortPage() {
  const { sid, uid, me, name } = useSessionCtx();
  const navigate = useNavigate();
  const [value, setValue] = useState<Discomfort | null>(null);
  const { saving, error, run } = useSaving();

  const send = async () => {
    if (!me || !value) return;
    let id = '';
    const ok = await run(async () => {
      id = await sendDiscomfort({ sid, seat: me.seat, uid, value });
    });
    if (ok) navigate(`../sent/${id}`, { replace: true });
  };

  return (
    <Screen
      header={<TopBar onBack={() => navigate('../home')} />}
      action={
        <Button onClick={send} disabled={!value || saving}>
          {saving ? '보내는 중…' : value ? `${NOUN[value]} 요청 보내기` : '불편한 점을 골라 주세요'}
        </Button>
      }
    >
      <PageHeading title="어떤 점이 불편한가요?" sub={`${honorific(name)}의 상태를 운전자에게 알려드릴게요.`} />

      <div className="stack">
        {DISCOMFORT_OPTIONS.map((o) => (
          <OptionRow
            key={o.value}
            tone="discomfort"
            compact
            icon={DISCOMFORT_ICON[o.value]}
            label={o.label}
            selected={value === o.value}
            onSelect={() => setValue(o.value)}
          />
        ))}
      </div>

      {value && (
        <Card tone="tint">
          <CardTitle icon={<Heart size={20} />}>{ADVICE[value].title}</CardTitle>
          <p className="card-text">{ADVICE[value].body}</p>
        </Card>
      )}

      {error && <div className="info-box info-box--error">{error}</div>}
      <p className="helper">요청을 보내도 차량 설정은 바로 바뀌지 않아요. 운전자가 안전할 때 확인하고 도와드려요.</p>
    </Screen>
  );
}
