# Firestore 스키마 (MotionCare)

> 이 문서가 **기준**이에요. 차량 앱(Kotlin, `shared/`)과 웹(TS, `web/src/types.ts`)은 여기 적힌 이름을 그대로 써요.
> 필드 이름이나 값을 바꿀 때는 **이 문서부터 고치고**, PR 제목에 `[schema]`를 붙여요.

- 버전: v0.2 (작성: 이수빈)
- 공조 범위 수정 : **온도 조절 + 외기/내기 전환**만 다뤄요. 풍량은 다루지 않아요.

---

## 1. 전체 구조

```
sessions/{sessionId}                 ← 차량 앱이 생성
├─ (필드) createdAt, active, hvacState, route
├─ passengers/{seat}                 ← 폰이 기록 (좌석당 문서 1개, 희망 온도 포함)
└─ requests/{requestId}              ← 폰이 생성, 차량 앱이 status 변경 (외기/내기 전환)
```

**온도와 외기는 방식이 달라요**

| 기능 | 폰이 하는 일 | 차량 앱이 하는 일 |
|---|---|---|
| 온도 | 자기 좌석의 **희망 온도**(`passengers/{seat}.desiredTemp`)를 저장 | 모든 희망 온도의 **중앙값**을 계산해서 적용 |
| 외기/내기 | `requests`에 전환 요청을 남김 | 요청을 받아 실행하고 `done`/`failed` 기록 |

온도는 "여러 사람의 의견을 모으는" 기능이라 상태(희망 온도)로 저장하고, 외기 전환은 "한 번 실행하는" 명령이라 요청으로 남겨요.

**쓰기 권한 원칙**

| 경로 | 차량 앱 | 폰(웹) |
|---|---|---|
| `sessions/{id}` 생성, `active` | ✅ | ❌ |
| `hvacState`, `route` | ✅ | ❌ (읽기만) |
| `passengers/{seat}` | 읽기 | ✅ 자기 좌석만 |
| `requests/{id}` 생성 | ❌ | ✅ |
| `requests/{id}` 의 `status`, `reason`, `handledAt` | ✅ | ❌ |

1단계에서는 Firestore가 테스트 모드라서 이 원칙이 강제되지 않아요. 코드는 처음부터 이 원칙대로 짜고, 4단계에서 `firestore.rules`로 막아요.

---

## 2. 공통 값

### 2.1 세션 ID

- 소문자와 숫자로 된 **6자리**예요. 예: `a8f3k2`
- 차량 앱이 실행될 때 만들어요.
- 헷갈리기 쉬운 문자(`0 o 1 l i`)는 빼는 걸 권장해요.
- **개발용 고정 세션: `test01`** (QR 없이 테스트할 때 써요)

### 2.2 좌석 `Seat`

| 값 | 의미 |
|---|---|
| `front_right` | 조수석 |
| `rear_left` | 뒷좌석 왼쪽 |
| `rear_right` | 뒷좌석 오른쪽 |

운전석은 쓰지 않아요. 운전자는 폰으로 조작하지 않아요.

### 2.3 시간

- 모든 시간 필드는 Firestore `Timestamp`예요.
- 쓸 때는 **서버 시간**을 넣어요.
  - Kotlin: `FieldValue.serverTimestamp()`
  - TS: `serverTimestamp()`

### 2.4 QR / 접속 URL

```
https://motioncare-fec56.web.app/join?session=<sessionId>&seat=<Seat>
```

---

## 3. 공조 규칙

### 3.1 차량 제약 (Pleos 에뮬레이터 확인 결과)

| 기능 | 운전석 | 조수석 | 뒷좌석 |
|---|---|---|---|
| 온도 (SDK 16~30℃) | 개별 | 개별 | ❌ 전용 구역 없음 |
| 외기/내기 | 공통 | 공통 | 공통 |

### 3.2 온도: 희망 온도 중앙값

1. 동승자가 폰에서 자기 **희망 온도**를 정하면 `passengers/{seat}.desiredTemp`에 저장돼요.
2. 차량 앱은 `passengers`를 구독하다가 값이 바뀌면 **중앙값**을 다시 계산해요.
   - 대상: `desiredTemp`가 `null`이 아닌 좌석만
   - 홀수 명: 가운데 값 / 짝수 명: 가운데 두 값의 평균
   - 결과를 **0.5℃ 단위로 반올림**하고 **18~26℃로 제한**해요
   - 예: [20, 22, 25] → 22 / [20, 23] → 21.5
3. 계산한 값을 **운전석과 조수석 구역 둘 다**에 적용해요. 뒷좌석 전용 구역이 없어서 앞 두 구역을 맞춰야 뒷좌석까지 같은 온도가 돼요.
4. 결과를 `hvacState`에 기록해요(`driverTemp`, `passengerTemp`, `medianTemp`, `voteCount`).

- **연속 변경 방지**: 마지막 변경 후 **3초** 동안 추가 변경이 없을 때 계산하고 적용해요. 버튼을 여러 번 눌러도 한 번만 적용돼요.
- 희망 온도가 하나도 없으면 아무것도 바꾸지 않아요.
- 2단계: 적용 전에 차량 화면에 "동승자 희망 온도 22.5℃로 맞출까요?" 원터치 승인을 띄워요. 1단계 시연에서는 바로 적용해요.

### 3.3 외기/내기 전환

- 값: `outside`(외기 유입, 환기) / `recirc`(내기 순환)
- 동승자 누구나 요청할 수 있고, 마지막 요청이 적용돼요.
- 멀미에는 환기가 도움이 되므로 "멀미나요"(2단계)와 함께 외기 전환을 제안할 수 있어요.
- ⚠️ **A 확인**: Vehicle SDK로 외기/내기 전환이 되는지 확인 필요. 안 되면 이 기능은 "차량 화면 안내만" 하도록 바꿔요.

---

## 4. `sessions/{sessionId}`

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `createdAt` | Timestamp | 차량 | 세션 생성 시각 |
| `active` | boolean | 차량 | 이동 중이면 `true`, 끝나면 `false`. 웹은 `false`면 "종료된 세션" 화면을 보여줘요 |
| `hvacState` | map | 차량 | 현재 **실제** 공조 값 (아래 4.1) |
| `route` | map \| 없음 | 차량 | 2단계. 경로 정보 (아래 4.2) |

### 4.1 `hvacState`

| 필드 | 타입 | 설명 |
|---|---|---|
| `driverTemp` | number | 운전석 구역 실제 설정 온도(℃), SDK에서 읽은 값 |
| `passengerTemp` | number | 조수석 구역 실제 설정 온도(℃), SDK에서 읽은 값 |
| `airIntake` | string | `outside` \| `recirc`, SDK에서 읽은 값 |
| `medianTemp` | number \| null | 마지막으로 계산한 희망 온도 중앙값 |
| `voteCount` | number | 중앙값 계산에 쓴 희망 온도 개수 |
| `updatedAt` | Timestamp | 마지막으로 반영된 시각 |

- 웹은 "현재 22.5℃ · 3명 희망 온도의 중앙값"처럼 보여줘요.
- 운전자가 차량 공조를 직접 바꾸면 `driverTemp`, `passengerTemp`가 `medianTemp`와 달라질 수 있어요. 차량 앱은 실제 값을 그대로 기록해요.

### 4.2 `route` (2단계, 초안)

| 필드 | 타입 | 설명 |
|---|---|---|
| `destinationName` | string | 목적지 이름 |
| `eta` | Timestamp | 도착 예정 시각 |
| `remainingMin` | number | 도착까지 남은 시간(분) |
| `restIntervalMin` | number | 휴식 간격(분), 예: 50 |
| `nextRestStop` | map \| null | `{ name: string, etaMin: number }` 다음 휴게소와 남은 시간(분) |
| `updatedAt` | Timestamp | |

---

## 5. `sessions/{sessionId}/passengers/{seat}`

문서 ID가 좌석 값이에요(`rear_left` 등). 폰이 처음 접속할 때 만들고, 이후에 수정해요.

| 필드 | 타입 | 값 | 설명 |
|---|---|---|---|
| `uid` | string | | 익명 로그인 uid (보안 규칙용) |
| `name` | string | | 표시 이름, 비어 있으면 좌석명으로 표시 |
| `desiredTemp` | number \| null | 18~26, 0.5 단위 | **희망 온도**. `null`이면 투표하지 않음 |
| `sensitivity` | string | `low` \| `medium` \| `high` | 멀미 민감도 |
| `sicknessLevel` | string | `ok` \| `mild` \| `severe` | 괜찮음 / 조금 불편 / 많이 불편 |
| `visualMode` | string | `off` \| `dot` \| `horizon` | 시각 보조 모드 (3단계) |
| `updatedAt` | Timestamp | | |

- 처음 접속할 때 `desiredTemp`는 `null`이에요. 사용자가 +/-를 처음 누르면 현재 온도를 기준으로 값이 생겨요.
- **"멀미나요" 동작 (2단계)**: 폰이 `sicknessLevel`을 `mild`나 `severe`로 바꿔요. 차량 앱은 `passengers`를 구독하다가 값이 `ok`에서 다른 값으로 바뀌면 알림, TTS, 외기 전환 제안을 띄워요.

---

## 6. `sessions/{sessionId}/requests/{requestId}`

**외기/내기 전환에만** 써요. 문서 ID는 자동 생성(`add()`)이에요.

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `seat` | Seat | 폰 | 요청한 좌석 |
| `uid` | string | 폰 | 요청자 익명 uid |
| `type` | string | 폰 | `SET_AIR_INTAKE` (나중에 다른 명령이 생기면 추가) |
| `value` | string | 폰 | `outside` \| `recirc` |
| `status` | string | 폰은 생성 시 `pending`, 이후 차량 | `pending` \| `done` \| `failed` |
| `reason` | string | 차량 | `failed`일 때만. 아래 6.2 |
| `createdAt` | Timestamp | 폰 | |
| `handledAt` | Timestamp | 차량 | done이나 failed로 바꾼 시각 |

### 6.1 요청 흐름

```
[폰]  add({ seat, uid, type:"SET_AIR_INTAKE", value:"outside", status:"pending", createdAt })
        │
[차량] where status == "pending" 구독 → Vehicle SDK 설정
        ├─ 성공: hvacState.airIntake 갱신 → status:"done", handledAt
        └─ 실패: status:"failed", reason, handledAt
        │
[폰]  자기 요청 문서 구독 → 대기 표시 해제 / hvacState 구독 → 상태 갱신
```

- `pending`이 여러 개 쌓이면 **가장 최근 것만 실행**하고, 나머지는 `failed` / `superseded`로 처리해요.
- 폰은 10초 안에 `pending`이 안 바뀌면 "차량 응답 없음"을 표시해요.

### 6.2 `reason` 값

| 값 | 의미 |
|---|---|
| `sdk_error` | Vehicle SDK 호출 실패 |
| `superseded` | 더 최근 요청이 있어서 건너뜀 |
| `unsupported` | 지원하지 않는 type이나 value |
| `declined` | 운전자가 승인하지 않음 (2단계) |

---

## 7. 테스트 데이터 (`test01`)

Firebase 콘솔에서 직접 만들어요.

```
sessions/test01
  createdAt : (timestamp) 지금
  active    : true
  hvacState : { driverTemp: 22, passengerTemp: 22, airIntake: "recirc",
                medianTemp: null, voteCount: 0, updatedAt: (timestamp) 지금 }
```

- **B(웹) 단독 테스트**: 콘솔에서 `hvacState.passengerTemp`, `airIntake`를 바꾸면 웹 화면이 따라 바뀌어야 해요.
- **A(차량) 단독 테스트**
  - 온도: 콘솔에서 `passengers/rear_left`(`desiredTemp: 20`), `passengers/front_right`(`desiredTemp: 23`)를 만들면 차량 앱이 21.5℃로 맞춰야 해요.
  - 외기: 콘솔에서 `requests`에 `seat: front_right, type: SET_AIR_INTAKE, value: outside, status: pending, uid: "console"`를 추가하면 외기로 바뀌어야 해요.

---

## 8. 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| v0.1 | 2026-11-12 | 초안 작성 |
| v0.2 | 2026-11-12 | 풍량 기능 제거. 온도는 희망 온도 중앙값 방식(`passengers.desiredTemp`), `requests`는 외기/내기 전환(`SET_AIR_INTAKE`) 전용 |
