import { useNavigate } from 'react-router-dom';
import { Check } from 'lucide-react';
import { Button, Card, CardTitle, PageHeading, Screen, Seal, StatusPill, TopBar } from '../components/ui';
import {
  SEAT_LABEL,
  SENSITIVITY_OPTIONS,
  TEMP_STATUS_LABEL,
  VISUAL_MODE_LABEL,
  fmtTemp,
  medianTemp,
} from '../types';
import { captionFor, useSessionCtx } from '../session/context';

/** M06 설정이 제출되었습니다 */
export default function DonePage() {
  const { me, name, passengers, session } = useSessionCtx();
  const navigate = useNavigate();
  if (!me) return null;

  const hvac = session.hvacState;
  const voters = passengers.filter((p) => typeof p.desiredTemp === 'number');
  // 차량 앱이 계산하기 전에도 같은 규칙으로 미리 보여줘요
  const preview = medianTemp(voters.map((p) => p.desiredTemp));
  const shown = hvac?.medianTemp ?? preview;
  // 차량 앱이 아직 최신 희망 온도를 반영하지 않았으면 "반영 중"
  const synced = hvac?.medianTemp != null && hvac.medianTemp === preview;
  const statusLabel = synced && hvac ? TEMP_STATUS_LABEL[hvac.tempStatus] : '차량에 전달 중';

  const sensitivityLabel = SENSITIVITY_OPTIONS.find((o) => o.value === me.sensitivity)?.label ?? '-';
  const visualLabel =
    me.visualMode === 'off' ? '사용 안 함' : `${VISUAL_MODE_LABEL[me.visualMode]} · ${me.visualIntensity}%`;

  const rows: [string, string][] = [
    ['멀미 민감도', sensitivityLabel],
    ['희망 온도', me.desiredTemp != null ? `${fmtTemp(me.desiredTemp)}°C` : '-'],
    ['시각 케어', visualLabel],
    ['휴식 알림', me.restIntervalMin ? `${me.restIntervalMin}분마다` : '꺼짐'],
  ];

  const voteText =
    voters.map((p) => `${p.name || '탑승자'} ${fmtTemp(p.desiredTemp as number)}°`).join(' · ') +
    (voters.length > 1 && preview != null ? ` → 중앙값 ${fmtTemp(preview)}°` : '');

  return (
    <Screen
      header={<TopBar step={6} onBack={() => navigate('../care')} />}
      action={<Button onClick={() => navigate('../home')}>실시간 케어 확인하기</Button>}
      caption={captionFor(name)}
    >
      <PageHeading title="설정이 제출되었습니다" sub={`${name}님의 취향을 차량에 전했어요.`} />

      <Seal>
        <Check size={40} />
      </Seal>

      <Card>
        <div className="row-between">
          <CardTitle>
            {name} · {SEAT_LABEL[me.seat]}
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

      <div className="center">
        <Button variant="tertiary" onClick={() => navigate('../sensitivity')}>
          내 설정 수정하기
        </Button>
      </div>
    </Screen>
  );
}
