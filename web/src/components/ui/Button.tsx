import type { ButtonHTMLAttributes } from 'react';

type Variant = 'primary' | 'brand' | 'secondary' | 'tertiary';

/**
 * primary: 진한 초록 (화면 하단 주요 버튼)
 * brand: 연두 (차량에 적용 등)
 * secondary: 흰 바탕 테두리
 * tertiary: 글자만 ("내 설정 수정하기")
 */
export function Button({
  variant = 'primary',
  block = true,
  className = '',
  type = 'button',
  ...rest
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant; block?: boolean }) {
  const cls = ['btn', `btn--${variant}`, block && variant !== 'tertiary' ? 'btn--block' : '', className]
    .filter(Boolean)
    .join(' ');
  return <button type={type} className={cls} {...rest} />;
}
