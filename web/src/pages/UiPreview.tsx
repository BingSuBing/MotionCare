import { useState } from 'react';
import { Coffee, Frown, Heart, HeartHandshake, Orbit, AlignCenter, Minus, Snowflake, Sun, Wind, Check } from 'lucide-react';
import {
  Button,
  Card,
  CardTitle,
  Chip,
  OptionRow,
  PageHeading,
  ProgressBar,
  Screen,
  Seal,
  Segmented,
  StatusPill,
  TempStepper,
  Toggle,
  TopBar,
} from '../components/ui';
import { SENSITIVITY_OPTIONS, type Sensitivity } from '../types';

/** /dev/ui : 공통 컴포넌트를 피그마와 나란히 비교하는 개발용 화면 */
export default function UiPreview() {
  const [sens, setSens] = useState<Sensitivity>('high');
  const [temp, setTemp] = useState(24);
  const [visual, setVisual] = useState<'yes' | 'no'>('yes');
  const [mode, setMode] = useState<'dot' | 'horizon' | 'minimal'>('dot');
  const [rest, setRest] = useState(true);
  const [interval, setInterval] = useState(60);
  const [discomfort, setDiscomfort] = useState('nausea');

  return (
    <Screen
      header={<TopBar step={3} onBack={() => history.back()} />}
      action={<Button>실내 취향 설정하기</Button>}
      caption="가족 여행 · 수빈의 탑승자 케어"
    >
      <ProgressBar current={2} />
      <PageHeading title="이동할 때, 어떠세요?" sub="수빈님에게 맞는 케어를 준비할게요." />

      {/* M03 */}
      <div style={{ display: 'grid', gap: 12 }}>
        {SENSITIVITY_OPTIONS.map((o) => (
          <OptionRow
            key={o.value}
            label={o.label}
            desc={o.desc}
            selected={sens === o.value}
            onSelect={() => setSens(o.value)}
          />
        ))}
      </div>

      {/* M04 */}
      <TempStepper value={temp} onChange={setTemp} />

      {/* M05 */}
      <Card>
        <CardTitle>시각 케어를 사용할까요?</CardTitle>
        <div style={{ marginTop: 16 }}>
          <Segmented
            options={[
              { value: 'yes', label: '네, 사용할래요' },
              { value: 'no', label: '아니요' },
            ]}
            value={visual}
            onChange={setVisual}
            showCheck
          />
        </div>
      </Card>

      <p className="section-title">선호하는 안내 방식</p>
      <div style={{ display: 'grid', gap: 12 }}>
        <OptionRow compact mark="check" icon={<Orbit size={22} />} label="움직이는 점" selected={mode === 'dot'} onSelect={() => setMode('dot')} />
        <OptionRow compact mark="check" icon={<AlignCenter size={22} />} label="수평선" selected={mode === 'horizon'} onSelect={() => setMode('horizon')} />
        <OptionRow compact mark="check" icon={<Minus size={22} />} label="최소 시각 신호" selected={mode === 'minimal'} onSelect={() => setMode('minimal')} />
      </div>

      <Card>
        <div className="row-between">
          <CardTitle>정기 휴식 알림</CardTitle>
          <Toggle on={rest} onChange={setRest} label="정기 휴식 알림" />
        </div>
        {rest && (
          <div style={{ marginTop: 16 }}>
            <Segmented
              options={[
                { value: 30, label: '30분' },
                { value: 60, label: '60분' },
                { value: 90, label: '90분' },
              ]}
              value={interval}
              onChange={setInterval}
            />
          </div>
        )}
      </Card>

      {/* M08 */}
      <p className="section-title">어떤 점이 불편한가요?</p>
      <div style={{ display: 'grid', gap: 12 }}>
        {[
          { v: 'nausea', label: '메스꺼워요', icon: <Frown size={22} /> },
          { v: 'cold', label: '너무 추워요', icon: <Snowflake size={22} /> },
          { v: 'hot', label: '너무 더워요', icon: <Sun size={22} /> },
          { v: 'windy', label: '바람이 너무 세요', icon: <Wind size={22} /> },
          { v: 'rest', label: '잠깐 쉬고 싶어요', icon: <Coffee size={22} /> },
        ].map((o) => (
          <OptionRow
            key={o.v}
            tone="discomfort"
            compact
            icon={o.icon}
            label={o.label}
            selected={discomfort === o.v}
            onSelect={() => setDiscomfort(o.v)}
          />
        ))}
      </div>

      <Card tone="tint">
        <CardTitle icon={<Heart size={20} />}>화면을 잠깐 쉬어주세요</CardTitle>
        <p className="helper" style={{ marginTop: 8, fontSize: 14 }}>
          시선을 편안히 두고 쉬어보세요.
        </p>
      </Card>

      {/* 칩, 배지, 원형 아이콘 */}
      <div className="chips">
        <Chip selected>선택됨</Chip>
        <Chip>사용 중</Chip>
        <Chip>선택 가능</Chip>
      </div>
      <div className="chips">
        <StatusPill>제출 완료</StatusPill>
        <StatusPill tone="neutral">운전자 적용 대기</StatusPill>
        <StatusPill tone="discomfort">11:18 · 메스꺼워요</StatusPill>
      </div>
      <div style={{ display: 'flex', gap: 24, justifyContent: 'center' }}>
        <Seal>
          <Check size={40} />
        </Seal>
        <Seal>
          <HeartHandshake size={40} />
        </Seal>
      </div>

      <Card tone="tint">
        <CardTitle>모두의 희망 온도</CardTitle>
        <p className="big-number" style={{ marginTop: 12 }}>
          23°C
        </p>
        <p className="helper" style={{ marginTop: 8, fontSize: 14 }}>
          수빈 24° · 지훈 22° · 지민 23° → 중앙값 23°
        </p>
      </Card>

      <input className="input" placeholder="이름 또는 별명" maxLength={10} />
      <Button variant="secondary">내 설정 수정</Button>
      <Button variant="brand">차량에 적용</Button>
      <Button disabled>설정 수집 중</Button>
      <div style={{ textAlign: 'center' }}>
        <Button variant="tertiary">내 설정 수정하기</Button>
      </div>
    </Screen>
  );
}
