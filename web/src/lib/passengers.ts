import { doc, runTransaction, serverTimestamp, updateDoc } from 'firebase/firestore';
import { db } from '../firebase';
import {
  PASSENGER_DEFAULTS,
  paths,
  type FanPreference,
  type Seat,
  type Sensitivity,
  type SicknessTrigger,
  type VisualMode,
} from '../types';

/** 다른 사람이 먼저 그 좌석을 차지했을 때 */
export class SeatTakenError extends Error {
  constructor() {
    super('seat_taken');
  }
}

/**
 * 좌석 등록 (스키마 5.1). 트랜잭션이라 두 사람이 동시에 눌러도 한 명만 성공해요.
 * - 빈 좌석: 새로 만들기 (이전 좌석이 있으면 그 설정을 그대로 옮겨요)
 * - 내 좌석: 이름만 갱신
 * - 남의 좌석: SeatTakenError
 */
export async function claimSeat(opts: {
  sid: string;
  seat: Seat;
  uid: string;
  name: string;
  prevSeat?: Seat | null;
}) {
  const { sid, seat, uid, name, prevSeat } = opts;
  const ref = doc(db, paths.passenger(sid, seat));
  const prevRef = prevSeat && prevSeat !== seat ? doc(db, paths.passenger(sid, prevSeat)) : null;

  await runTransaction(db, async (tx) => {
    // 트랜잭션은 읽기를 먼저 모두 끝내고 쓰기를 해야 해요.
    const snap = await tx.get(ref);
    const prevSnap = prevRef ? await tx.get(prevRef) : null;

    if (snap.exists()) {
      if (snap.data().uid !== uid) throw new SeatTakenError();
      tx.update(ref, { name, updatedAt: serverTimestamp() });
      return;
    }

    // 이전 좌석이 내 것이면 설정을 옮기고 지워요.
    const carried = prevSnap?.exists() && prevSnap.data().uid === uid ? prevSnap.data() : null;

    tx.set(ref, {
      ...PASSENGER_DEFAULTS,
      ...(carried ?? {}),
      uid,
      name,
      joinedAt: carried?.joinedAt ?? serverTimestamp(),
      updatedAt: serverTimestamp(),
    });
    if (carried && prevRef) tx.delete(prevRef);
  });
}

/** 이미 좌석이 있는 사람이 이름을 바꿨을 때 */
export function updatePassengerName(sid: string, seat: Seat, name: string) {
  return updateDoc(doc(db, paths.passenger(sid, seat)), { name, updatedAt: serverTimestamp() });
}

/** 내 좌석 문서의 설정 저장 (M03~M05). submittedAt 은 제출할 때만 넣어요 */
export function updateMyPassenger(
  sid: string,
  seat: Seat,
  patch: {
    sensitivity?: Sensitivity;
    sicknessTriggers?: SicknessTrigger[];
    desiredTemp?: number;
    fanPreference?: FanPreference | null;
    avoidDirectWind?: boolean | null;
    visualMode?: VisualMode;
    restIntervalMin?: number | null;
    submit?: boolean;
  },
) {
  const { submit, ...fields } = patch;
  return updateDoc(doc(db, paths.passenger(sid, seat)), {
    ...fields,
    ...(submit ? { submittedAt: serverTimestamp() } : {}),
    updatedAt: serverTimestamp(),
  });
}
