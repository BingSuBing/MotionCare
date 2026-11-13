import { useEffect, useState } from 'react';
import { doc, onSnapshot } from 'firebase/firestore';
import { db } from '../firebase';
import { paths, type CareRequest } from '../types';

/** requests/{id} 하나를 구독해요 (M09: 운전자 확인 여부) */
export function useRequest(sid: string, requestId: string | undefined) {
  const [state, setState] = useState<{ loading: boolean; request: CareRequest | null }>({
    loading: true,
    request: null,
  });

  useEffect(() => {
    if (!requestId) return;
    const unsubscribe = onSnapshot(
      doc(db, `${paths.requests(sid)}/${requestId}`),
      (snap) =>
        setState({
          loading: false,
          // 서버 시간이 아직 안 정해졌으면 예상값으로 받아요
          request: snap.exists() ? (snap.data({ serverTimestamps: 'estimate' }) as CareRequest) : null,
        }),
      () => setState({ loading: false, request: null }),
    );
    return unsubscribe;
  }, [sid, requestId]);

  return state;
}
