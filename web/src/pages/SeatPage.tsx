import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Armchair, CircleCheck, CircleX, User } from 'lucide-react';
import { Button, Chip, PageHeading, ProgressBar, Screen, TopBar } from '../components/ui';
import { SeatTakenError, claimSeat } from '../lib/passengers';
import { SEAT_LABEL, type Seat } from '../types';
import { captionFor, useSessionCtx } from '../session/context';

type TileState = 'driver' | 'taken' | 'selected' | 'free';

function SeatTile({
  label,
  sub,
  state,
  onClick,
}: {
  label: string;
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
      <span className="seat__label">{label}</span>
      <span className="seat__sub">{sub}</span>
    </button>
  );
}

/** M02 좌석 선택 */
export default function SeatPage() {
  const { sid, uid, session, passengers, me, name } = useSessionCtx();
  const navigate = useNavigate();
  const [picked, setPicked] = useState<Seat | null>(me?.seat ?? null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const ownerOf = (seat: Seat) => passengers.find((p) => p.seat === seat);

  const tile = (seat: Seat) => {
    const owner = ownerOf(seat);
    const takenByOther = owner && owner.uid !== uid;
    // 다른 사람이 방금 가져간 좌석은 선택에서 풀어요
    const state: TileState = takenByOther ? 'taken' : picked === seat ? 'selected' : 'free';
    const sub =
      state === 'taken'
        ? `${owner?.name || '탑승자'} · 사용 중`
        : state === 'selected'
          ? `${name} · 선택됨`
          : '선택 가능';
    return (
      <SeatTile
        label={SEAT_LABEL[seat]}
        sub={sub}
        state={state}
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
      header={<TopBar step={2} onBack={() => navigate('../welcome')} />}
      action={
        <Button onClick={submit} disabled={!picked || saving || pickedIsTaken}>
          {saving ? '등록하는 중…' : '이 좌석으로 계속'}
        </Button>
      }
      caption={captionFor(name)}
    >
      <ProgressBar current={1} />
      <PageHeading title="어디에 앉으셨나요?" sub={`${name}님의 좌석을 선택해 주세요.`} />

      <div className="seat-map">
        <p className="seat-map__front">차량 앞쪽 ↑</p>
        <span className="seat-map__dash" aria-hidden />
        <div className="seat-map__grid">
          <SeatTile
            label="운전석"
            sub={`${session.driverName || '운전자'} · 사용 중`}
            state="driver"
          />
          {tile('front_right')}
          {tile('rear_left')}
          {tile('rear_right')}
        </div>
      </div>

      <div className="chips">
        <Chip selected>선택됨</Chip>
        <Chip>사용 중</Chip>
        <Chip>선택 가능</Chip>
      </div>

      <div className={`info-box${error ? ' info-box--error' : ''}`} role={error ? 'alert' : undefined}>
        {error ?? '이미 사용 중인 좌석은 선택할 수 없어요.'}
      </div>
    </Screen>
  );
}
