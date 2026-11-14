import { useMemo, useState, type ReactNode } from 'react';
import { Activity } from 'lucide-react';
import { useVehicleMotion } from '../../hooks/useVehicleMotion';
import { useSessionCtx } from '../../session/context';
import CueDebug from './CueDebug';
import MotionCueCanvas from './MotionCueCanvas';
import { MotionCueContext, type MotionCueCtx } from './context';

const DEMO_KEY = 'motioncare.cueDemo';
const DEBUG = (() => {
  try {
    return new URLSearchParams(window.location.search).has('debug') || sessionStorage.getItem('motioncare.cueDebug') === '1';
  } catch {
    return false;
  }
})();
try {
  if (DEBUG) sessionStorage.setItem('motioncare.cueDebug', '1');
} catch {
  /* 무시 */
}

const loadDemo = () => {
  try {
    return new URLSearchParams(window.location.search).has('demo') || sessionStorage.getItem(DEMO_KEY) === '1';
  } catch {
    return false;
  }
};

/**
 * 시각 케어 (웹, iOS·Android 공통).
 * 내 visualMode가 켜져 있으면 세션 화면 전체 위에 점·수평선·가장자리 안내를 겹쳐 그려요.
 * 화면을 터치하는 데는 영향이 없어요 (pointer-events: none).
 */
export default function MotionCueProvider({ children }: { children: ReactNode }) {
  const { me } = useSessionCtx();
  const [preview, setPreview] = useState(false);
  const [paused, setPaused] = useState(false);
  const [demo, setDemoState] = useState(loadDemo);

  const mode = me?.visualMode ?? 'off';
  const on = mode !== 'off';
  const { status, read, requestPermission } = useVehicleMotion(on || preview, demo);

  const value = useMemo<MotionCueCtx>(
    () => ({
      status,
      read,
      requestPermission,
      setPreview,
      paused,
      setPaused,
      demo,
      setDemo: (v) => {
        setDemoState(v);
        try {
          sessionStorage.setItem(DEMO_KEY, v ? '1' : '0');
        } catch {
          /* 저장 못 해도 동작에는 문제없어요 */
        }
      },
    }),
    [status, read, requestPermission, paused, demo],
  );

  // 센서 준비 전(권한 대기 등)에도 점은 먼저 보여줘요. 움직임은 준비되면 따라가요.
  const showCue = on && !preview && !paused;

  return (
    <MotionCueContext.Provider value={value}>
      {children}
      {DEBUG && (
        <CueDebug
          info={`${mode} · ${status}${preview ? ' · 미리보기' : ''}${paused ? ' · 멈춤' : ''} · ${typeof DeviceMotionEvent}`}
          read={read}
        />
      )}
      {showCue && me && <MotionCueCanvas mode={mode} intensity={me.visualIntensity} read={read} />}
      {on && !preview && !paused && status === 'needs-permission' && (
        <button type="button" className="cue-permission" onClick={() => void requestPermission()}>
          <Activity size={18} />
          눌러서 시각 케어 켜기
        </button>
      )}
    </MotionCueContext.Provider>
  );
}
