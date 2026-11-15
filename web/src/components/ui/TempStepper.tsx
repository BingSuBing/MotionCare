import { Minus, Plus } from 'lucide-react';
import { TEMP_MAX, TEMP_MIN, TEMP_UI_STEP, fmtTemp } from '../../types';
import { Card } from './Card';

/** M04 희망 온도 카드: (−)  24°C  (+) */
export function TempStepper({
  value,
  onChange,
  compact = false,
}: {
  value: number;
  onChange: (v: number) => void;
  /** compact: 카드 안 제목·범위 문구 없이 숫자와 버튼만 (제목은 카드 밖에) */
  compact?: boolean;
}) {
  const dec = () => onChange(Math.max(TEMP_MIN, value - TEMP_UI_STEP));
  const inc = () => onChange(Math.min(TEMP_MAX, value + TEMP_UI_STEP));

  return (
    <Card className={compact ? 'temp-card--compact' : ''}>
      {!compact && <p className="card-label">희망 온도</p>}
      <div className="temp">
        <button type="button" className="temp__btn" onClick={dec} disabled={value <= TEMP_MIN} aria-label="1도 내리기">
          <Minus size={24} />
        </button>
        <p className="temp__value" aria-live="polite">
          {fmtTemp(value)}°C
        </p>
        <button
          type="button"
          className="temp__btn temp__btn--plus"
          onClick={inc}
          disabled={value >= TEMP_MAX}
          aria-label="1도 올리기"
        >
          <Plus size={24} />
        </button>
      </div>
      {!compact && (
        <p className="temp__caption">
          내 희망 온도 · {TEMP_MIN}–{TEMP_MAX}°C
        </p>
      )}
    </Card>
  );
}
