import { useState } from 'react';

/** 저장 버튼용: 저장 중 표시 + 실패 문구. run(작업) 이 성공하면 true */
export function useSaving() {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const run = async (task: () => Promise<unknown>) => {
    if (saving) return false;
    setSaving(true);
    setError(null);
    try {
      await task();
      return true;
    } catch {
      setError('저장하지 못했어요. 네트워크를 확인하고 다시 시도해 주세요.');
      return false;
    } finally {
      setSaving(false);
    }
  };

  return { saving, error, run };
}
