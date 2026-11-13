import { useEffect, useState } from 'react';
import { onAuthStateChanged, signInAnonymously } from 'firebase/auth';
import { auth } from '../firebase';

/**
 * 익명 로그인.
 * 이미 로그인한 적이 있으면 브라우저에 저장된 같은 uid를 그대로 써요.
 */
export function useAnonAuth() {
  const [uid, setUid] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (user) => {
      if (user) {
        setUid(user.uid);
      } else {
        signInAnonymously(auth).catch((e: { code?: string }) =>
          setError(e.code ?? '알 수 없는 오류'),
        );
      }
    });
    return unsubscribe;
  }, []);

  return { uid, error };
}
