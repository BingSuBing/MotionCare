import { useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlignCenter, ChevronRight, Coffee, EyeOff, Minus, Orbit } from 'lucide-react';
import {
  Button,
  Card,
  PageHeading,
  Screen,
  Segmented,
  TempStepper,
  Toggle,
  TopBar,
} from '../components/ui';
import { updateMyPassenger } from '../lib/passengers';
import { initialTemp } from '../lib/temp';
import { fmtTime, nextTick, useNow } from '../lib/time';
import {
  AIR_INTAKE_LABEL,
  REST_INTERVALS,
  TEMP_STATUS_LABEL,
  VISUAL_MODE_LABEL,
  cabinTemp,
  fmtTemp,
  type VisualMode,
} from '../types';
import { honorific, useSessionCtx } from '../session/context';

const VISUAL_ICON: Record<VisualMode, ReactNode> = {
  off: <EyeOff size={26} />,
  dot: <Orbit size={26} />,
  horizon: <AlignCenter size={26} />,
  minimal: <Minus size={26} />,
};

const SAVE_DELAY_MS = 800;

/**
 * 메인 (이동 중)
 * - 현재 실내 환경 / 내 희망 온도(바로 수정)
 * - 시각 케어 시작·바꾸기 / 휴식 알림 시간 바꾸기 / 내 설정 바꾸기
 * - 큼지막한 "불편해요" 버튼
 */
export default function HomePage() {
  const { sid, me, session } = useSessionCtx();
  const navigate = useNavigate();
  const now = useNow();

  // 희망 온도: 누를 때마다 바로 보이고, 0.8초 뒤 한 번만 저장
  const [temp, setTemp] = useState(() => initialTemp(me?.desiredTemp, session.hvacState));
  const [saved, setSaved] = useState(true);
  const timer = useRef<number | undefined>(undefined);
  useEffect(() => () => window.clearTimeout(timer.current), []);

  const [restOpen, setRestOpen] = useState(false);

  if (!me) return null;
  const seat = me.seat;

  const changeTemp = (v: number) => {
    setTemp(v);
    setSaved(false);
    window.clearTimeout(timer.current);
    timer.current = window.setTimeout(() => {
      updateMyPassenger(sid, seat, { desiredTemp: v })
        .then(() => setSaved(true))
        .catch(() => setSaved(false));
    }, SAVE_DELAY_MS);
  };

  const changeRest = (min: number | null) => {
    updateMyPassenger(sid, seat, { restIntervalMin: min }).catch(() => undefined);
  };

  const hvac = session.hvacState;
  const restStart = me.submittedAt?.toDate() ?? me.joinedAt?.toDate() ?? null;
  const nextRest = me.restIntervalMin && restStart ? nextTick(restStart, me.restIntervalMin, now) : null;
  const visualOn = me.visualMode !== 'off';

  return (
    <Screen
      header={<TopBar />}
      action={
        <Button size="lg" onClick={() => navigate('../discomfort')}>
          불편해요
        </Button>
      }
    >
      <PageHeading
        title={`${honorific(session.driverName || '운전자')}과 이동 중이에요`}
        sub={`${session.carNumber ? `${session.carNumber}의 ` : ''}편안타에 참여 중이에요.`}
      />

      <Card tone="tint">
        <p className="card-label">현재 실내 환경</p>
        {hvac ? (
          <>
            <p className="big-number card-gap">{fmtTemp(cabinTemp(hvac))}°C</p>
            <p className="card-strong">{AIR_INTAKE_LABEL[hvac.airIntake] ?? hvac.airIntake}</p>
            {hvac.medianTemp != null && (
              <p className="card-text">
                추천 {fmtTemp(hvac.medianTemp)}°C · {TEMP_STATUS_LABEL[hvac.tempStatus]}
                {hvac.nextReviewAt ? ` · 다음 조정 ${fmtTime(hvac.nextReviewAt.toDate())}` : ''}
              </p>
            )}
          </>
        ) : (
          <p className="card-text">차량 공조 정보를 기다리고 있어요.</p>
        )}
      </Card>

      <div className="row-between">
        <p className="section-title">희망 온도</p>
        <span className="saved-note" aria-live="polite">
          {saved ? '차량에 전달됨' : '전달하는 중…'}
        </span>
      </div>
      <TempStepper value={temp} onChange={changeTemp} compact />

      {/* 시각 케어: 시작하기 / 방식 바꾸기 */}
      <button type="button" className="card card-button" onClick={() => navigate('../care')}>
        <div className="icon-row">
          <span className="icon-row__icon">{VISUAL_ICON[me.visualMode]}</span>
          <div>
            <p className="icon-row__title">
              {visualOn ? `${VISUAL_MODE_LABEL[me.visualMode]} 케어` : '움직이는 점 케어 시작하기'}
            </p>
            <p className="icon-row__sub">
              {visualOn ? `강도 ${me.visualIntensity}% · 눌러서 방식 바꾸기` : '화면 가장자리 안내로 시각적 편안함을 도와요'}
            </p>
          </div>
          <ChevronRight size={20} className="icon-row__chevron" />
        </div>
      </button>

      {/* 휴식 알림: 시간 바꾸기 */}
      <Card>
        <button
          type="button"
          className="card-button card-button--plain"
          onClick={() => setRestOpen((o) => !o)}
          aria-expanded={restOpen}
        >
          <div className="icon-row">
            <span className="icon-row__icon">
              <Coffee size={26} />
            </span>
            <div>
              <p className="icon-row__title">{nextRest ? `다음 휴식 알림 · ${fmtTime(nextRest)}` : '휴식 알림 꺼짐'}</p>
              <p className="icon-row__sub">
                {nextRest ? `${me.restIntervalMin}분 간격 · 눌러서 알림 시간 바꾸기` : '눌러서 알림 켜기'}
              </p>
            </div>
            <ChevronRight size={20} className="icon-row__chevron" />
          </div>
        </button>
        {restOpen && (
          <div className="card-gap">
            <div className="row-between">
              <span className="card-label">정기 휴식 알림</span>
              <Toggle
                on={me.restIntervalMin != null}
                onChange={(on) => changeRest(on ? 60 : null)}
                label="정기 휴식 알림"
              />
            </div>
            {me.restIntervalMin != null && (
              <div className="card-gap">
                <Segmented
                  options={REST_INTERVALS.map((m) => ({ value: m as number, label: `${m}분` }))}
                  value={me.restIntervalMin}
                  onChange={(m) => changeRest(m)}
                />
              </div>
            )}
          </div>
        )}
      </Card>

      <div>
        <p className="section-title">지금은 편안한가요?</p>
        <p className="helper helper--below">
          메스껍거나 춥다면 아래 버튼으로 알려주세요. 운전자가 확인하고 케어를 조정할 수 있어요.
        </p>
        <Button variant="tertiary" onClick={() => navigate('../sensitivity')}>
          내 설정 바꾸기 →
        </Button>
      </div>
    </Screen>
  );
}
