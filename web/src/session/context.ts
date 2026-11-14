import { createContext, useContext } from 'react';
import type { Session } from '../types';
import type { SeatedPassenger } from '../hooks/usePassengers';

export interface SessionCtx {
  sid: string;
  uid: string;
  session: Session;
  /** 이 세션의 모든 탑승자 (좌석 포함) */
  passengers: SeatedPassenger[];
  /** 내 좌석 문서. 아직 좌석을 안 골랐으면 null */
  me: SeatedPassenger | null;
  /** 좌석이 있다가 사라졌는지 (운전자가 내보냄) */
  removed: boolean;
  clearRemoved: () => void;
  /** 내 이름: 좌석 문서의 이름 → 없으면 M00에서 입력한 이름 */
  name: string;
  setName: (name: string) => void;
}

export const SessionContext = createContext<SessionCtx | null>(null);

/** /s/:sid 아래 화면에서 세션 정보를 꺼내 써요 */
export function useSessionCtx(): SessionCtx {
  const ctx = useContext(SessionContext);
  if (!ctx) throw new Error('useSessionCtx는 SessionLayout 안에서만 쓸 수 있어요');
  return ctx;
}

/** "현아 님" (이름이 없으면 "탑승자 님") */
export const honorific = (name: string) => `${name || '탑승자'} 님`;
