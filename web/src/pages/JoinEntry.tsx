import { Navigate, useSearchParams } from 'react-router-dom';
import { Notice } from '../components/ui';

/** QR 주소 /join?session=<id> → /s/<id> 로 넘겨요 */
export default function JoinEntry() {
  const [params] = useSearchParams();
  const sid = params.get('session')?.trim();
  if (!sid) return <Notice title="QR 코드를 다시 찍어 주세요" body="접속 주소에 차량 정보가 없어요." />;
  return <Navigate to={`/s/${encodeURIComponent(sid)}`} replace />;
}
