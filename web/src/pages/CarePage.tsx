import { useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlignCenter, Minus, Orbit } from 'lucide-react';
import { Button, Card, CardTitle, OptionRow, PageHeading, Screen, Segmented, TopBar } from '../components/ui';
import { useSaving } from '../hooks/useSaving';
import { updateMyPassenger } from '../lib/passengers';
import { VISUAL_MODE_LABEL, type VisualMode } from '../types';
import { useSessionCtx } from '../session/context';

type CueMode = Exclude<VisualMode, 'off'>;

const CUE_OPTIONS: { value: CueMode; icon: ReactNode }[] = [
  { value: 'dot', icon: <Orbit size={22} /> },
  { value: 'horizon', icon: <AlignCenter size={22} /> },
  { value: 'minimal', icon: <Minus size={22} /> },
];

/**
 * 나만의 케어 (시각 케어). 처음 설정에서 빠지고, 메인 화면에서 들어와요.
 * "이대로 계속하기" → 메인으로 돌아가고, 화면에 점·수평선 안내가 떠요 (안내 표시는 feature/web-motion-cue).
 */
export default function CarePage() {
  const { sid, me } = useSessionCtx();
  const navigate = useNavigate();
  const saved = me?.visualMode ?? 'off';
  // 메인에서 "시작하기"로 들어오므로 기본은 '사용'
  const [useVisual, setUseVisual] = useState<'yes' | 'no'>('yes');
  const [mode, setMode] = useState<CueMode>(saved === 'off' ? 'dot' : saved);
  const { saving, error, run } = useSaving();

  const save = async () => {
    if (!me) return;
    const ok = await run(() =>
      updateMyPassenger(sid, me.seat, { visualMode: useVisual === 'yes' ? mode : 'off' }),
    );
    if (ok) navigate('../home');
  };

  return (
    <Screen
      header={<TopBar onBack={() => navigate('../home')} />}
      action={
        <Button onClick={save} disabled={saving}>
          {saving ? '저장하는 중…' : '이대로 계속하기'}
        </Button>
      }
    >
      <PageHeading title="나만의 케어를 골라요" sub="필요한 만큼만, 편안한 방식으로." />

      <Card>
        <CardTitle>시각 케어를 사용할까요?</CardTitle>
        <div className="card-gap">
          <Segmented
            options={[
              { value: 'yes', label: '네, 사용할래요' },
              { value: 'no', label: '아니요, 괜찮아요' },
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

      {error && <div className="info-box info-box--error">{error}</div>}
      <p className="helper">불편하면 시각 케어를 끄거나 화면을 잠시 멈춰주세요. 실제 적용은 운전자 확인 후 진행돼요.</p>
    </Screen>
  );
}
