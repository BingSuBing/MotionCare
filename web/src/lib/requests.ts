import { addDoc, collection, serverTimestamp } from 'firebase/firestore';
import { db } from '../firebase';
import { paths, type Discomfort, type Seat } from '../types';

/** M08 불편 알리기 → requests 에 추가하고 문서 ID를 돌려줘요 (스키마 6장) */
export async function sendDiscomfort(opts: { sid: string; seat: Seat; uid: string; value: Discomfort }) {
  const ref = await addDoc(collection(db, paths.requests(opts.sid)), {
    seat: opts.seat,
    uid: opts.uid,
    type: 'REPORT_DISCOMFORT',
    value: opts.value,
    status: 'pending',
    createdAt: serverTimestamp(),
  });
  return ref.id;
}
