import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlignCenter, Minus, Orbit } from 'lucide-react';
import {
  Button,
  Card,
  CardTitle,
  OptionRow,
  PageHeading,
  ProgressBar,
  Screen,
  Segmented,
  Toggle,
  TopBar,
} from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { updateMyPassenger } from '../lib/passengers';
import { REST_INTERVALS, VISUAL_MODE_LABEL, type VisualMode } from '../types';
import { captionFor, useSessionCtx } from '../session/context';

type CueMode = Exclude<VisualMode, 'off'>;

const CUE_OPTIONS: { value: CueMode; icon: ReactNode }[] = [
  { value: 'dot', icon: <Orbit size={22} /> },
  { value: 'horizon', icon: <AlignCenter size={22} /> },
  { value: 'minimal', icon: <Minus size={22} /> },
];

/** M05 나만의 케어를 골라요 (시각 케어 + 정기 휴식 알림) → 제출 */
export default function CarePage() {
  const { sid, me, name } = useSessionCtx();
  const navigate = useNavigate();
  const saved = me?.visualMode ?? 'dot';
  const [useVisual, setUseVisual] = useState<'yes' | 'no'>(saved === 'off' ? 'no' : 'yes');
  const [mode, setMode] = useState<CueMode>(saved === 'off' ? 'dot' : saved);
  const [restOn, setRestOn] = useState(me?.restIntervalMin != null);
  const [interval, setInterval] = useState<number>(me?.restIntervalMin ?? 60);
  const { saving, error, run } = useSaving();

  const submit = async () => {
    if (!me) return;
    const ok = await run(() =>
      updateMyPassenger(sid, me.seat, {
        visualMode: useVisual === 'yes' ? mode : 'off',
        restIntervalMin: restOn ? interval : null,
        submit: true,
      }),
    );
    if (ok) navigate('../done');
  };

  return (
    <Screen
      header={<TopBar step={5} onBack={() => navigate('../climate')} />}
      action={
        <Button onClick={submit} disabled={saving}>
          {saving ? '제출하는 중…' : '내 설정 제출하기'}
        </Button>
      }
      caption={captionFor(name)}
    >
      <ProgressBar current={4} />
      <PageHeading title={`${name}만의 케어를 골라요`} sub="필요한 만큼만, 편안한 방식으로." />

      <Card>
        <CardTitle>시각 케어를 사용할까요?</CardTitle>
        <div className="card-gap">
          <Segmented
            options={[
              { value: 'yes', label: '네, 사용할래요' },
              { value: 'no', label: '아니요' },
            ]}
            value={useVisual}
            onChange={setUseVisual}
            showCheck
          />
        </div>
        <p className="helper helper--below">화면 가장자리의 작은 안내가 이동 중 시각적 편안함을 도와요.</p>
      </Card>

      {useVisual === 'yes' && (
        <>
          <p className="section-title">선호하는 안내 방식</p>
          <div className="stack">
            {CUE_OPTIONS.map((o) => (
              <OptionRow
                key={o.value}
                compact
                mark="check"
                icon={o.icon}
                label={VISUAL_MODE_LABEL[o.value]}
                selected={mode === o.value}
                onSelect={() => setMode(o.value)}
              />
            ))}
          </div>
        </>
      )}

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
      <p className="helper">불편하면 시각 케어를 끄거나 화면을 잠시 멈춰주세요. 실제 적용은 운전자 확인 후 진행돼요.</p>
    </Screen>
  );
}
