import { TEMP_MAX, TEMP_MIN, cabinTemp, type HvacState } from '../types';

const DEFAULT_TEMP = 24;

/** 희망 온도 시작값: 저장된 값 → 지금 실내 온도(정수) → 24℃ */
export function initialTemp(saved: number | null | undefined, hvac: HvacState | undefined) {
  const base = saved ?? (hvac ? Math.round(cabinTemp(hvac)) : DEFAULT_TEMP);
  return Math.min(TEMP_MAX, Math.max(TEMP_MIN, base));
}
