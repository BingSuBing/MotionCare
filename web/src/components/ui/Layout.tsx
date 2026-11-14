import type { ReactNode } from 'react';
import { ChevronLeft } from 'lucide-react';

/** 화면 전체 틀: 상단(header) + 본문(children) + 하단 고정 버튼(action) */
export function Screen({
  header,
  action,
  caption,
  children,
}: {
  header?: ReactNode;
  action?: ReactNode;
  caption?: string;
  children: ReactNode;
}) {
  return (
    <div className="screen">
      {header}
      <main className="screen__body">{children}</main>
      {(action || caption) && (
        <div className="screen__action">
          {action}
          {caption && <p className="caption">{caption}</p>}
        </div>
      )}
    </div>
  );
}

/** 상단 바: ‹  편안타  01 / 09 */
export function TopBar({
  title = '편안타',
  step,
  total = 9,
  onBack,
}: {
  title?: string;
  step?: number;
  total?: number;
  onBack?: () => void;
}) {
  return (
    <header className="topbar">
      <div>
        {onBack && (
          <button type="button" className="topbar__back" onClick={onBack} aria-label="뒤로">
            <ChevronLeft size={24} />
          </button>
        )}
      </div>
      <h1 className="topbar__title">{title}</h1>
      <span className="topbar__step">
        {step !== undefined && `${String(step).padStart(2, '0')} / ${String(total).padStart(2, '0')}`}
      </span>
    </header>
  );
}

/** 처음 설정 진행 바 (좌석 → 멀미 정도 → 실내 취향: 3칸) */
export function ProgressBar({ current, total = 3 }: { current: number; total?: number }) {
  return (
    <div className="progress" role="progressbar" aria-valuemin={0} aria-valuemax={total} aria-valuenow={current}>
      {Array.from({ length: total }, (_, i) => (
        <span key={i} className={`progress__seg${i < current ? ' progress__seg--done' : ''}`} />
      ))}
    </div>
  );
}

/** 큰 제목 + 설명 */
export function PageHeading({ title, sub }: { title: string; sub?: string }) {
  return (
    <div className="heading">
      <h2 className="heading__title">{title}</h2>
      {sub && <p className="heading__sub">{sub}</p>}
    </div>
  );
}

/** 로딩·오류 같은 안내 화면 */
export function Notice({ title, body, children }: { title: string; body?: string; children?: ReactNode }) {
  return (
    <div className="screen">
      <TopBar />
      <div className="notice">
        <h2 className="notice__title">{title}</h2>
        {body && <p className="heading__sub">{body}</p>}
        {children}
      </div>
    </div>
  );
}
