import { useEffect, useState } from 'react';
import { collection, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';
import { isSeat, paths, type Passenger, type Seat } from '../types';

export type SeatedPassenger = Passenger & { seat: Seat };

/**
 * sessions/{sid}/passengers 전체를 실시간으로 구독해요.
 * 문서 ID가 좌석 값이라 seat 필드로 붙여서 돌려줘요.
 */
export function usePassengers(sid: string | null, enabled: boolean) {
  const [state, setState] = useState<{ loading: boolean; list: SeatedPassenger[]; error: string | null }>({
    loading: true,
    list: [],
    error: null,
  });

  useEffect(() => {
    if (!sid || !enabled) return;
    const unsubscribe = onSnapshot(
      collection(db, paths.passengers(sid)),
      (snap) => {
        const list = snap.docs
          .filter((d) => isSeat(d.id))
          .map((d) => ({ ...(d.data() as Passenger), seat: d.id as Seat }));
        setState({ loading: false, list, error: null });
      },
      (err) => setState({ loading: false, list: [], error: err.code }),
    );
    return unsubscribe;
  }, [sid, enabled]);

  return state;
}
