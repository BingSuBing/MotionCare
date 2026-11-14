import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  CardTitle,
  Chip,
  PageHeading,
  ProgressBar,
  Screen,
  Segmented,
  TempStepper,
  Toggle,
  TopBar,
} from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { updateMyPassenger } from '../lib/passengers';
import { FAN_OPTIONS, REST_INTERVALS, type FanPreference } from '../types';
import { initialTemp } from '../lib/temp';
import { useSessionCtx } from '../session/context';

/**
 * 처음 설정 3/3: 실내 취향 → 제출
 * 희망 풍량·직접 바람은 차량을 직접 제어하지 않고, 취향 조율(가중치)에 참고해요.
 */
export default function ClimatePage() {
  const { sid, me, session } = useSessionCtx();
  const navigate = useNavigate();
  const [temp, setTemp] = useState(() => initialTemp(me?.desiredTemp, session.hvacState));
  const [fan, setFan] = useState<FanPreference | null>(me?.fanPreference ?? null);
  const [avoidWind, setAvoidWind] = useState<boolean | null>(me?.avoidDirectWind ?? null);
  const [restOn, setRestOn] = useState(me?.restIntervalMin != null);
  const [interval, setInterval] = useState<number>(me?.restIntervalMin ?? 60);
  const { saving, error, run } = useSaving();

  const submit = async () => {
    if (!me) return;
    const ok = await run(() =>
      updateMyPassenger(sid, me.seat, {
        desiredTemp: temp,
        fanPreference: fan,
        avoidDirectWind: avoidWind,
        restIntervalMin: restOn ? interval : null,
        submit: true,
      }),
    );
    if (ok) navigate('../done');
  };

  return (
    <Screen
      header={<TopBar step={3} total={3} onBack={() => navigate('../sensitivity')} />}
      action={
        <Button onClick={submit} disabled={saving}>
          {saving ? '제출하는 중…' : '내 설정 제출하기'}
        </Button>
      }
    >
      <ProgressBar current={3} />
      <PageHeading title="어떤 실내가 편안한가요?" sub="내 취향으로 차량 환경을 함께 조율해요." />

      <p className="section-title">희망 온도</p>
      <TempStepper value={temp} onChange={setTemp} compact />

      <p className="section-title">바람 세기</p>
      <Segmented options={FAN_OPTIONS} value={fan} onChange={setFan} tone="white" />

      <p className="section-title">바람에 민감한가요?</p>
      <div className="chips">
        <Chip selected={avoidWind === true} onClick={() => setAvoidWind(true)}>
          직접 바람은 싫어요
        </Chip>
        <Chip selected={avoidWind === false} onClick={() => setAvoidWind(false)}>
          괜찮아요
        </Chip>
      </div>

      <Card>
        <div className="row-between">
          <CardTitle>정기 휴식 알림</CardTitle>
          <Toggle on={restOn} onChange={setRestOn} label="정기 휴식 알림" />
        </div>
        {restOn && (
          <div className="card-gap">
            <Segmented
              options={REST_INTERVALS.map((m) => ({ value: m as number, label: `${m}분` }))}
              value={interval}
              onChange={setInterval}
            />
          </div>
        )}
      </Card>

      {error && <div className="info-box info-box--error">{error}</div>}
    </Screen>
  );
}
