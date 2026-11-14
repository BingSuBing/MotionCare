import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Armchair, CircleCheck, CircleX, User } from 'lucide-react';
import { Button, Chip, PageHeading, ProgressBar, Screen, TopBar } from '../components/ui';
import { SeatTakenError, claimSeat } from '../lib/passengers';
import { SEAT_LABEL, type Seat } from '../types';
import { honorific, useSessionCtx } from '../session/context';

type TileState = 'driver' | 'taken' | 'selected' | 'free';

/**
 * 좌석 칸: 다른 사람이 앉은 칸은 "그 사람 이름 / 사용 중",
 * 빈 칸은 "좌석 이름 / 선택 가능", 내가 고른 칸은 "좌석 이름 / 선택됨"
 */
function SeatTile({
  title,
  sub,
  state,
  onClick,
}: {
  title: string;
  sub: string;
  state: TileState;
  onClick?: () => void;
}) {
  const icon =
    state === 'driver' ? (
      <CircleX size={26} />
    ) : state === 'taken' ? (
      <User size={26} />
    ) : state === 'selected' ? (
      <CircleCheck size={26} />
    ) : (
      <Armchair size={26} />
    );
  const disabled = state === 'driver' || state === 'taken';

  return (
    <button
      type="button"
      className={`seat seat--${state === 'driver' ? 'taken' : state}`}
      onClick={onClick}
      disabled={disabled}
      aria-pressed={state === 'selected'}
    >
      <span className="seat__icon">{icon}</span>
      <span className="seat__label">{title}</span>
      <span className="seat__sub">{sub}</span>
    </button>
  );
}

/** 처음 설정 1/3: 좌석 선택 */
export default function SeatPage() {
  const { sid, uid, session, passengers, me, name } = useSessionCtx();
  const navigate = useNavigate();
  const [picked, setPicked] = useState<Seat | null>(me?.seat ?? null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ownerOf = (seat: Seat) => passengers.find((p) => p.seat === seat);

  const tile = (seat: Seat) => {
    const owner = ownerOf(seat);
    if (owner && owner.uid !== uid) {
      return <SeatTile title={owner.name || '탑승자'} sub="사용 중" state="taken" />;
    }
    const selected = picked === seat;
    return (
      <SeatTile
        title={SEAT_LABEL[seat]}
        sub={selected ? '선택됨' : '선택 가능'}
        state={selected ? 'selected' : 'free'}
        onClick={() => {
          setError(null);
          setPicked(seat);
        }}
      />
    );
  };

  const pickedIsTaken = picked ? Boolean(ownerOf(picked) && ownerOf(picked)?.uid !== uid) : false;

  const submit = async () => {
    if (!picked || saving || pickedIsTaken) return;
    setSaving(true);
    setError(null);
    try {
      await claimSeat({ sid, seat: picked, uid, name, prevSeat: me?.seat ?? null });
      navigate('../sensitivity');
    } catch (e) {
      setError(
        e instanceof SeatTakenError
          ? '방금 다른 분이 선택했어요. 다른 좌석을 골라 주세요.'
          : '좌석을 등록하지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
      setPicked(null);
      setSaving(false);
    }
  };

  return (
    <Screen
      header={<TopBar step={1} total={3} onBack={() => navigate('../welcome')} />}
      action={
        <Button onClick={submit} disabled={!picked || saving || pickedIsTaken}>
          {saving ? '등록하는 중…' : '이 좌석으로 계속'}
        </Button>
      }
    >
      <ProgressBar current={1} />
      <PageHeading title="어디에 앉으셨나요?" sub={`${honorific(name)}의 좌석을 선택해 주세요.`} />

      <div className="seat-map">
        <span className="seat-map__front-bar">차량 앞쪽</span>
        <div className="seat-map__grid">
          <SeatTile title={session.driverName || '운전자'} sub="선택 불가" state="driver" />
          {tile('front_right')}
          {tile('rear_left')}
          {tile('rear_right')}
        </div>
      </div>

      <div className="chips">
        <Chip>사용 중</Chip>
        <Chip tone="white">선택 가능</Chip>
      </div>

      <div className={`info-box${error ? ' info-box--error' : ''}`} role={error ? 'alert' : undefined}>
        {error ?? '이미 사용 중인 좌석은 선택할 수 없어요.'}
      </div>
    </Screen>
  );
}
