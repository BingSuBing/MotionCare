import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { Button, Card, CardTitle, PageHeading, Screen, Seal, StatusPill, TopBar } from '../components/ui';
import {
  FAN_LABEL,
  SEAT_LABEL,
  SENSITIVITY_OPTIONS,
  TEMP_STATUS_LABEL,
  fmtTemp,
} from '../types';
import { fmtTime } from '../lib/time';
import { honorific, useSessionCtx } from '../session/context';

/**
 * 제출 완료
 * "함께 조율한 추천 온도": 차량 앱이 30분마다 멀미 가중치로 계산한 값 (스키마 3.2)
 */
export default function DonePage() {
  const { me, name, passengers, session } = useSessionCtx();
  const navigate = useNavigate();
  if (!me) return null;

  const hvac = session.hvacState;
  const voters = passengers.filter((p) => typeof p.desiredTemp === 'number');
  const lead = hvac?.leadSeat ? passengers.find((p) => p.seat === hvac.leadSeat) : undefined;
  const nextReview = hvac?.nextReviewAt?.toDate();

  const rows: [string, string][] = [
    ['멀미 민감도', SENSITIVITY_OPTIONS.find((o) => o.value === me.sensitivity)?.label ?? '-'],
    ['희망 온도', me.desiredTemp != null ? `${fmtTemp(me.desiredTemp)}°C` : '-'],
    ['희망 풍량', me.fanPreference ? FAN_LABEL[me.fanPreference] : '-'],
    ['휴식 알림', me.restIntervalMin ? `${me.restIntervalMin}분마다` : '꺼짐'],
  ];

  const voteText = voters.map((p) => `${p.name || '탑승자'} ${fmtTemp(p.desiredTemp as number)}°`).join(' · ');

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
        <CardTitle>함께 조율한 추천 온도</CardTitle>
        {hvac?.medianTemp != null ? (
          <>
            <p className="big-number card-gap">{fmtTemp(hvac.medianTemp)}°C</p>
            {lead && <p className="card-text">멀미를 가장 많이 느끼는 {honorific(lead.name)}을 더 고려했어요.</p>}
            {voters.length > 0 && <p className="card-text">{voteText}</p>}
            <div className="card-gap row-between">
              <StatusPill tone="neutral">{TEMP_STATUS_LABEL[hvac.tempStatus]}</StatusPill>
              {nextReview && <span className="saved-note">다음 조정 {fmtTime(nextReview)}</span>}
            </div>
          </>
        ) : (
          <p className="card-text card-gap">모두의 희망 온도와 멀미 정도를 모아 추천 온도를 계산하고 있어요.</p>
        )}
      </Card>
    </Screen>
  );
}
