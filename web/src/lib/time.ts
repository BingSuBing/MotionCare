import { useEffect, useState } from 'react';

/** 11:18 형식 */
export const fmtTime = (d: Date) =>
  `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;

/** 1분마다 다시 그려야 하는 화면(다음 휴식 시각)용 현재 시각 */
export function useNow(intervalMs = 60_000) {
  const [now, setNow] = useState(() => new Date());
  useEffect(() => {
    const id = window.setInterval(() => setNow(new Date()), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs]);
  return now;
}

/** start 부터 everyMin 분 간격으로, now 이후 첫 시각 */
export function nextTick(start: Date, everyMin: number, now: Date) {
  const step = everyMin * 60_000;
  const passed = Math.max(0, now.getTime() - start.getTime());
  return new Date(start.getTime() + (Math.floor(passed / step) + 1) * step);
}
