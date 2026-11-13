import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, OptionRow, PageHeading, ProgressBar, Screen, TopBar } from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { updateMyPassenger } from '../lib/passengers';
import { SENSITIVITY_OPTIONS, TRIGGER_OPTIONS, type Sensitivity, type SicknessTrigger } from '../types';
import { captionFor, useSessionCtx } from '../session/context';

/** M03 이동할 때, 어떠세요? (멀미 민감도) */
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
      header={<TopBar step={3} onBack={() => navigate('../seat')} />}
      action={
        <Button onClick={next} disabled={!sensitivity || saving}>
          {saving ? '저장하는 중…' : '실내 취향 설정하기'}
        </Button>
      }
      caption={captionFor(name)}
    >
      <ProgressBar current={2} />
      <PageHeading title="이동할 때, 어떠세요?" sub={`${name}님에게 맞는 케어를 준비할게요.`} />

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
      <p className="helper">민감도는 언제든 바꿀 수 있어요. 이 정보는 편안함을 돕기 위한 참고이며 진단에 사용되지 않아요.</p>
    </Screen>
  );
}
