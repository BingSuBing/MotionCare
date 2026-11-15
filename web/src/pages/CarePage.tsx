import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlignCenter, Minus, Orbit } from 'lucide-react';
import { Button, Card, CardTitle, OptionRow, PageHeading, Screen, Segmented, TopBar } from '../components/ui';
import MotionCueCanvas from '../components/motion/MotionCueCanvas';
import { MOTION_STATUS_LABEL, useMotionCue } from '../components/motion/context';
import { useSaving } from '../hooks/useSaving';
import { demoMotion } from '../lib/motion';
import { updateMyPassenger } from '../lib/passengers';
import { VISUAL_INTENSITIES, VISUAL_MODE_DESC, VISUAL_MODE_LABEL, type VisualMode } from '../types';
import { useSessionCtx } from '../session/context';

type CueMode = Exclude<VisualMode, 'off'>;

const CUE_OPTIONS: { value: CueMode; icon: ReactNode }[] = [
  { value: 'dot', icon: <Orbit size={22} /> },
  { value: 'horizon', icon: <AlignCenter size={22} /> },
  { value: 'minimal', icon: <Minus size={22} /> },
];

/** 저장된 강도를 가장 가까운 단계로 */
const nearestIntensity = (v: number) =>
  VISUAL_INTENSITIES.reduce((a, b) => (Math.abs(b.value - v) < Math.abs(a.value - v) ? b : a)).value;

/**
 * 나만의 케어 (시각 케어). 메인 화면에서 들어와요.
 * 고르는 동안 미리보기 상자에서 바로 움직임을 보여주고,
 * "이대로 계속하기" → 저장 후 메인에서 화면 전체에 안내가 떠요 (MotionCueProvider).
 */
export default function CarePage() {
  const { sid, me } = useSessionCtx();
  const cue = useMotionCue();
  const navigate = useNavigate();
  const saved = me?.visualMode ?? 'off';
  // 메인에서 "시작하기"로 들어오므로 기본은 '사용'
  const [useVisual, setUseVisual] = useState<'yes' | 'no'>('yes');
  const [mode, setMode] = useState<CueMode>(saved === 'off' ? 'dot' : saved);
  const [intensity, setIntensity] = useState(() => nearestIntensity(me?.visualIntensity ?? 60));
  const { saving, error, run } = useSaving();

  // 이 화면에 있는 동안은 미리보기만 보여요
  const { setPreview } = cue;
  useEffect(() => {
    setPreview(true);
    return () => setPreview(false);
  }, [setPreview]);

  // 센서가 아직 없으면(권한 전, PC) 미리보기는 데모 움직임으로
  const sensorRead = cue.read;
  const live = cue.status === 'sensor';
  const previewRead = useCallback(
    () => (live ? sensorRead() : demoMotion(performance.now() / 1000)),
    [live, sensorRead],
  );

  const save = async () => {
    if (!me) return;
    const on = useVisual === 'yes';
    // iOS는 버튼을 누른 그 순간에 권한을 물어야 해서 저장보다 먼저 불러요
    const permission = on ? cue.requestPermission() : null;
    const ok = await run(() =>
      updateMyPassenger(sid, me.seat, { visualMode: on ? mode : 'off', visualIntensity: intensity }),
    );
    await permission;
    if (ok) {
      cue.setPaused(false);
      navigate('../home');
    }
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
        <p className="helper helper--below">
          화면 가장자리의 작은 안내가 차의 움직임을 눈으로 보여줘서, 보는 것과 몸이 느끼는 것의 차이를 줄여줘요.
        </p>
      </Card>

      {useVisual === 'yes' && (
        <>
          <div className="cue-preview" aria-label="미리보기">
            <div className="cue-preview__content">
              <span />
              <span />
              <span />
              <span />
            </div>
            <MotionCueCanvas mode={mode} intensity={intensity} read={previewRead} contained />
            <span className="cue-preview__badge">{live ? '내 폰 움직임' : '미리보기'}</span>
          </div>

          <p className="section-title">선호하는 안내 방식</p>
          <div className="stack">
            {CUE_OPTIONS.map((o) => (
              <OptionRow
                key={o.value}
                compact
                mark="check"
                icon={o.icon}
                label={VISUAL_MODE_LABEL[o.value]}
                desc={VISUAL_MODE_DESC[o.value]}
                selected={mode === o.value}
                onSelect={() => setMode(o.value)}
              />
            ))}
          </div>

          <p className="section-title">안내 강도</p>
          <Segmented
            options={VISUAL_INTENSITIES.map((i) => ({ value: i.value, label: i.label }))}
            value={intensity}
            onChange={setIntensity}
            tone="white"
          />
          {cue.status === 'needs-permission' && (
            <p className="helper">저장할 때 "동작 및 방향" 접근을 물어봐요. 허용해야 실제 움직임을 따라가요.</p>
          )}
          {cue.status !== 'idle' && cue.status !== 'needs-permission' && (
            <p className="helper">{MOTION_STATUS_LABEL[cue.status]}</p>
          )}
        </>
      )}

      {error && <div className="info-box info-box--error">{error}</div>}
      <p className="helper">불편하면 시각 케어를 끄거나 메인에서 잠시 멈춰주세요.</p>
    </Screen>
  );
}
