import type { ReactNode } from 'react';
import { useNavigate } from 'react-router-dom';
import { AlignCenter, Coffee, EyeOff, Minus, Orbit } from 'lucide-react';
import { Button, Card, PageHeading, Screen, TopBar } from '../components/ui';
import { AIR_INTAKE_LABEL, TEMP_STATUS_LABEL, VISUAL_MODE_LABEL, cabinTemp, fmtTemp, type VisualMode } from '../types';
import { fmtTime, nextTick, useNow } from '../lib/time';
import { captionFor, useSessionCtx } from '../session/context';

const VISUAL_ICON: Record<VisualMode, ReactNode> = {
  off: <EyeOff size={26} />,
  dot: <Orbit size={26} />,
  horizon: <AlignCenter size={26} />,
  minimal: <Minus size={26} />,
};

/** M07 {이름}의 편안한 이동 */
export default function HomePage() {
  const { me, name, session } = useSessionCtx();
  const navigate = useNavigate();
  const now = useNow();
  if (!me) return null;

  const hvac = session.hvacState;
  const updated = hvac?.updatedAt?.toDate();

  // 다음 휴식 알림: 제출 시각부터 간격마다
  const restStart = me.submittedAt?.toDate() ?? me.joinedAt?.toDate() ?? null;
  const nextRest = me.restIntervalMin && restStart ? nextTick(restStart, me.restIntervalMin, now) : null;

  return (
    <Screen
      header={<TopBar step={7} />}
      action={<Button onClick={() => navigate('../discomfort')}>불편해요</Button>}
      caption={captionFor(name)}
    >
      <PageHeading
        title={`${name}의 편안한 이동`}
        sub={`${session.tripTitle ? `${session.tripTitle} · ` : ''}여정 케어가 함께하고 있어요.`}
      />

      <Card tone="tint">
        <div className="row-between">
          <p className="card-label">현재 실내 환경</p>
          {hvac && <span className="status-text">{TEMP_STATUS_LABEL[hvac.tempStatus]}</span>}
        </div>
        {hvac ? (
          <>
            <p className="big-number card-gap">{fmtTemp(cabinTemp(hvac))}°C</p>
            <p className="card-text">{AIR_INTAKE_LABEL[hvac.airIntake] ?? hvac.airIntake}</p>
            {updated && <p className="card-strong">{fmtTime(updated)} 차량 반영</p>}
          </>
        ) : (
          <p className="card-text">차량 공조 정보를 기다리고 있어요.</p>
        )}
      </Card>

      <Card>
        <div className="icon-row">
          <span className="icon-row__icon">{VISUAL_ICON[me.visualMode]}</span>
          <div>
            <p className="icon-row__title">
              {me.visualMode === 'off' ? '시각 케어 꺼짐' : `${VISUAL_MODE_LABEL[me.visualMode]} 케어`}
            </p>
            <p className="icon-row__sub">
              {me.visualMode === 'off' ? '필요하면 내 취향에서 켤 수 있어요' : `강도 ${me.visualIntensity}%`}
            </p>
          </div>
        </div>
      </Card>

      <Card>
        <div className="icon-row">
          <span className="icon-row__icon">
            <Coffee size={26} />
          </span>
          <div>
            <p className="icon-row__title">{nextRest ? `다음 휴식 알림 · ${fmtTime(nextRest)}` : '휴식 알림 꺼짐'}</p>
            <p className="icon-row__sub">
              {nextRest ? `${me.restIntervalMin}분 간격 · 운전자에게도 알려드려요` : '필요하면 내 취향에서 켤 수 있어요'}
            </p>
          </div>
        </div>
      </Card>

      <div>
        <p className="section-title">지금은 편안한가요?</p>
        <p className="helper helper--below">
          메스껍거나 춥다면 아래 버튼으로 알려주세요. 운전자가 확인하고 케어를 조정할 수 있어요.
        </p>
        <Button variant="tertiary" onClick={() => navigate('../sensitivity')}>
          내 취향 수정하기 →
        </Button>
      </div>
    </Screen>
  );
}
