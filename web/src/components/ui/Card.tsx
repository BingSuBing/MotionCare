import type { HTMLAttributes, ReactNode } from 'react';

/** 흰 카드(기본) / 연초록 카드(tint) / 주황 카드(discomfort) */
export function Card({
  tone = 'default',
  className = '',
  ...rest
}: HTMLAttributes<HTMLElement> & { tone?: 'default' | 'tint' | 'discomfort' }) {
  const cls = ['card', tone !== 'default' ? `card--${tone}` : '', className].filter(Boolean).join(' ');
  return <section className={cls} {...rest} />;
}

export function CardTitle({ icon, children }: { icon?: ReactNode; children: ReactNode }) {
  return (
    <h3 className="card__title">
      {icon}
      {children}
    </h3>
  );
}

/** 상태 배지: "제출 완료", "운전자 적용 대기", "11:18 · 메스꺼워요" */
export function StatusPill({
  tone = 'tint',
  children,
}: {
  tone?: 'tint' | 'neutral' | 'discomfort';
  children: ReactNode;
}) {
  return <span className={`pill${tone !== 'tint' ? ` pill--${tone}` : ''}`}>{children}</span>;
}

/** 원형 아이콘 (M06 체크, M09 하트) */
export function Seal({ children }: { children: ReactNode }) {
  return <div className="seal">{children}</div>;
}
