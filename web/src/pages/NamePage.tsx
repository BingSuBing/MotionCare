import { useState, type FormEvent } from 'react';
import { useNavigate } from 'react-router-dom';
import { Button, PageHeading, Screen, TopBar } from '../components/ui';
import { updatePassengerName } from '../lib/passengers';
import { NAME_MAX } from '../types';
import { captionFor, useSessionCtx } from '../session/context';

/** M00 이름 입력 (피그마 없음, 새 화면) */
export default function NamePage() {
  const { sid, me, name, setName } = useSessionCtx();
  const navigate = useNavigate();
  const [value, setValue] = useState(name);
  const [saving, setSaving] = useState(false);
  const trimmed = value.trim();

  const submit = async (e?: FormEvent) => {
    e?.preventDefault();
    if (!trimmed || saving) return;
    setSaving(true);
    setName(trimmed);
    // 이미 좌석이 있으면 좌석 문서의 이름도 바꿔요
    if (me && me.name !== trimmed) {
      await updatePassengerName(sid, me.seat, trimmed).catch(() => undefined);
    }
    navigate('../welcome');
  };

  return (
    <Screen
      header={<TopBar />}
      action={
        <Button onClick={() => submit()} disabled={!trimmed || saving}>
          시작하기
        </Button>
      }
      caption={captionFor(trimmed)}
    >
      <PageHeading title="어떻게 불러드릴까요?" sub="운전자와 가족에게 이 이름으로 보여요." />
      <form onSubmit={submit}>
        <input
          className="input"
          value={value}
          onChange={(e) => setValue(e.target.value.slice(0, NAME_MAX))}
          placeholder="이름 또는 별명"
          maxLength={NAME_MAX}
          autoComplete="nickname"
          enterKeyHint="done"
          aria-label="이름 또는 별명"
          autoFocus
        />
        <p className="helper helper--below">
          {NAME_MAX}자까지 입력할 수 있어요. 실명이 아니어도 괜찮아요.
        </p>
      </form>
    </Screen>
  );
}
