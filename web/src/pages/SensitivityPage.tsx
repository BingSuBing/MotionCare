import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, OptionRow, PageHeading, ProgressBar, Screen, TopBar } from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { updateMyPassenger } from '../lib/passengers';
import { SENSITIVITY_OPTIONS, TRIGGER_OPTIONS, type Sensitivity, type SicknessTrigger } from '../types';
import { honorific, useSessionCtx } from '../session/context';

/** 처음 설정 2/3: 멀미 정도 */
export default function SensitivityPage() {
  const { sid, me, name } = useSessionCtx();
  const navigate = useNavigate();
  const [sensitivity, setSensitivity] = useState<Sensitivity | null>(me?.sensitivity ?? null);
  const [triggers, setTriggers] = useState<SicknessTrigger[]>(me?.sicknessTriggers ?? []);
  const { saving, error, run } = useSaving();

  const toggleTrigger = (t: SicknessTrigger) =>
    setTriggers((prev) => (prev.includes(t) ? prev.filter((x) => x !== t) : [...prev, t]));

  const next = async () => {
    if (!me || !sensitivity) return;
    const ok = await run(() => updateMyPassenger(sid, me.seat, { sensitivity, sicknessTriggers: triggers }));
    if (ok) navigate('../climate');
  };

  return (
    <Screen
      header={<TopBar step={2} total={3} onBack={() => navigate('../seat')} />}
      action={
        <Button onClick={next} disabled={!sensitivity || saving}>
          {saving ? '저장하는 중…' : '실내 취향 설정하기'}
        </Button>
      }
    >
      <ProgressBar current={2} />
      <PageHeading title="멀미 정도를 알려주세요" sub={`${honorific(name)}에게 맞는 케어를 준비할게요.`} />

      <div className="stack">
        {SENSITIVITY_OPTIONS.map((o) => (
          <OptionRow
            key={o.value}
            label={o.label}
            desc={o.desc}
            selected={sensitivity === o.value}
            onSelect={() => setSensitivity(o.value)}
          />
        ))}
      </div>

      <p className="section-title">함께 알려주세요 · 선택</p>
      <div className="stack">
        {TRIGGER_OPTIONS.map((o) => (
          <OptionRow
            key={o.value}
            compact
            mark="check"
            label={o.label}
            selected={triggers.includes(o.value)}
            onSelect={() => toggleTrigger(o.value)}
          />
        ))}
      </div>

      {error && <div className="info-box info-box--error">{error}</div>}
    </Screen>
  );
}
