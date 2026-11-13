// docs/firestore-schema.md (v0.3) 와 1:1로 맞춘 타입이에요.
// 스키마를 바꿀 때는 문서부터 고치고 이 파일을 따라 고쳐요.
import type { Timestamp } from 'firebase/firestore';

/* ---------- 공통 ---------- */

export const SEATS = ['front_right', 'rear_left', 'rear_right'] as const;
export type Seat = (typeof SEATS)[number];

export const SEAT_LABEL: Record<Seat, string> = {
  front_right: '조수석',
  rear_left: '뒷좌석 왼쪽',
  rear_right: '뒷좌석 오른쪽',
};

export const isSeat = (v: unknown): v is Seat =>
  typeof v === 'string' && (SEATS as readonly string[]).includes(v);

// 희망 온도 허용 범위 (SDK 범위는 16~30℃지만 안전을 위해 좁게 둬요)
export const TEMP_MIN = 18;
export const TEMP_MAX = 26;
export const TEMP_STEP = 0.5;

export type AirIntake = 'outside' | 'recirc';
export const AIR_INTAKE_LABEL: Record<AirIntake, string> = {
  outside: '외기 (환기)',
  recirc: '내기 순환',
};

/* ---------- sessions/{sessionId} ---------- */

export interface HvacState {
  driverTemp: number;
  passengerTemp: number;
  airIntake: AirIntake;
  medianTemp: number | null;
  voteCount: number;
  updatedAt?: Timestamp;
}

export interface RestStop {
  name: string;
  etaMin: number;
}

/** 2단계 */
export interface RouteInfo {
  destinationName: string;
  eta: Timestamp;
  remainingMin: number;
  restIntervalMin: number;
  nextRestStop: RestStop | null;
  updatedAt?: Timestamp;
}

export interface Session {
  createdAt: Timestamp;
  active: boolean;
  hvacState?: HvacState;
  route?: RouteInfo;
}

/* ---------- sessions/{sessionId}/passengers/{seat} ---------- */

export type Sensitivity = 'low' | 'medium' | 'high';
export type SicknessLevel = 'ok' | 'mild' | 'severe';
export type VisualMode = 'off' | 'dot' | 'horizon';

export interface Passenger {
  uid: string;
  name: string;
  desiredTemp: number | null; // 희망 온도, null이면 투표 안 함
  sensitivity: Sensitivity;
  sicknessLevel: SicknessLevel;
  visualMode: VisualMode;
  updatedAt?: Timestamp;
}

/* ---------- sessions/{sessionId}/requests/{requestId} ---------- */

export type RequestType = 'SET_AIR_INTAKE';
export type RequestStatus = 'pending' | 'done' | 'failed';
export type FailReason = 'sdk_error' | 'superseded' | 'unsupported' | 'declined';

export interface CareRequest {
  seat: Seat;
  uid: string;
  type: RequestType;
  value: AirIntake;
  status: RequestStatus;
  reason?: FailReason;
  createdAt: Timestamp;
  handledAt?: Timestamp;
}

/* ---------- 헬퍼 ---------- */

/** 0.5 단위 반올림 + 허용 범위로 제한 */
export const clampTemp = (t: number): number =>
  Math.min(TEMP_MAX, Math.max(TEMP_MIN, Math.round(t / TEMP_STEP) * TEMP_STEP));

/** 차량 앱과 같은 규칙의 중앙값 (웹에서 "적용 예정 온도" 미리보기용) */
export const medianTemp = (temps: (number | null | undefined)[]): number | null => {
  const v = temps.filter((t): t is number => typeof t === 'number').sort((a, b) => a - b);
  if (v.length === 0) return null;
  const mid = Math.floor(v.length / 2);
  const m = v.length % 2 ? v[mid] : (v[mid - 1] + v[mid]) / 2;
  return clampTemp(m);
};

/** 좌석에서 체감하는 설정 온도. 뒷좌석은 앞 두 구역이 다르면 null */
export const tempForSeat = (hvac: HvacState, seat: Seat): number | null => {
  if (seat === 'front_right') return hvac.passengerTemp;
  return hvac.driverTemp === hvac.passengerTemp ? hvac.passengerTemp : null;
};

export const paths = {
  session: (sid: string) => `sessions/${sid}`,
  passengers: (sid: string) => `sessions/${sid}/passengers`,
  passenger: (sid: string, seat: Seat) => `sessions/${sid}/passengers/${seat}`,
  requests: (sid: string) => `sessions/${sid}/requests`,
} as const;

export const DEV_SESSION_ID = 'test01';
