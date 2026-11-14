import { useCallback, useEffect, useRef, useState } from 'react';
import { createMotionFilter, demoMotion, toScreenFrame, ZERO_MOTION, type CarMotion, type Vec3 } from '../lib/motion';

/**
 * - idle: 꺼짐
 * - needs-permission: iOS라 버튼을 눌러 허용해야 해요
 * - sensor: 폰 센서로 동작 중
 * - demo: 센서가 없어서(PC, 권한 거부) 또는 시연용으로 가짜 움직임 사용
 */
export type MotionStatus = 'idle' | 'needs-permission' | 'sensor' | 'demo';

/** 센서 이벤트를 이 시간 안에 못 받으면 데모로 바꿔요 */
const SENSOR_WAIT_MS = 1500;

type PermissionFn = () => Promise<'granted' | 'denied'>;
const permissionFn = (): PermissionFn | null => {
  if (typeof DeviceMotionEvent === 'undefined') return null;
  const fn = (DeviceMotionEvent as unknown as { requestPermission?: PermissionFn }).requestPermission;
  return typeof fn === 'function' ? fn.bind(DeviceMotionEvent) : null;
};
const hasMotionApi = () => typeof DeviceMotionEvent !== 'undefined';

const readVec = (v: DeviceMotionEventAcceleration | null): Vec3 | null =>
  v && v.x != null && v.y != null && v.z != null ? [v.x, v.y, v.z] : null;

const screenAngle = () => {
  if (typeof screen !== 'undefined' && screen.orientation) return screen.orientation.angle;
  const legacy = (window as unknown as { orientation?: number }).orientation;
  return typeof legacy === 'number' ? (legacy + 360) % 360 : 0;
};

/** 센서 응답: 기다리는 중 / 받음 / 끝내 안 옴 */
type SensorSeen = 'waiting' | 'yes' | 'no';

/**
 * 차량 움직임 구독.
 * 매 프레임 값이 바뀌므로 state가 아니라 read()로 꺼내 써요 (다시 그리기 없음).
 * @param enabled 시각 케어가 켜져 있을 때만 센서를 써요
 * @param forceDemo 시연용: 센서가 있어도 가짜 움직임 사용
 */
export function useVehicleMotion(enabled: boolean, forceDemo = false) {
  const filter = useRef(createMotionFilter());
  const [permission, setPermission] = useState<'granted' | 'denied' | 'ask'>(() =>
    permissionFn() ? 'ask' : 'granted',
  );
  const [seen, setSeen] = useState<SensorSeen>('waiting');

  const listening = enabled && !forceDemo && hasMotionApi() && permission === 'granted';

  // 권한이 필요한 브라우저라도 버튼 없이 허용되는 곳(Chrome 등)이 있어서 먼저 조용히 물어봐요.
  // iOS는 버튼을 눌러야 해서 여기선 실패하고, 그때만 "시각 케어 켜기" 버튼을 보여줘요.
  const autoAsked = useRef(false);
  useEffect(() => {
    if (!enabled || forceDemo || permission !== 'ask' || autoAsked.current) return;
    const fn = permissionFn();
    if (!fn) return;
    autoAsked.current = true;
    fn()
      .then((r) => setPermission(r === 'granted' ? 'granted' : 'denied'))
      .catch(() => undefined);
  }, [enabled, forceDemo, permission]);

  useEffect(() => {
    if (!listening) return;
    const f = filter.current;
    f.reset();
    const onMotion = (e: DeviceMotionEvent) => {
      const withGravity = readVec(e.accelerationIncludingGravity);
      if (!withGravity) return;
      const angle = screenAngle();
      const linear = readVec(e.acceleration);
      f.push(toScreenFrame(withGravity, angle), linear ? toScreenFrame(linear, angle) : null, e.timeStamp);
      setSeen('yes');
    };
    window.addEventListener('devicemotion', onMotion);
    const timer = window.setTimeout(() => setSeen((s) => (s === 'yes' ? s : 'no')), SENSOR_WAIT_MS);
    return () => {
      window.removeEventListener('devicemotion', onMotion);
      window.clearTimeout(timer);
      setSeen('waiting');
    };
  }, [listening]);

  let status: MotionStatus;
  if (!enabled) status = 'idle';
  else if (forceDemo || !hasMotionApi() || permission === 'denied') status = 'demo';
  else if (permission === 'ask') status = 'needs-permission';
  else status = seen === 'no' ? 'demo' : 'sensor';

  /** iOS: 사용자가 버튼을 누른 그 순간에 불러야 해요 */
  const requestPermission = useCallback(async () => {
    const fn = permissionFn();
    if (!fn) return true;
    try {
      const ok = (await fn()) === 'granted';
      setPermission(ok ? 'granted' : 'denied');
      return ok;
    } catch {
      setPermission('denied');
      return false;
    }
  }, []);

  const read = useCallback((): CarMotion => {
    if (status === 'demo') return demoMotion(performance.now() / 1000);
    if (status === 'sensor') return filter.current.value();
    return ZERO_MOTION;
  }, [status]);

  return { status, read, requestPermission };
}
