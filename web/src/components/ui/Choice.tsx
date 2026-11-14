import type { ReactNode } from 'react';
import { Check, CircleCheck } from 'lucide-react';

/** 선택 행 (M03 민감도, M05 안내 방식, M08 불편 종류) */
export function OptionRow({
  label,
  desc,
  icon,
  selected,
  onSelect,
  mark = 'radio',
  tone = 'default',
  compact = false,
}: {
  label: string;
  desc?: string;
  icon?: ReactNode;
  selected: boolean;
  onSelect: () => void;
  /** radio: 오른쪽 동그라미 / check: 선택 시 체크만 / none: 표시 없음 */
  mark?: 'radio' | 'check' | 'none';
  tone?: 'default' | 'discomfort';
  compact?: boolean;
}) {
  const cls = [
    'option',
    selected && 'option--selected',
    tone === 'discomfort' && 'option--discomfort',
    compact && 'option--compact',
  ]
    .filter(Boolean)
    .join(' ');

  return (
    <button type="button" className={cls} onClick={onSelect} aria-pressed={selected}>
      {icon && <span className="option__icon">{icon}</span>}
      <span className="option__text">
        <span className="option__label">{label}</span>
        {desc && (
          <span className="option__desc">{desc}</span>
        )}
      </span>
      {mark === 'radio' && (
        <span className="option__mark">
          {selected ? <CircleCheck size={24} /> : <span className="option__radio" />}
        </span>
      )}
      {mark === 'check' && selected && (
        <span className="option__mark">
          <Check size={20} />
        </span>
      )}
    </button>
  );
}

/** 칩 (선택됨 / 사용 중 / 선택 가능, 상태 표시용) */
export function Chip({
  selected = false,
  tone = 'default',
  onClick,
  children,
}: {
  selected?: boolean;
  /** white: 흰 바탕 칩 (M02 범례 "선택 가능") */
  tone?: 'default' | 'white';
  onClick?: () => void;
  children: ReactNode;
}) {
  const cls = `chip${selected ? ' chip--selected' : ''}${tone === 'white' ? ' chip--white' : ''}`;
  // onClick이 없으면 누를 수 없는 표시용 칩 (M02 범례)
  if (!onClick) {
    return (
      <span className={cls}>
        {selected && <Check size={16} strokeWidth={3} />}
        {children}
      </span>
    );
  }
  return (
    <button
      type="button"
      className={cls}
      onClick={onClick}
      aria-pressed={selected}
    >
      {selected && <Check size={16} strokeWidth={3} />}
      {children}
    </button>
  );
}

/** 가로로 나란한 선택 버튼 (M05 "네/아니요", "30분/60분/90분") */
export function Segmented<T extends string | number>({
  options,
  value,
  onChange,
  showCheck = false,
  tone = 'default',
}: {
  options: { value: T; label: string }[];
  value: T | null;
  onChange: (v: T) => void;
  showCheck?: boolean;
  /** white: 캔버스 위에 바로 놓일 때 흰 바탕 (M03 풍량) */
  tone?: 'default' | 'white';
}) {
  return (
    <div className={`segments${tone === 'white' ? ' segments--white' : ''}`} role="radiogroup">
      {options.map((o) => {
        const on = o.value === value;
        return (
          <button
            key={String(o.value)}
            type="button"
            role="radio"
            aria-checked={on}
            className={`segment${on ? ' segment--selected' : ''}`}
            onClick={() => onChange(o.value)}
          >
            {on && showCheck && <Check size={16} strokeWidth={3} />}
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

/** 토글 스위치 (M05 정기 휴식 알림) */
export function Toggle({ on, onChange, label }: { on: boolean; onChange: (v: boolean) => void; label: string }) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={on}
      aria-label={label}
      className={`toggle${on ? ' toggle--on' : ''}`}
      onClick={() => onChange(!on)}
    >
      <span className="toggle__thumb" />
    </button>
  );
}
