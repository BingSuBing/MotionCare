import { useMemo, useState } from 'react';
import { Navigate, Outlet, useParams } from 'react-router-dom';
import { Notice } from '../components/ui';
import { useAnonAuth } from '../hooks/useAnonAuth';
import { useSession } from '../hooks/useSession';
import { usePassengers } from '../hooks/usePassengers';
import { loadName, saveName } from '../lib/nameStore';
import { SessionContext, useSessionCtx, type SessionCtx } from './context';

/**
 * /s/:sid/* 공통 틀.
 * 익명 로그인 → 세션 구독 → 탑승자 구독을 한 번만 하고, 아래 화면들에 context로 나눠줘요.
 * 로딩·세션 없음·이동 종료는 여기서 한꺼번에 처리해요.
 */
export default function SessionLayout() {
  const { sid = '' } = useParams();
  const { uid, error: authError } = useAnonAuth();
  const session = useSession(sid || null, uid !== null);
  const passengers = usePassengers(sid || null, uid !== null);
  const [storedName, setStoredName] = useState(() => loadName(sid));

  const me = useMemo(() => passengers.list.find((p) => p.uid === uid) ?? null, [passengers.list, uid]);

  if (authError) return <Notice title="로그인에 실패했어요" body={`잠시 후 다시 시도해 주세요. (${authError})`} />;
  if (!uid || session.status === 'loading' || passengers.loading) return <Notice title="차량에 연결하는 중…" />;
  if (session.status === 'error' || passengers.error)
    return <Notice title="연결에 실패했어요" body="네트워크를 확인하고 다시 시도해 주세요." />;
  if (session.status === 'not_found')
    return <Notice title="세션을 찾을 수 없어요" body="차량 화면의 QR 코드를 다시 찍어 주세요." />;
  if (!session.session.active) return <Notice title="이동이 끝났어요" body="함께해 주셔서 고마워요." />;

  const ctx: SessionCtx = {
    sid,
    uid,
    session: session.session,
    passengers: passengers.list,
    me,
    name: me?.name || storedName,
    setName: (name) => {
      saveName(sid, name);
      setStoredName(name);
    },
  };

  return (
    <SessionContext.Provider value={ctx}>
      <Outlet />
    </SessionContext.Provider>
  );
}

/** /s/:sid 로 들어오면 내 상태에 맞는 화면으로 보내요 (web-screens.md "진입 분기") */
export function StartRedirect() {
  const { me } = useSessionCtx();
  if (!me) return <Navigate to="name" replace />;
  if (!me.submittedAt) return <Navigate to="sensitivity" replace />;
  return <Navigate to="home" replace />;
}

/** 이름이 없으면 M00으로 */
export function RequireName() {
  const { name } = useSessionCtx();
  return name ? <Outlet /> : <Navigate to="../name" replace />;
}

/** 좌석이 없으면 M00으로 (M03 이후 화면) */
export function RequireSeat() {
  const { me } = useSessionCtx();
  return me ? <Outlet /> : <Navigate to="../name" replace />;
}
