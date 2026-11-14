import type { ReactNode } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import { Check, Coffee, Fan, HeartHandshake, Thermometer, Wind } from 'lucide-react';
import { Button, Card, CardTitle, PageHeading, Screen, Seal, StatusPill, TopBar } from '../components/ui';
import { useRequest } from '../hooks/useRequest';
import { fmtTime } from '../lib/time';
import { DISCOMFORT_OPTIONS, SEAT_LABEL, type Discomfort } from '../types';
import { honorific, useSessionCtx } from '../session/context';

type Step = { icon: ReactNode; title: string; desc: string };

/** 불편 종류별로 차량에서 일어날 일 (스키마 6.1) */
function stepsFor(value: Discomfort, driverName: string, restStop: string | null): Step[] {
  const steps: Step[] = [
    {
      icon: <Check size={20} />,
      title: '차량 화면에 요청 표시',
      desc: `${driverName ? `${driverName}님이` : '운전자가'} 안전할 때 확인해요.`,
    },
  ];
  if (value === 'nausea')
    steps.push({ icon: <Fan size={20} />, title: '영상 일시 정지 · 환기 제안', desc: '운전자 확인 후 설정을 조정해요.' });
  if (value === 'windy')
    steps.push({ icon: <Wind size={20} />, title: '바람 조절 요청', desc: '운전자가 확인 후 풍량이나 방향을 조정해요.' });
  if (value === 'cold' || value === 'hot')
    steps.push({ icon: <Thermometer size={20} />, title: '온도 조정 제안', desc: '운전자 확인 후 설정을 조정해요.' });
  if (value === 'nausea' || value === 'rest')
    steps.push({
      icon: <Coffee size={20} />,
      title: '가까운 휴식 장소 추천',
      desc: restStop ?? '경로 정보를 확인하고 있어요.',
    });
  return steps;
}

/** M09 잘 전해드렸어요 */
export default function SentPage() {
  const { requestId } = useParams();
  const { sid, me, name, session } = useSessionCtx();
  const navigate = useNavigate();
  const { loading, request } = useRequest(sid, requestId);

  const back = <Button onClick={() => navigate('../home')}>내 케어 상태로 돌아가기</Button>;

  if (loading)
    return (
      <Screen header={<TopBar />}>
        <PageHeading title="요청을 확인하는 중…" />
      </Screen>
    );
  if (!request || !me)
    return (
      <Screen header={<TopBar />} action={back}>
        <PageHeading title="요청을 찾을 수 없어요" sub="홈으로 돌아가서 다시 시도해 주세요." />
      </Screen>
    );

  const label = DISCOMFORT_OPTIONS.find((o) => o.value === request.value)?.label ?? request.value;
  const sentAt = request.createdAt?.toDate?.() ?? new Date();
  const stop = session.route?.nextRestStop;
  const restStop = stop ? `${stop.name} · 약 ${stop.etaMin}분 거리` : null;

  return (
    <Screen header={<TopBar />} action={back}>
      <PageHeading title="잘 전해드렸어요" sub={`${honorific(name)}의 불편 요청이 차량에 도착했어요.`} />

      <Seal>
        <HeartHandshake size={40} />
      </Seal>

      <div className="center-col">
        <div className="chips">
          <StatusPill tone="discomfort">
            {fmtTime(sentAt)} · {label}
          </StatusPill>
          {request.status === 'done' && <StatusPill>운전자 확인됨</StatusPill>}
        </div>
        <p className="heading__sub">
          {SEAT_LABEL[me.seat]} · {name} — 운전자에게 요청을 전달했어요.
        </p>
      </div>

      <Card>
        <CardTitle>이제 이렇게 도와드려요</CardTitle>
        <ul className="steps">
          {stepsFor(request.value, session.driverName, restStop).map((s) => (
            <li key={s.title} className="steps__item">
              <span className="steps__icon">{s.icon}</span>
              <div>
                <p className="steps__title">{s.title}</p>
                <p className="steps__desc">{s.desc}</p>
              </div>
            </li>
          ))}
        </ul>
      </Card>

      <div className="info-box info-box--tint">
        지금은 화면을 잠시 내려놓고 쉬어주세요. 불편함이 계속되면 보호자에게 직접 알려주세요.
      </div>
    </Screen>
  );
}
