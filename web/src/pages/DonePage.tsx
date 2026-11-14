import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { Button, Card, CardTitle, PageHeading, Screen, Seal, StatusPill, TopBar } from '../components/ui';
import {
  FAN_LABEL,
  SEAT_LABEL,
  SENSITIVITY_OPTIONS,
  TEMP_STATUS_LABEL,
  fmtTemp,
  medianTemp,
} from '../types';
import { honorific, useSessionCtx } from '../session/context';

/**
 * 제출 완료
 * 피그마의 "함께 조율한 추천"은 다른 내용으로 바뀔 예정이라,
 * 그 자리에 지금 동작하는 "모두의 희망 온도"(중앙값)를 보여줘요.
 */
export default function DonePage() {
  const { me, name, passengers, session } = useSessionCtx();
  const navigate = useNavigate();
  if (!me) return null;

  const hvac = session.hvacState;
  const voters = passengers.filter((p) => typeof p.desiredTemp === 'number');
  const preview = medianTemp(voters.map((p) => p.desiredTemp));
  const shown = hvac?.medianTemp ?? preview;
  const synced = hvac?.medianTemp != null && hvac.medianTemp === preview;
  const statusLabel = synced && hvac ? TEMP_STATUS_LABEL[hvac.tempStatus] : '차량에 전달 중';

  const rows: [string, string][] = [
    ['멀미 민감도', SENSITIVITY_OPTIONS.find((o) => o.value === me.sensitivity)?.label ?? '-'],
    ['희망 온도', me.desiredTemp != null ? `${fmtTemp(me.desiredTemp)}°C` : '-'],
    ['희망 풍량', me.fanPreference ? FAN_LABEL[me.fanPreference] : '-'],
    ['휴식 알림', me.restIntervalMin ? `${me.restIntervalMin}분마다` : '꺼짐'],
  ];

  const voteText =
    voters.map((p) => `${p.name || '탑승자'} ${fmtTemp(p.desiredTemp as number)}°`).join(' · ') +
    (voters.length > 1 && preview != null ? ` → 중앙값 ${fmtTemp(preview)}°` : '');

  return (
    <Screen
      header={<TopBar onBack={() => navigate('../climate')} />}
      action={<Button onClick={() => navigate('../home')}>실시간 케어 확인하기</Button>}
    >
      <div className="heading-row">
        <PageHeading title="설정이 제출되었습니다" sub={`${honorific(name)}의 취향을 차량에 전했어요.`} />
        <Seal>
          <Check size={32} />
        </Seal>
      </div>

      <Card>
        <div className="row-between">
          <CardTitle>
            {name} ({SEAT_LABEL[me.seat]})
          </CardTitle>
          <StatusPill>제출 완료</StatusPill>
        </div>
        <dl className="summary">
          {rows.map(([k, v]) => (
            <div key={k} className="summary__row">
              <dt>{k}</dt>
              <dd>{v}</dd>
            </div>
          ))}
        </dl>
      </Card>

      <Card tone="tint">
        <CardTitle>모두의 희망 온도</CardTitle>
        <p className="big-number card-gap">{shown != null ? `${fmtTemp(shown)}°C` : '-'}</p>
        {voters.length > 0 && <p className="card-text">{voteText}</p>}
        <div className="card-gap">
          <StatusPill tone="neutral">{statusLabel}</StatusPill>
        </div>
      </Card>
    </Screen>
  );
}
