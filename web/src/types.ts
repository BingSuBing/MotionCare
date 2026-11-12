// docs/firestore-schema.md 와 1:1로 맞춘 타입이에요.
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

// ⚠️ A 확인: Vehicle SDK 실제 범위에 맞춰 수정
export const TEMP_MIN = 18;
export const TEMP_MAX = 26;
export const TEMP_STEP = 0.5;
export const FAN_MIN = 0;
export const FAN_MAX = 8;

/* ---------- sessions/{sessionId} ---------- */

export interface HvacState {
  temp: number;
  fan: number;
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
  sensitivity: Sensitivity;
  sicknessLevel: SicknessLevel;
  visualMode: VisualMode;
  updatedAt?: Timestamp;
}

/* ---------- sessions/{sessionId}/requests/{requestId} ---------- */

export type RequestType = 'SET_TEMP' | 'SET_FAN';
export type RequestStatus = 'pending' | 'done' | 'failed';
export type FailReason = 'out_of_range' | 'sdk_error' | 'superseded' | 'unsupported';

export interface CareRequest {
  seat: Seat;
  uid: string;
  type: RequestType;
  value: number; // 절댓값 (예: 21.5)
  status: RequestStatus;
  reason?: FailReason;
  createdAt: Timestamp;
  handledAt?: Timestamp;
}

/* ---------- 경로 헬퍼 ---------- */

export const paths = {
  session: (sid: string) => `sessions/${sid}`,
  passenger: (sid: string, seat: Seat) => `sessions/${sid}/passengers/${seat}`,
  requests: (sid: string) => `sessions/${sid}/requests`,
} as const;

export const DEV_SESSION_ID = 'test01';
