// docs/firestore-schema.md (v0.3.1) 와 1:1로 맞춘 타입이에요.
// 스키마를 바꿀 때는 문서부터 고치고 이 파일을 따라 고쳐요.
import type { Timestamp } from 'firebase/firestore';

/* ---------- 좌석 ---------- */

export const SEATS = ['front_right', 'rear_left', 'rear_right'] as const;
export type Seat = (typeof SEATS)[number];

export const SEAT_LABEL: Record<Seat, string> = {
  front_right: '조수석',
  rear_left: '뒷좌석 왼쪽',
  rear_right: '뒷좌석 오른쪽',
};

export const isSeat = (v: unknown): v is Seat =>
  typeof v === 'string' && (SEATS as readonly string[]).includes(v);

/* ---------- 온도 ---------- */

export const TEMP_MIN = 18;
export const TEMP_MAX = 26;
export const TEMP_UI_STEP = 1; // 웹 ± 버튼 단위
export const TEMP_ROUND_STEP = 0.5; // 중앙값 반올림 단위
export const NAME_MAX = 10;

export type AirIntake = 'outside' | 'recirc';
export const AIR_INTAKE_LABEL: Record<AirIntake, string> = {
  outside: '외기 유입 중',
  recirc: '내기 순환 중',
};

export type TempStatus = 'applied' | 'pending' | 'driver_set';
export const TEMP_STATUS_LABEL: Record<TempStatus, string> = {
  applied: '적용됨',
  pending: '운전자 적용 대기',
  driver_set: '운전자가 설정함',
};

/* ---------- sessions/{sessionId} ---------- */

export interface HvacState {
  driverTemp: number;
  passengerTemp: number;
  airIntake: AirIntake;
  medianTemp: number | null;
  voteCount: number;
  tempStatus: TempStatus;
  updatedAt?: Timestamp;
}

export interface RestStop {
  name: string;
  distanceKm: number;
  etaMin: number;
}

/** 2단계 */
export interface RouteInfo {
  destinationName: string;
  eta: Timestamp;
  remainingMin: number;
  nextRestStop: RestStop | null;
  updatedAt?: Timestamp;
}

export interface Session {
  /** 세션을 만든 차량 앱의 익명 uid (보안 규칙용, 웹은 읽기만) */
  carUid: string;
  createdAt: Timestamp;
  active: boolean;
  tripTitle: string;
  driverName: string;
  hvacState?: HvacState;
  route?: RouteInfo;
}

/* ---------- sessions/{sessionId}/passengers/{seat} ---------- */

export type Sensitivity = 'none' | 'low' | 'medium' | 'high';
export const SENSITIVITY_OPTIONS: { value: Sensitivity; label: string; desc: string }[] = [
  { value: 'none', label: '없음', desc: '차량 이동이 편안해요' },
  { value: 'low', label: '낮음', desc: '가끔 약하게 느껴요' },
  { value: 'medium', label: '보통', desc: '긴 이동이 조금 불편해요' },
  { value: 'high', label: '높음', desc: '화면을 보면 쉽게 멀미가 나요' },
];

export type SicknessTrigger = 'video' | 'screen';
export const TRIGGER_OPTIONS: { value: SicknessTrigger; label: string }[] = [
  { value: 'video', label: '영상 시청 시 멀미가 잘 나요' },
  { value: 'screen', label: '차량 이동 중 화면 보기 불편해요' },
];

export type VisualMode = 'off' | 'dot' | 'horizon' | 'minimal';
export const VISUAL_MODE_LABEL: Record<VisualMode, string> = {
  off: '사용 안 함',
  dot: '움직이는 점',
  horizon: '수평선',
  minimal: '최소 시각 신호',
};

export const REST_INTERVALS = [30, 60, 90] as const;

export interface Passenger {
  uid: string;
  name: string;
  sensitivity: Sensitivity | null;
  sicknessTriggers: SicknessTrigger[];
  desiredTemp: number | null;
  visualMode: VisualMode;
  visualIntensity: number; // 0~100
  restIntervalMin: number | null; // null = 휴식 알림 꺼짐
  joinedAt?: Timestamp;
  submittedAt: Timestamp | null;
  updatedAt?: Timestamp;
}

/** 좌석 등록 시 기본값 (uid, name, 시간 필드 제외) */
export const PASSENGER_DEFAULTS = {
  sensitivity: null,
  sicknessTriggers: [],
  desiredTemp: null,
  visualMode: 'dot',
  visualIntensity: 30,
  restIntervalMin: 60,
  submittedAt: null,
} satisfies Partial<Passenger>;

/* ---------- sessions/{sessionId}/requests/{requestId} ---------- */

export type RequestType = 'REPORT_DISCOMFORT';
export type RequestStatus = 'pending' | 'done' | 'failed';

export type Discomfort = 'nausea' | 'cold' | 'hot' | 'stuffy' | 'rest';
export const DISCOMFORT_OPTIONS: { value: Discomfort; label: string }[] = [
  { value: 'nausea', label: '메스꺼워요' },
  { value: 'cold', label: '너무 추워요' },
  { value: 'hot', label: '너무 더워요' },
  { value: 'stuffy', label: '답답해요' },
  { value: 'rest', label: '잠깐 쉬고 싶어요' },
];

export interface CareRequest {
  seat: Seat;
  uid: string;
  type: RequestType;
  value: Discomfort;
  status: RequestStatus;
  createdAt: Timestamp;
  handledAt?: Timestamp;
}

/* ---------- 헬퍼 ---------- */

/** 0.5 단위 반올림 + 허용 범위로 제한 (차량 앱 Kotlin 과 같은 규칙) */
export const clampTemp = (t: number): number =>
  Math.min(TEMP_MAX, Math.max(TEMP_MIN, Math.round(t / TEMP_ROUND_STEP) * TEMP_ROUND_STEP));

/** 희망 온도 중앙값. 희망 온도가 없으면 null (차량 앱 Kotlin 과 같은 규칙) */
export const medianTemp = (temps: (number | null | undefined)[]): number | null => {
  const v = temps.filter((t): t is number => typeof t === 'number').sort((a, b) => a - b);
  if (v.length === 0) return null;
  const mid = Math.floor(v.length / 2);
  const m = v.length % 2 ? v[mid] : (v[mid - 1] + v[mid]) / 2;
  return clampTemp(m);
};

/** M07 "현재 실내 온도": 두 구역이 같으면 그 값, 다르면 평균(0.5 반올림) */
export const cabinTemp = (hvac: HvacState): number =>
  hvac.driverTemp === hvac.passengerTemp
    ? hvac.passengerTemp
    : Math.round(((hvac.driverTemp + hvac.passengerTemp) / 2) / TEMP_ROUND_STEP) * TEMP_ROUND_STEP;

/** 22 → "22", 21.5 → "21.5" */
export const fmtTemp = (t: number): string => (Number.isInteger(t) ? String(t) : t.toFixed(1));

export const paths = {
  session: (sid: string) => `sessions/${sid}`,
  passengers: (sid: string) => `sessions/${sid}/passengers`,
  passenger: (sid: string, seat: Seat) => `sessions/${sid}/passengers/${seat}`,
  requests: (sid: string) => `sessions/${sid}/requests`,
} as const;

export const DEV_SESSION_ID = 'test01';
