import { useEffect, useState } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';
import { paths, type Session } from '../types';

export type SessionState =
  | { status: 'loading' }
  | { status: 'not_found' }
  | { status: 'error'; message: string }
  | { status: 'ready'; session: Session };

/**
 * sessions/{sid} 문서를 실시간으로 구독해요.
 * 콘솔이나 차량 앱이 값을 바꾸면 화면이 바로 따라 바뀌어요.
 */
export function useSession(sid: string | null, enabled: boolean): SessionState {
  const [state, setState] = useState<SessionState>({ status: 'loading' });

  useEffect(() => {
    if (!sid || !enabled) return;
    const unsubscribe = onSnapshot(
      doc(db, paths.session(sid)),
      (snap) =>
        setState(
          snap.exists()
            ? { status: 'ready', session: snap.data() as Session }
            : { status: 'not_found' },
        ),
      (err) => setState({ status: 'error', message: err.code }),
    );
    return unsubscribe;
  }, [sid, enabled]);

  return state;
}
