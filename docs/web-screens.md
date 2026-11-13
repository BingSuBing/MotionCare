# 동승자 웹 화면 명세 (M00~M09)

> 기준: 스키마 v0.3 (`docs/firestore-schema.md`), 피그마 캡처 `docs/design/mobile/*.png`
> 피그마는 정적 시안이라 **바람·조명·앞뒤 좌석 온도 구분**이 남아 있어요. 아래 "피그마와 다른 점"이 우선이에요.

## 경로

QR 주소는 `/join?session=<id>` 하나예요. 접속하면 상태를 보고 알맞은 화면으로 보내요.

| 경로 | 화면 | 진행 바 | 상단 번호 |
|---|---|---|---|
| `/join?session=:sid` | 분기 (아래 "진입 분기") | | |
| `/s/:sid/name` | M00 이름 입력 | | |
| `/s/:sid/welcome` | M01 참여하기 | | 01 / 09 |
| `/s/:sid/seat` | M02 좌석 선택 | 1/4 | 02 / 09 |
| `/s/:sid/sensitivity` | M03 민감도 | 2/4 | 03 / 09 |
| `/s/:sid/climate` | M04 희망 온도 | 3/4 | 04 / 09 |
| `/s/:sid/care` | M05 시각 케어·휴식 | 4/4 | 05 / 09 |
| `/s/:sid/done` | M06 제출 완료 | | 06 / 09 |
| `/s/:sid/home` | M07 이동 중 홈 | | 07 / 09 |
| `/s/:sid/discomfort` | M08 불편 알리기 | | 08 / 09 |
| `/s/:sid/sent/:requestId` | M09 전달 완료 | | 09 / 09 |

### 진입 분기 (`/join`)

1. 익명 로그인 (`useAnonAuth`)
2. 세션 구독 (`useSession`). 없으면 "세션을 찾을 수 없어요", `active === false`면 "이동이 끝났어요"
3. `passengers`에서 `uid == 내 uid` 문서 찾기
   - 없음 → M00
   - 있음 + `submittedAt == null` → M03
   - 있음 + `submittedAt` 있음 → M07
4. `/s/:sid/*` 화면에 바로 들어와도 같은 검사를 해서, 좌석이 없는데 M03 이후로 오면 M00으로 보내요.

### 공통 상태

- 이름: M00에서 받은 이름은 좌석 등록 전까지 `sessionStorage`(`motioncare:name:<sid>`)에 둬요. 좌석 등록 후에는 `passengers/{seat}.name`이 기준이에요.
- 화면 하단 캡션: `편안타 · {이름}의 탑승자 케어` (이름이 없으면 `편안타 · 탑승자 케어`), `captionFor()` 사용
- 상단 ‹ 뒤로가기는 이전 단계 경로로 이동해요 (M07, M09 제외).

---

## M00 이름 입력 (피그마 없음, 새 화면)

- 제목 "어떻게 불러드릴까요?", 설명 "운전자와 가족에게 이 이름으로 보여요."
- 입력칸 1개 (`.input`, 최대 10자, 앞뒤 공백 제거, 빈 값이면 버튼 비활성)
- 버튼 "시작하기" → 이름 저장 → M01
- 이미 좌석이 있으면 이름을 `passengers/{seat}.name`에도 반영

## M01 참여하기 (`M01.png`)

- 읽기: `session.tripTitle`, `session.driverName`
- 상단 이미지: `web/public/images/welcome.png` (일러스트)
- 연초록 카드: 차 아이콘 + `tripTitle`, "`{driverName}`님의 차량에서 초대했어요.", "차량 연결 확인됨"
- 안내 3줄(아이콘 + 문구)과 회색 안내 문구는 피그마 그대로
- 버튼 "내 좌석 선택하기" → M02

## M02 좌석 선택 (`M02.png`)

- 읽기: `passengers` 컬렉션 전체 (실시간 구독), `session.driverName`
- 2×2 좌석 카드
  - 운전석: 항상 비활성, "`{driverName}` · 사용 중"
  - 다른 사람이 쓰는 좌석: 비활성, "`{name}` · 사용 중"
  - 내가 고른 좌석: 선택 스타일, "`{내 이름}` · 선택됨"
  - 빈 좌석: "선택 가능"
- 범례 칩 3개(선택됨/사용 중/선택 가능), 안내 박스 "이미 사용 중인 좌석은 선택할 수 없어요."
- 버튼 "이 좌석으로 계속" → **트랜잭션으로 등록** (스키마 5.1) → M03
  - 실패(다른 사람이 먼저 선택): "방금 다른 분이 선택했어요. 다른 좌석을 골라 주세요."
  - 제출 전 좌석 변경: 이전 내 좌석 문서 삭제 후 새 좌석 등록
- 쓰기: `passengers/{seat}` = `{ uid, name, ...PASSENGER_DEFAULTS, joinedAt, updatedAt }`

## M03 민감도 (`M03.png`)

- 읽기/쓰기: `sensitivity`, `sicknessTriggers`
- `SENSITIVITY_OPTIONS` 4개 (`OptionRow`, radio), "함께 알려주세요 · 선택"에 `TRIGGER_OPTIONS` 2개 (다중 선택, `OptionRow` compact + check)
- 민감도를 고르지 않으면 버튼 비활성
- 버튼 "실내 취향 설정하기" → `updateDoc` → M04

## M04 희망 온도 (`M04.png`)

**피그마와 다른 점**

- "바람 세기", "바람에 민감한가요?", "조명 분위기" 섹션 **전부 삭제**
- 온도 범위 표기 18–28 → **18–26°C** (`TempStepper`가 처리)
- 하단 안내 문구: "모든 탑승자의 희망 온도 중앙값으로 실내 온도를 맞춰요. 운전자가 확인 후 적용해요."

**동작**

- 초기값: `desiredTemp` → 없으면 `cabinTemp(hvacState)`를 정수로 반올림 → 없으면 24 (18~26으로 제한)
- 버튼 "케어 취향 설정하기" → `desiredTemp` 저장 → M05

## M05 시각 케어·휴식 (`M05.png`)

- "시각 케어를 사용할까요?" 네/아니요 (`Segmented`). 아니요면 `visualMode: "off"`, 아래 안내 방식 목록 숨김
- 안내 방식 3개: `dot` 움직이는 점(Orbit 아이콘) / `horizon` 수평선(AlignCenter) / `minimal` 최소 시각 신호(Minus)
- 정기 휴식 알림 `Toggle` + 30/60/90분 `Segmented`. 끄면 `restIntervalMin: null`
- 강도(`visualIntensity`)는 이 화면에 없어요. 기본값 30 유지 (3단계에서 슬라이더 추가)
- 버튼 "내 설정 제출하기" → 위 값 + `submittedAt: serverTimestamp()` 저장 → M06

## M06 제출 완료 (`M06.png`)

**피그마와 다른 점**

- 요약 카드 행: 멀미 민감도 / **희망 온도**(예: 24°C) / 시각 케어(예: 움직이는 점) / 휴식 알림(예: 60분마다). "바람 / 조명" 행 삭제
- 연초록 카드 제목 "함께 조율한 추천" → **"모두의 희망 온도"**, 큰 숫자 `hvacState.medianTemp`(없으면 내 희망 온도)
- 설명: 희망 온도를 낸 사람 목록 "수빈 24° · 지훈 22° · 지민 23° → 중앙값 23°" (`passengers` 구독, 클라이언트에서 `medianTemp()`로 미리보기)
- 칩: `TEMP_STATUS_LABEL[hvacState.tempStatus]`

**동작**: "내 설정 수정하기"(tertiary) → M03 / 버튼 "실시간 케어 확인하기" → M07

## M07 이동 중 홈 (`M07.png`)

**피그마와 다른 점**

- 카드 제목 "현재 뒷좌석 환경" → **"현재 실내 환경"**, 큰 숫자 `cabinTemp(hvacState)`
- "바람 1단 · 직접 송풍 제외", "전체 조명 30%" 삭제 → `AIR_INTAKE_LABEL[airIntake]`("외기 유입 중"/"내기 순환 중")
- 우측 상단 상태: `TEMP_STATUS_LABEL[tempStatus]`

**나머지**

- 제목 "`{이름}`의 편안한 이동", 설명 "`{tripTitle}` · 여정 케어가 함께하고 있어요."
- 시각 케어 카드: `VISUAL_MODE_LABEL[visualMode]` + "강도 `{visualIntensity}`%" (off면 "시각 케어 꺼짐")
- 휴식 알림 카드: 다음 알림 시각 = `submittedAt`부터 `restIntervalMin` 간격으로 지금 이후 첫 시각 (꺼짐이면 "휴식 알림 꺼짐")
- "내 취향 수정하기 →" → M03 / 버튼 "불편해요" → M08

## M08 불편 알리기 (`M08.png`)

**피그마와 다른 점**: "바람이 너무 세요" → **"답답해요"**(Wind 아이콘, 값 `stuffy`)

- `DISCOMFORT_OPTIONS` 5개 (`OptionRow` tone="discomfort", 아이콘: Frown/Snowflake/Sun/Wind/Coffee)
- 선택에 따라 연초록 안내 카드 문구 변경
  - nausea: "화면을 잠깐 쉬어주세요" / "시선을 편안히 두고 쉬어보세요. 증상이 계속되면 안전한 장소에서 충분히 쉬는 것이 좋아요."
  - cold/hot: "운전자에게 온도 조정을 제안할게요"
  - stuffy: "운전자에게 환기(외기 유입)를 제안할게요"
  - rest: "가까운 휴식 장소를 함께 찾아볼게요"
- 버튼 "`{라벨}` 요청 보내기" → `requests`에 `add({ seat, uid, type: "REPORT_DISCOMFORT", value, status: "pending", createdAt })` → M09(`requestId`)

## M09 전달 완료 (`M09.png`)

- 요청 문서 구독. 배지 "`HH:MM` · `{라벨}`" (주황 `StatusPill`)
- "`{좌석명}` · `{이름}` — 운전자에게 요청을 전달했어요."
- `status === "done"`이면 배지 옆에 "운전자 확인됨" 표시
- "이제 이렇게 도와드려요" 목록 (value별)
  - 공통: "차량 화면에 요청 표시 / `{driverName}`님이 안전할 때 확인해요."
  - nausea, stuffy: "환기 제안 / 운전자 확인 후 외기로 바꿔요."
  - cold, hot: "온도 조정 제안 / 운전자 확인 후 설정을 조정해요."
  - nausea, rest: "가까운 휴식 장소 추천 / `route.nextRestStop`이 있으면 이름·거리, 없으면 '경로 정보를 확인하고 있어요'"
- 버튼 "내 케어 상태로 돌아가기" → M07

---

## 구현 순서 (브랜치)

1. `feature/web-join`: 진입 분기, 훅(`usePassengers`, `useMyPassenger`), M00, M01, M02
2. `feature/web-profile`: M03, M04, M05, M06
3. `feature/web-home`: M07, M08, M09
