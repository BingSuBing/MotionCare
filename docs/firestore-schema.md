# Firestore 스키마 (MotionCare)

> 이 문서가 **기준**이에요. 차량 앱(Kotlin, `shared/`)과 웹(TS, `web/src/types.ts`)은 여기 적힌 이름을 그대로 써요.
> 필드 이름이나 값을 바꿀 때는 **이 문서부터 고치고**, PR 제목에 `[schema]`를 붙여요.

- 버전: v0.1 (초안, 작성: 이수빈)

---

## 1. 전체 구조

```
sessions/{sessionId}                 ← 차량 앱이 생성
├─ (필드) createdAt, active, hvacState, route
├─ passengers/{seat}                 ← 폰이 기록 (좌석당 문서 1개)
└─ requests/{requestId}              ← 폰이 생성, 차량 앱이 status 변경
```

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
https://<프로젝트ID>.web.app/join?session=<sessionId>&seat=<Seat>
예) https://motioncare-xxxx.web.app/join?session=test01&seat=rear_left
```

---

## 3. `sessions/{sessionId}`

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `createdAt` | Timestamp | 차량 | 세션 생성 시각 |
| `active` | boolean | 차량 | 이동 중이면 `true`, 끝나면 `false`. 웹은 `false`면 "종료된 세션" 화면을 보여줘요 |
| `hvacState` | map | 차량 | 현재 **실제** 공조 값 (아래 3.1) |
| `route` | map \| 없음 | 차량 | 2단계. 경로 정보 (아래 3.2) |

### 3.1 `hvacState`

| 필드 | 타입 | 범위 | 설명 |
|---|---|---|---|
| `temp` | number | 18.0 ~ 26.0, 0.5 단위 | 설정 온도(℃) |
| `fan` | number | 0 ~ 8  | 풍량 단계 |
| `updatedAt` | Timestamp | | 마지막으로 반영된 시각 |

- 이 값은 Vehicle SDK에서 **실제로 읽은 값**이에요. 요청값이 아니에요.
- 좌석(존)별 공조가 지원되면 나중에 `zones: { rear_left: {temp, fan}, ... }`를 추가할 수 있어요. 1단계에서는 단일 값으로 해요.

### 3.2 `route` (2단계, 초안)

| 필드 | 타입 | 설명 |
|---|---|---|
| `destinationName` | string | 목적지 이름 |
| `eta` | Timestamp | 도착 예정 시각 |
| `remainingMin` | number | 도착까지 남은 시간(분) |
| `restIntervalMin` | number | 휴식 간격(분), 예: 50 |
| `nextRestStop` | map \| null | `{ name: string, etaMin: number }` 다음 휴게소와 남은 시간(분) |
| `updatedAt` | Timestamp | |

---

## 4. `sessions/{sessionId}/passengers/{seat}`

문서 ID가 좌석 값이에요(`rear_left` 등). 폰이 처음 접속할 때 만들고, 이후에 수정해요.

| 필드 | 타입 | 값 | 설명 |
|---|---|---|---|
| `uid` | string | | 익명 로그인 uid (보안 규칙용) |
| `name` | string | | 표시 이름, 비어 있으면 좌석명으로 표시 |
| `sensitivity` | string | `low` \| `medium` \| `high` | 멀미 민감도 |
| `sicknessLevel` | string | `ok` \| `mild` \| `severe` | 괜찮음 / 조금 불편 / 많이 불편 |
| `visualMode` | string | `off` \| `dot` \| `horizon` | 시각 보조 모드 (3단계) |
| `updatedAt` | Timestamp | | |

**"멀미나요" 동작 (2단계)**: 폰이 `sicknessLevel`을 `mild`나 `severe`로 바꿔요. 차량 앱은 `passengers`를 구독하다가 값이 `ok`에서 다른 값으로 바뀌면 알림, TTS, 공조 제안을 띄워요. 별도의 request를 만들지 않아요.

---

## 5. `sessions/{sessionId}/requests/{requestId}`

문서 ID는 자동 생성(`add()`)이에요.

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `seat` | Seat | 폰 | 요청한 좌석 |
| `uid` | string | 폰 | 요청자 익명 uid |
| `type` | string | 폰 | `SET_TEMP` \| `SET_FAN` |
| `value` | number | 폰 | 원하는 **절댓값**. 예: 21.5 (+1 같은 변화량이 아니에요) |
| `status` | string | 폰은 생성 시 `pending`, 이후 차량 | `pending` \| `done` \| `failed` |
| `reason` | string | 차량 | `failed`일 때만. 아래 5.2 |
| `createdAt` | Timestamp | 폰 | |
| `handledAt` | Timestamp | 차량 | done이나 failed로 바꾼 시각 |

### 5.1 요청 흐름

```
[폰]  add({ seat, uid, type:"SET_TEMP", value:21, status:"pending", createdAt })
        │
[차량] where status == "pending" 구독 → 범위 검사 → Vehicle SDK 설정
        ├─ 성공: hvacState 갱신 → status:"done", handledAt
        └─ 실패: status:"failed", reason, handledAt
        │
[폰]  자기 요청 문서 구독 → 대기 표시 해제 / hvacState 구독 → 온도 갱신
```

- **절댓값으로 보내는 이유**: 버튼을 여러 번 빠르게 눌러도 마지막 요청값으로 수렴해요.
- 차량 앱은 같은 좌석의 `pending`이 여러 개 쌓이면 **가장 최근 것만 실행**하고, 나머지는 `failed` / `superseded`로 처리해도 돼요.
- 폰은 10초 안에 `pending`이 안 바뀌면 "차량 응답 없음"을 표시해요.

### 5.2 `reason` 값

| 값 | 의미 |
|---|---|
| `out_of_range` | 허용 범위 밖 |
| `sdk_error` | Vehicle SDK 호출 실패 |
| `superseded` | 더 최근 요청이 있어서 건너뜀 |
| `unsupported` | 지원하지 않는 type |

---

## 6. 테스트 데이터 (`test01`)

Firebase 콘솔에서 직접 만들어요.

```
sessions/test01
  createdAt : (timestamp) 지금
  active    : true
  hvacState : { temp: 22, fan: 3, updatedAt: (timestamp) 지금 }
```

- **B(웹) 단독 테스트**: 콘솔에서 `hvacState.temp`를 바꾸면 웹 화면이 따라 바뀌어야 해요.
- **A(차량) 단독 테스트**: 콘솔에서 `requests`에 문서를 추가하면(`seat: rear_left, type: SET_TEMP, value: 20, status: pending, uid: "console"`) 차량 앱이 처리해야 해요.

---

## 7. 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| v0.1 | 2026-11-12 | 초안 작성 (B) |
