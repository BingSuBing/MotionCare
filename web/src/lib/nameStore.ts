// M00에서 받은 이름을 좌석 등록 전까지 보관해요 (새로고침해도 유지).
// 시크릿 모드 등에서 sessionStorage가 막히면 조용히 무시해요.
const key = (sid: string) => `motioncare:name:${sid}`;

export function loadName(sid: string): string {
  try {
    return sessionStorage.getItem(key(sid)) ?? '';
  } catch {
    return '';
  }
}

export function saveName(sid: string, name: string) {
  try {
    sessionStorage.setItem(key(sid), name);
  } catch {
    /* 저장 실패해도 화면 상태로는 계속 진행 */
  }
}
