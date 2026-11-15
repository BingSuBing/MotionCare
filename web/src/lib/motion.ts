/**
 * 시각 케어용 차량 움직임 계산 (docs/visual-care.md)
 *
 * 폰 센서(DeviceMotion)는 "폰 기준" 가속도를 줘요. 폰을 세워 들든 눕혀 들든
 * 같은 결과가 나오도록, 중력 방향으로 수평면을 구한 뒤 차량 기준 두 값으로 바꿔요.
 *   - lateral: 좌우 가속도 (+ 오른쪽, m/s²)  → 좌회전하면 음수
 *   - longitudinal: 앞뒤 가속도 (+ 앞, m/s²) → 출발하면 양수, 브레이크면 음수
 */

export type Vec3 = [number, number, number];

export interface CarMotion {
  lateral: number;
  longitudinal: number;
}

export const ZERO_MOTION: CarMotion = { lateral: 0, longitudinal: 0 };

export const MOTION_RULE = {
  /** 중력 추정 저역 필터 시간 상수 (초) */
  GRAVITY_TAU: 0.8,
  /** 가속도 평활 시간 상수 (초). 손떨림·노면 진동을 걸러요 */
  ACCEL_TAU: 0.25,
  /** 이보다 작은 가속도는 0으로 (m/s²) */
  DEAD_ZONE: 0.12,
  /** 표시용 최대 가속도 (m/s²). 일반 주행은 대부분 ±3 안이에요 */
  MAX_ACCEL: 4,
} as const;

const dot = (a: Vec3, b: Vec3) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const scale = (a: Vec3, k: number): Vec3 => [a[0] * k, a[1] * k, a[2] * k];
const sub = (a: Vec3, b: Vec3): Vec3 => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const norm = (a: Vec3) => Math.hypot(a[0], a[1], a[2]);
const unit = (a: Vec3): Vec3 | null => {
  const n = norm(a);
  return n < 1e-6 ? null : scale(a, 1 / n);
};
/** 수직 성분을 빼서 수평면에 눕혀요 */
const horizontal = (a: Vec3, up: Vec3): Vec3 => sub(a, scale(up, dot(a, up)));

/**
 * 폰 좌표 → 화면 좌표 (가로 모드 대응).
 * angle은 screen.orientation.angle (0, 90, 180, 270).
 */
export function toScreenFrame([x, y, z]: Vec3, angle: number): Vec3 {
  const r = (angle * Math.PI) / 180;
  const c = Math.round(Math.cos(r));
  const s = Math.round(Math.sin(r));
  // 90° = 폰을 왼쪽으로 눕힘: 화면 오른쪽 = 폰 아래쪽(-y), 화면 위쪽 = 폰 오른쪽(+x)
  return [x * c - y * s, x * s + y * c, z];
}

/**
 * 화면 좌표의 순수 가속도를 차량 기준 좌우·앞뒤로 바꿔요.
 * @param accel 중력을 뺀 가속도 (화면 좌표)
 * @param gravityUp 위쪽 방향 (중력 반대, 화면 좌표, 크기 무관)
 *
 * 앞쪽 = (화면 위쪽 - 화면 바깥쪽)을 수평으로 눕힌 방향.
 *   - 폰을 눕혀 들면 화면 위쪽이 차 앞쪽
 *   - 폰을 세워 들면(화면이 나를 봄) 화면 뒤쪽이 차 앞쪽
 *   - 그 사이 각도도 자연스럽게 섞여요
 */
export function toCarFrame(accel: Vec3, gravityUp: Vec3): CarMotion {
  const up = unit(gravityUp);
  if (!up) return ZERO_MOTION;
  const forward = unit(horizontal([0, 1, -1], up));
  const right = unit(horizontal([1, 0, 0], up));
  if (!forward || !right) return ZERO_MOTION;
  return { lateral: dot(accel, right), longitudinal: dot(accel, forward) };
}

const deadZone = (v: number) => {
  const a = Math.abs(v);
  if (a < MOTION_RULE.DEAD_ZONE) return 0;
  const clipped = Math.min(a, MOTION_RULE.MAX_ACCEL) - MOTION_RULE.DEAD_ZONE;
  return Math.sign(v) * clipped;
};

const alpha = (dtSec: number, tau: number) => 1 - Math.exp(-Math.max(dtSec, 0) / tau);

/**
 * 센서 값을 받아 매끄러운 차량 가속도로 바꾸는 필터.
 * 센서 이벤트마다 push()를 부르고, 그리는 쪽은 value()를 읽어요.
 */
export function createMotionFilter() {
  let gravity: Vec3 | null = null;
  let smooth: CarMotion = ZERO_MOTION;
  let lastMs: number | null = null;

  return {
    /**
     * @param withGravity accelerationIncludingGravity (화면 좌표)
     * @param linear acceleration (중력 제외, 없으면 null → 중력 추정으로 계산)
     */
    push(withGravity: Vec3, linear: Vec3 | null, nowMs: number) {
      const dt = lastMs == null ? 0.016 : (nowMs - lastMs) / 1000;
      lastMs = nowMs;
      const g = alpha(dt, MOTION_RULE.GRAVITY_TAU);
      gravity = gravity
        ? [
            gravity[0] + (withGravity[0] - gravity[0]) * g,
            gravity[1] + (withGravity[1] - gravity[1]) * g,
            gravity[2] + (withGravity[2] - gravity[2]) * g,
          ]
        : withGravity;

      // 기기마다 부호가 반대일 수 있어요(일부 iOS). 사람은 화면이 위·나를 보게 드니까
      // "위쪽"이 화면 위·바깥쪽을 향하지 않으면 부호를 뒤집어요.
      const flip = dot(gravity, [0, 1, 1]) < 0 ? -1 : 1;
      const pure = scale(linear ?? sub(withGravity, gravity), flip);
      const car = toCarFrame(pure, scale(gravity, flip));
      const a = alpha(dt, MOTION_RULE.ACCEL_TAU);
      smooth = {
        lateral: smooth.lateral + (deadZone(car.lateral) - smooth.lateral) * a,
        longitudinal: smooth.longitudinal + (deadZone(car.longitudinal) - smooth.longitudinal) * a,
      };
    },
    value: () => smooth,
    reset() {
      gravity = null;
      smooth = ZERO_MOTION;
      lastMs = null;
    },
  };
}

/** 센서가 없을 때(PC, 권한 거부, 발표 시연) 쓰는 가짜 주행 움직임 */
export function demoMotion(tSec: number): CarMotion {
  return {
    lateral: 1.8 * Math.sin(tSec * 0.42) + 0.5 * Math.sin(tSec * 1.31 + 0.7),
    longitudinal: 1.1 * Math.sin(tSec * 0.27 + 1.2) + 0.3 * Math.sin(tSec * 0.93),
  };
}
