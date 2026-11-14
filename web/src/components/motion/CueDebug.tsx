import { useEffect, useState } from 'react';
import type { CarMotion } from '../../lib/motion';

/** 주소 끝에 ?debug 를 붙이면 화면 위쪽에 상태가 보여요 (폰 테스트용) */
export default function CueDebug({ info, read }: { info: string; read: () => CarMotion }) {
  const [m, setM] = useState<CarMotion>({ lateral: 0, longitudinal: 0 });
  useEffect(() => {
    const t = window.setInterval(() => setM(read()), 300);
    return () => window.clearInterval(t);
  }, [read]);
  return (
    <div className="cue-debug">
      {info} · 좌우 {m.lateral.toFixed(2)} · 앞뒤 {m.longitudinal.toFixed(2)}
    </div>
  );
}
