import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Film, Maximize2, X } from 'lucide-react';
import { Button, Card, PageHeading, Screen, Segmented, TopBar } from '../components/ui';
import { MOTION_STATUS_LABEL, useMotionCue } from '../components/motion/context';
import { VISUAL_MODE_LABEL } from '../types';
import { useSessionCtx } from '../session/context';

/** 발표용 기본 영상 (없으면 내 폰의 영상을 골라요). 예: VITE_DEMO_VIDEO_URL=/videos/demo.mp4 */
const DEMO_VIDEO = import.meta.env.VITE_DEMO_VIDEO_URL as string | undefined;

/**
 * 영상 보며 시각 케어 (우리 앱 안의 영상 플레이어).
 * iOS는 다른 앱 위에 띄울 수 없어서, 이 화면 안에서 영상 위에 안내를 겹쳐 보여줘요.
 * 폰 기본 전체 화면을 쓰면 안내가 가려지므로 "크게 보기"는 앱 안에서 처리해요.
 */
export default function WatchPage() {
  const { me } = useSessionCtx();
  const cue = useMotionCue();
  const navigate = useNavigate();
  const [src, setSrc] = useState<string | undefined>(DEMO_VIDEO);
  const [big, setBig] = useState(false);
  const fileUrl = useRef<string | null>(null);

  useEffect(
    () => () => {
      if (fileUrl.current) URL.revokeObjectURL(fileUrl.current);
    },
    [],
  );

  if (!me) return null;
  const on = me.visualMode !== 'off';

  const pick = (file: File | undefined) => {
    if (!file) return;
    if (fileUrl.current) URL.revokeObjectURL(fileUrl.current);
    fileUrl.current = URL.createObjectURL(file);
    setSrc(fileUrl.current);
  };

  return (
    <Screen
      header={<TopBar onBack={() => navigate('../home')} />}
      action={
        <Button variant="secondary" onClick={() => navigate('../care')}>
          {on ? '안내 방식 바꾸기' : '시각 케어 켜기'}
        </Button>
      }
    >
      <PageHeading title="영상 보며 편안하게" sub="영상 위에 차의 움직임을 보여줘서 멀미를 덜어줘요." />

      <div className={`watch${big ? ' watch--big' : ''}`}>
        {src ? (
          <video className="watch__video" src={src} controls playsInline loop controlsList="nofullscreen" />
        ) : (
          <label className="watch__empty">
            <Film size={32} />
            <span>내 폰의 영상 고르기</span>
            <input type="file" accept="video/*" hidden onChange={(e) => pick(e.target.files?.[0])} />
          </label>
        )}
        {src && (
          <button
            type="button"
            className="watch__toggle"
            onClick={() => setBig((b) => !b)}
            aria-label={big ? '작게 보기' : '크게 보기'}
          >
            {big ? <X size={20} /> : <Maximize2 size={20} />}
          </button>
        )}
      </div>

      <Card>
        <div className="row-between">
          <span className="card-label">시각 케어</span>
          <span className="card-strong card-strong--flush">
            {on ? VISUAL_MODE_LABEL[me.visualMode] : '꺼짐'}
          </span>
        </div>
        {on && (
          <>
            <p className="card-text card-gap">{cue.paused ? '잠시 멈춤' : MOTION_STATUS_LABEL[cue.status]}</p>
            <div className="card-gap">
              <Segmented
                options={[
                  { value: 'on', label: '켜기' },
                  { value: 'pause', label: '잠시 멈추기' },
                ]}
                value={cue.paused ? 'pause' : 'on'}
                onChange={(v) => cue.setPaused(v === 'pause')}
              />
            </div>
          </>
        )}
      </Card>

      {on && (
        <Card>
          <p className="card-label">움직임 출처</p>
          <div className="card-gap">
            <Segmented
              options={[
                { value: 'sensor', label: '폰 센서' },
                { value: 'demo', label: '데모 (시연용)' },
              ]}
              value={cue.demo ? 'demo' : 'sensor'}
              onChange={(v) => cue.setDemo(v === 'demo')}
            />
          </div>
          <p className="helper helper--below">
            차가 서 있을 때 시연하려면 데모를 고르세요. 센서는 HTTPS 주소에서만 동작해요.
          </p>
        </Card>
      )}

      {src && (
        <label className="btn btn--tertiary btn--block">
          다른 영상 고르기
          <input type="file" accept="video/*" hidden onChange={(e) => pick(e.target.files?.[0])} />
        </label>
      )}
    </Screen>
  );
}
