import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, PageHeading, ProgressBar, Screen, TempStepper, TopBar } from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { updateMyPassenger } from '../lib/passengers';
import { TEMP_MAX, TEMP_MIN, cabinTemp, type HvacState } from '../types';
import { captionFor, useSessionCtx } from '../session/context';

const DEFAULT_TEMP = 24;

/** 희망 온도 시작값: 저장된 값 → 지금 실내 온도(정수) → 24℃ */
function initialTemp(saved: number | null | undefined, hvac: HvacState | undefined) {
  const base = saved ?? (hvac ? Math.round(cabinTemp(hvac)) : DEFAULT_TEMP);
  return Math.min(TEMP_MAX, Math.max(TEMP_MIN, base));
}

/** M04 어떤 실내가 편안한가요? (희망 온도만. 바람·조명은 범위에서 제외) */
export default function ClimatePage() {
  const { sid, me, name, session } = useSessionCtx();
  const navigate = useNavigate();
  const [temp, setTemp] = useState(() => initialTemp(me?.desiredTemp, session.hvacState));
  const { saving, error, run } = useSaving();

  const next = async () => {
    if (!me) return;
    const ok = await run(() => updateMyPassenger(sid, me.seat, { desiredTemp: temp }));
    if (ok) navigate('../care');
  };

  return (
    <Screen
      header={<TopBar step={4} onBack={() => navigate('../sensitivity')} />}
      action={
        <Button onClick={next} disabled={saving}>
          {saving ? '저장하는 중…' : '케어 취향 설정하기'}
        </Button>
      }
      caption={captionFor(name)}
    >
      <ProgressBar current={3} />
      <PageHeading title="어떤 실내가 편안한가요?" sub="내 취향을 알려주시면 함께 조율해요." />

      <TempStepper value={temp} onChange={setTemp} />

      {error && <div className="info-box info-box--error">{error}</div>}
      <p className="helper">
        모든 탑승자의 희망 온도 중앙값으로 실내 온도를 맞춰요. 운전자가 확인 후 적용해요.
      </p>
    </Screen>
  );
}
