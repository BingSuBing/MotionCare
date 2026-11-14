import { createContext, useContext } from 'react';
import type { CarMotion } from '../../lib/motion';
import type { MotionStatus } from '../../hooks/useVehicleMotion';

export interface MotionCueCtx {
  status: MotionStatus;
  read: () => CarMotion;
  /** iOS 센서 권한 요청. 버튼 클릭 처리 맨 앞에서 불러요 */
  requestPermission: () => Promise<boolean>;
  /** 나만의 케어 화면 미리보기 중이면 화면 전체 안내는 숨겨요 */
  setPreview: (on: boolean) => void;
  /** 잠시 멈춤 (이 폰에서만) */
  paused: boolean;
  setPaused: (on: boolean) => void;
  /** 발표용 데모 움직임 */
  demo: boolean;
  setDemo: (on: boolean) => void;
}

export const MotionCueContext = createContext<MotionCueCtx | null>(null);

export function useMotionCue(): MotionCueCtx {
  const ctx = useContext(MotionCueContext);
  if (!ctx) throw new Error('useMotionCue는 MotionCueProvider 안에서만 쓸 수 있어요');
  return ctx;
}

export const MOTION_STATUS_LABEL: Record<MotionStatus, string> = {
  idle: '꺼짐',
  'needs-permission': '움직임 센서 허용 필요',
  sensor: '폰 센서로 동작 중',
  demo: '데모 움직임으로 동작 중',
};
