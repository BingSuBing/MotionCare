import { useEffect, useRef } from 'react';
import type { CarMotion } from '../../lib/motion';
import type { VisualMode } from '../../types';

type CueMode = Exclude<VisualMode, 'off'>;

/**
 * 시각 케어 그리기 규칙 (docs/visual-care.md "그리기 규칙")
 * 몸이 쏠리는 방향으로 안내가 움직여서, 눈으로 보는 것과 몸이 느끼는 것을 맞춰줘요.
 *   - 좌회전(왼쪽 가속) → 몸은 오른쪽으로 쏠림 → 점이 오른쪽으로
 *   - 출발(앞 가속)     → 몸은 뒤로 쏠림     → 점이 아래로
 */
const DRAW = {
  /** 가속도 1 m/s²당 이동 거리 (px) */
  PX_PER_ACCEL: 9,
  /** 최대 이동 거리 (px) */
  MAX_SHIFT: 20,
  /** 화면 움직임 평활 (초) */
  FOLLOW_TAU: 0.12,
  /** 수평선 기울기: 가속도 1 m/s²당 (rad) */
  ROLL_PER_ACCEL: 0.05,
  MAX_ROLL: 0.3,
  /** 이 가속도에서 안내가 가장 진해져요 (m/s²) */
  FULL_ACCEL: 2.5,
} as const;

const clamp = (v: number, m: number) => Math.max(-m, Math.min(m, v));

/** 강도 0~100 → 크기·진하기 배율 */
const strength = (intensity: number) => {
  const k = Math.max(0, Math.min(100, intensity)) / 100;
  return { size: 0.7 + 0.6 * k, alpha: 0.45 + 0.55 * k };
};

const cssVar = (name: string, fallback: string) =>
  getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;

interface Colors {
  ink: string;
  halo: string;
  glow: string;
}

function drawDots(ctx: CanvasRenderingContext2D, w: number, h: number, dx: number, dy: number, s: ReturnType<typeof strength>, energy: number, c: Colors) {
  const r = 4.5 * s.size;
  // 가장 많이 움직여도 화면 안에 남도록 바깥 여백 = 최대 이동 + 점 크기
  const margin = DRAW.MAX_SHIFT + r + 4;
  const gapX = 20 * s.size;
  const gapY = 56;
  const cols = [margin, margin + gapX, w - margin - gapX, w - margin];
  ctx.globalAlpha = s.alpha * (0.45 + 0.55 * energy);
  for (let ci = 0; ci < cols.length; ci++) {
    // 안쪽 줄은 반 칸 내려서 엇갈리게
    const start = ci === 1 || ci === 2 ? gapY / 2 : 0;
    for (let y = 40 + start; y < h - 20; y += gapY) {
      const x = cols[ci] + dx;
      const yy = y + dy;
      ctx.beginPath();
      ctx.arc(x, yy, r + 1.5, 0, Math.PI * 2);
      ctx.fillStyle = c.halo;
      ctx.fill();
      ctx.beginPath();
      ctx.arc(x, yy, r, 0, Math.PI * 2);
      ctx.fillStyle = c.ink;
      ctx.fill();
    }
  }
}

function drawHorizon(ctx: CanvasRenderingContext2D, w: number, h: number, dy: number, roll: number, s: ReturnType<typeof strength>, energy: number, c: Colors) {
  const cy = h / 2 + dy * 1.5;
  const cx = w / 2;
  const half = w * 0.6;
  ctx.save();
  ctx.translate(cx, cy);
  ctx.rotate(roll);
  ctx.globalAlpha = s.alpha * (0.55 + 0.45 * energy);
  ctx.lineCap = 'round';
  // 가운데(내용)는 비우고 양쪽 가장자리만 그려요
  const segs: [number, number][] = [
    [-half, -w * 0.22],
    [w * 0.22, half],
  ];
  for (const [color, width] of [
    [c.halo, 6 * s.size],
    [c.ink, 3 * s.size],
  ] as const) {
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    for (const [a, b] of segs) {
      ctx.beginPath();
      ctx.moveTo(a, 0);
      ctx.lineTo(b, 0);
      ctx.stroke();
    }
  }
  ctx.restore();
}

function drawMinimal(ctx: CanvasRenderingContext2D, w: number, h: number, m: CarMotion, s: ReturnType<typeof strength>, c: Colors) {
  const band = 18 * s.size;
  const k = (v: number) => Math.min(1, Math.max(0, v) / DRAW.FULL_ACCEL) * s.alpha;
  // 몸이 쏠리는 쪽 가장자리가 빛나요
  const edges: { a: number; x0: number; y0: number; x1: number; y1: number; rect: [number, number, number, number] }[] = [
    { a: k(-m.lateral), x0: w, y0: 0, x1: w - band, y1: 0, rect: [w - band, 0, band, h] }, // 오른쪽
    { a: k(m.lateral), x0: 0, y0: 0, x1: band, y1: 0, rect: [0, 0, band, h] }, // 왼쪽
    { a: k(-m.longitudinal), x0: 0, y0: 0, x1: 0, y1: band, rect: [0, 0, w, band] }, // 위 (브레이크)
    { a: k(m.longitudinal), x0: 0, y0: h, x1: 0, y1: h - band, rect: [0, h - band, w, band] }, // 아래 (출발)
  ];
  for (const e of edges) {
    if (e.a <= 0.01) continue;
    const g = ctx.createLinearGradient(e.x0, e.y0, e.x1, e.y1);
    g.addColorStop(0, c.glow);
    g.addColorStop(1, 'transparent');
    ctx.globalAlpha = e.a;
    ctx.fillStyle = g;
    ctx.fillRect(...e.rect);
  }
}

/**
 * 시각 케어 캔버스. 매 프레임 read()로 차량 움직임을 읽어 그려요.
 * @param contained true면 부모 상자 안(미리보기), false면 화면 전체 위에 겹쳐요
 */
export default function MotionCueCanvas({
  mode,
  intensity,
  read,
  contained = false,
}: {
  mode: CueMode;
  intensity: number;
  read: () => CarMotion;
  contained?: boolean;
}) {
  const ref = useRef<HTMLCanvasElement>(null);
  // 최신 값을 루프 안에서 읽으려고 ref에 둬요
  const props = useRef({ mode, intensity, read });
  useEffect(() => {
    props.current = { mode, intensity, read };
  }, [mode, intensity, read]);

  useEffect(() => {
    const canvas = ref.current;
    const ctx = canvas?.getContext('2d');
    if (!canvas || !ctx) return;

    const colors: Colors = {
      ink: cssVar('--c-deep', '#00491e'),
      halo: cssVar('--c-white', '#ffffff'),
      glow: cssVar('--c-brand', '#87b170'),
    };

    let w = 0;
    let h = 0;
    const resize = () => {
      const dpr = window.devicePixelRatio || 1;
      w = canvas.clientWidth;
      h = canvas.clientHeight;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    };
    resize();
    const ro = new ResizeObserver(resize);
    ro.observe(canvas);

    let dx = 0;
    let dy = 0;
    let roll = 0;
    let last = performance.now();
    let raf = 0;
    const frame = (now: number) => {
      const dt = (now - last) / 1000;
      last = now;
      const { mode: md, intensity: it, read: rd } = props.current;
      const m = rd();
      const s = strength(it);
      const f = 1 - Math.exp(-dt / DRAW.FOLLOW_TAU);
      const shift = DRAW.PX_PER_ACCEL * s.size;
      dx += (clamp(-m.lateral * shift, DRAW.MAX_SHIFT) - dx) * f;
      dy += (clamp(m.longitudinal * shift, DRAW.MAX_SHIFT) - dy) * f;
      roll += (clamp(m.lateral * DRAW.ROLL_PER_ACCEL, DRAW.MAX_ROLL) - roll) * f;
      const energy = Math.min(1, Math.hypot(m.lateral, m.longitudinal) / DRAW.FULL_ACCEL);

      ctx.clearRect(0, 0, w, h);
      if (md === 'dot') drawDots(ctx, w, h, dx, dy, s, energy, colors);
      else if (md === 'horizon') drawHorizon(ctx, w, h, dy, roll, s, energy, colors);
      else drawMinimal(ctx, w, h, m, s, colors);
      ctx.globalAlpha = 1;
      raf = requestAnimationFrame(frame);
    };
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      ro.disconnect();
    };
  }, []);

  return <canvas ref={ref} className={`motion-cue${contained ? ' motion-cue--contained' : ''}`} aria-hidden="true" />;
}
