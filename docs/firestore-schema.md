# Firestore 스키마 (MotionCare / 편안타)

> 이 문서가 **기준**이에요. 차량 앱(Kotlin, `shared/src/main/java/com/carfortable/shared/FirestoreModels.kt`)과 웹(TS, `web/src/types.ts`)은 여기 적힌 이름을 그대로 써요.
> 필드 이름이나 값을 바꿀 때는 **이 문서부터 고치고**, PR 제목에 `[schema]`를 붙여요.

- 버전: **v0.3.1** (작성: 이수빈) — Figma "편안타" 디자인(M01~M09) 반영, 차량 식별용 `carUid` 추가
- 공조 범위: **온도(희망 온도 중앙값) + 외기/내기 전환**. 풍량·바람 방향·조명은 다루지 않아요.

---

## 1. 전체 구조

```
sessions/{sessionId}                 ← 차량 앱이 생성 (차 1대 = 세션 1개 = QR 1개)
├─ (필드) carUid, createdAt, active, tripTitle, driverName, hvacState, route
├─ passengers/{seat}                 ← 폰이 기록 (좌석당 문서 1개: 이름, 취향, 희망 온도)
└─ requests/{requestId}              ← 폰이 생성, 차량 앱이 처리 (불편 알리기)
```

**사용자 흐름 (동승자 폰)**

```
QR 스캔 → M00 이름 입력 → M01 참여하기 → M02 좌석 선택 → M03 민감도
       → M04 희망 온도 → M05 시각 케어·휴식 → M06 제출 완료
       → M07 이동 중 홈 ⇄ M08 불편 알리기 → M09 전달 완료
```

- **사람 구별**: 폰(브라우저)마다 Firebase **익명 로그인 uid**가 따로 생겨요. 같은 QR로 4명이 들어와도 uid 4개로 구별돼요. 새로고침하거나 QR을 다시 찍어도 같은 uid가 유지돼요.
- **이름**: M00에서 받은 이름은 앱 안에 들고 있다가, M02에서 좌석을 정할 때 `passengers/{seat}`에 `uid`와 함께 저장해요.

**차량 앱과 폰 구별**: 둘 다 익명 로그인이라 uid만으로는 구별이 안 돼요. 그래서 차량 앱이 세션을 만들 때 **자기 uid를 `carUid`에 남기고**, 보안 규칙은 `request.auth.uid == carUid`이면 차량 앱으로 판단해요. `carUid`는 생성 후 바꿀 수 없어요.

**쓰기 권한 원칙**

| 경로 | 차량 앱 (`uid == carUid`) | 폰(웹) |
|---|---|---|
| `sessions/{id}` 생성 (`carUid` = 내 uid), `active`, `tripTitle`, `driverName` | ✅ | ❌ |
| `hvacState`, `route` | ✅ | ❌ (읽기만) |
| `passengers/{seat}` | 읽기 | ✅ 자기 좌석만 (`uid`가 내 uid) |
| `requests/{id}` 생성 | ❌ | ✅ |
| `requests/{id}`의 `status`, `handledAt` | ✅ | ❌ |

1단계에서는 Firestore가 테스트 모드라서 이 원칙이 강제되지 않아요. 코드는 처음부터 이 원칙대로 짜고, 4단계에서 `firestore.rules`로 막아요.

---

## 2. 공통 값

### 2.1 세션 ID

- 소문자와 숫자로 된 **6자리**예요. 예: `a8f3k2`
- 차량 앱이 실행될 때 만들어요.
- 헷갈리기 쉬운 문자(`0 o 1 l i`)는 빼는 걸 권장해요.
- **개발용 고정 세션: `test01`**

### 2.2 QR / 접속 URL

차 한 대에 **QR 하나**예요. 좌석은 폰에서 고르니까 URL에 좌석이 없어요.

```
https://motioncare-fec56.web.app/join?session=<sessionId>
예) https://motioncare-fec56.web.app/join?session=test01
```

### 2.3 좌석 `Seat`

| 값 | 화면 표기 |
|---|---|
| `front_right` | 조수석 |
| `rear_left` | 뒷좌석 왼쪽 |
| `rear_right` | 뒷좌석 오른쪽 |

- 운전석은 선택할 수 없어요. M02에서는 `driverName`으로 "건호 · 사용 중"이라고 표시만 해요.
- `passengers/{seat}` 문서가 있으면 그 좌석은 **사용 중**이에요.

### 2.4 시간

- 모든 시간 필드는 Firestore `Timestamp`예요.
- 쓸 때는 **서버 시간**을 넣어요. Kotlin은 `FieldValue.serverTimestamp()`, TS는 `serverTimestamp()`를 써요.

---

## 3. 공조 규칙

### 3.1 차량 제약 (Pleos 에뮬레이터 확인 결과)

| 기능 | 운전석 | 조수석 | 뒷좌석 | 사용 |
|---|---|---|---|---|
| 온도 (SDK 16~30℃) | 개별 | 개별 | ❌ 전용 구역 없음 | ✅ |
| 외기/내기 | 공통 | 공통 | 공통 | ✅ (SDK 지원 A 확인 필요) |
| 풍량, 바람 방향, 전원/A/C, 김서림, 조명 | | | | ❌ 제외 |

### 3.2 온도: 모두의 희망 온도 중앙값

1. 각 동승자가 M04에서 **희망 온도**를 정해요. 웹 버튼은 **1℃ 단위**, 범위는 **18~26℃**예요. 값은 `passengers/{seat}.desiredTemp`에 저장돼요.
2. 차량 앱은 `passengers`를 구독하다가 값이 바뀌면 **중앙값**을 계산해요.
   - 대상: `desiredTemp`가 `null`이 아닌 좌석만
   - 홀수 명이면 가운데 값, 짝수 명이면 가운데 두 값의 평균
   - **0.5℃ 단위로 반올림**하고 **18~26℃로 제한**해요
   - 예: [20, 22, 25] → 22 / [22, 24] → 23
3. 계산한 값을 **운전석·조수석 구역 둘 다**에 적용해요. 앞뒤를 나누지 않고 실내 전체를 한 온도로 맞춰요.
4. 마지막 변경 후 **3초** 뒤에 계산해요(연타 방지).
5. 적용 방식
   - **1단계**: 계산하면 바로 적용 → `tempStatus: "applied"`
   - **2단계**: 차량 화면에 "모두의 희망 온도 23℃로 맞출까요?" 원터치 승인 → 승인 전에는 `"pending"`, 승인하면 `"applied"`
   - 운전자가 차량에서 온도를 직접 바꾸면 → `"driver_set"`

### 3.3 외기/내기

- **폰에는 외기 버튼이 없어요.** 차량 앱(운전자)만 바꿔요.
- 동승자가 M08에서 "답답해요"나 "메스꺼워요"를 보내면, 차량 앱이 운전자에게 **외기 전환을 원터치로 제안**해요.
- 현재 상태는 `hvacState.airIntake`에 기록하고, 폰 M07에서 "외기 유입 중 / 내기 순환 중"으로 보여줘요.

---

## 4. `sessions/{sessionId}`

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `carUid` | string | 차량 | 세션을 만든 **차량 앱의 익명 uid**. 생성 시 한 번만 쓰고 바꾸지 않아요. 보안 규칙에서 차량 앱인지 확인할 때 써요 |
| `createdAt` | Timestamp | 차량 | 세션 생성 시각 |
| `active` | boolean | 차량 | 이동 중이면 `true`, 끝나면 `false`. 웹은 `false`면 "이동이 끝났어요" 화면을 보여줘요 |
| `tripTitle` | string | 차량 | M01 카드 제목. 예: "우리 가족 · 서울 → 강릉" |
| `driverName` | string | 차량 | M01 "건호님의 차량에서 초대했어요", M02 운전석 "건호 · 사용 중" |
| `hvacState` | map | 차량 | 현재 공조 상태 (아래 4.1) |
| `route` | map \| 없음 | 차량 | 2단계. 경로 정보 (아래 4.2) |

### 4.1 `hvacState`

| 필드 | 타입 | 설명 |
|---|---|---|
| `driverTemp` | number | 운전석 구역 실제 온도(℃), SDK에서 읽은 값 |
| `passengerTemp` | number | 조수석 구역 실제 온도(℃), SDK에서 읽은 값 |
| `airIntake` | string | `outside`(외기 유입) \| `recirc`(내기 순환), SDK에서 읽은 값 |
| `medianTemp` | number \| null | 마지막으로 계산한 희망 온도 중앙값 |
| `voteCount` | number | 중앙값 계산에 쓴 희망 온도 개수 |
| `tempStatus` | string | `applied`(적용됨) \| `pending`(운전자 적용 대기) \| `driver_set`(운전자가 직접 설정) |
| `updatedAt` | Timestamp | 마지막으로 반영된 시각 |

- 웹 M07의 "현재 실내 온도"는 두 구역이 같으면 그 값, 다르면 평균(0.5℃ 반올림)을 보여줘요.
- M06의 "운전자 적용 대기 / 적용됨" 칩은 `tempStatus`로 정해요.

### 4.2 `route` (2단계, 초안)

| 필드 | 타입 | 설명 |
|---|---|---|
| `destinationName` | string | 목적지 이름 |
| `eta` | Timestamp | 도착 예정 시각 |
| `remainingMin` | number | 도착까지 남은 시간(분) |
| `nextRestStop` | map \| null | `{ name: string, distanceKm: number, etaMin: number }`. M09 "횡성휴게소 · 약 12분 거리" |
| `updatedAt` | Timestamp | |

---

## 5. `sessions/{sessionId}/passengers/{seat}`

문서 ID가 좌석 값이에요(`rear_left` 등). M02에서 좌석을 고를 때 만들어지고, M03~M05에서 채워지고, M06에서 제출돼요.

| 필드 | 타입 | 값 | 화면 | 설명 |
|---|---|---|---|---|
| `uid` | string | | M02 | 익명 로그인 uid |
| `name` | string | 최대 10자 | M00 | 표시 이름. 예: "수빈" |
| `sensitivity` | string \| null | `none` \| `low` \| `medium` \| `high` | M03 | 없음 / 낮음 / 보통 / 높음 |
| `sicknessTriggers` | string[] | `video`, `screen` | M03 | "영상 시청 시 멀미가 잘 나요", "차량 이동 중 화면 보기 불편해요" |
| `desiredTemp` | number \| null | 18~26, 1℃ 단위 | M04 | 희망 온도. `null`이면 아직 안 정함 |
| `visualMode` | string | `off` \| `dot` \| `horizon` \| `minimal` | M05 | 사용 안 함 / 움직이는 점 / 수평선 / 최소 시각 신호 |
| `visualIntensity` | number | 0~100 | M05 | 시각 안내 강도(%), 기본 30 |
| `restIntervalMin` | number \| null | 30 \| 60 \| 90 | M05 | 정기 휴식 알림 간격. `null`이면 꺼짐 |
| `joinedAt` | Timestamp | | M02 | 좌석 등록 시각 |
| `submittedAt` | Timestamp \| null | | M06 | 설정 제출 시각. 값이 있으면 다시 접속할 때 M07로 바로 가요 |
| `updatedAt` | Timestamp | | | |

### 5.1 좌석 등록 (트랜잭션)

두 사람이 같은 좌석을 동시에 고를 수 있으니까 **트랜잭션**으로 등록해요.

```
runTransaction:
  seat 문서 읽기
  ├─ 없음 → 새로 만들기 { uid, name, 기본값..., joinedAt }
  ├─ 있음 + uid == 내 uid → 이름만 갱신
  └─ 있음 + uid != 내 uid → 실패: "방금 다른 분이 선택했어요" → M02로
```

- 제출 전에 좌석을 바꾸면 이전 좌석 문서를 지워요.
- 기본값: `sensitivity: null`, `sicknessTriggers: []`, `desiredTemp: null`, `visualMode: "dot"`, `visualIntensity: 30`, `restIntervalMin: 60`, `submittedAt: null`

### 5.2 다시 접속했을 때

QR을 다시 찍으면 `passengers`에서 `uid == 내 uid`인 문서를 찾아요.

| 상황 | 이동 |
|---|---|
| 문서 없음 | M00 이름 입력부터 |
| 문서 있음, `submittedAt == null` | M03 (설정 이어서 하기) |
| 문서 있음, `submittedAt` 있음 | M07 홈 |

---

## 6. `sessions/{sessionId}/requests/{requestId}`

M08 **불편 알리기** 전용이에요. 문서 ID는 자동 생성(`add()`)이에요.

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `seat` | Seat | 폰 | 요청한 좌석 |
| `uid` | string | 폰 | 요청자 uid |
| `type` | string | 폰 | `REPORT_DISCOMFORT` (나중에 다른 요청이 생기면 추가) |
| `value` | string | 폰 | 아래 6.1 |
| `status` | string | 폰은 `pending`으로 생성, 이후 차량 | `pending`(전달됨) \| `done`(운전자 확인) \| `failed` |
| `createdAt` | Timestamp | 폰 | |
| `handledAt` | Timestamp | 차량 | 운전자가 확인한 시각 |

### 6.1 불편 종류 `value`

| 값 | M08 표기 | 차량 앱 반응 |
|---|---|---|
| `nausea` | 메스꺼워요 | 알림 + TTS, 외기 전환 제안, (2단계) 휴게소 추천 |
| `cold` | 너무 추워요 | 알림, 온도 +1℃ 제안 |
| `hot` | 너무 더워요 | 알림, 온도 −1℃ 제안 |
| `stuffy` | 답답해요 | 알림, 외기 전환 제안 |
| `rest` | 잠깐 쉬고 싶어요 | 알림 + TTS, (2단계) 가까운 휴게소 추천 |

- 폰은 문서가 만들어지면 바로 M09 "잘 전해드렸어요"를 보여줘요.
- 요청을 보내도 차량 설정은 바로 바뀌지 않아요. 운전자가 확인하고 적용해요.

---

## 7. 테스트 데이터 (`test01`)

Firebase 콘솔에서 직접 만들어요.

```
sessions/test01
  carUid     : "console"   ← 4단계 규칙 적용 후에는 차량 앱의 실제 uid로 바꿔야 차량 앱이 쓸 수 있어요
  createdAt  : (timestamp) 지금
  active     : true
  tripTitle  : "우리 가족 · 서울 → 강릉"
  driverName : "건호"
  hvacState  : { driverTemp: 22, passengerTemp: 22, airIntake: "recirc",
                 medianTemp: null, voteCount: 0, tempStatus: "applied",
                 updatedAt: (timestamp) 지금 }
```

- **테스트 전 정리**: 차량 앱이 `test01`을 다시 만들어도(`set`) 하위 `passengers`, `requests` 문서는 남아요. 예전 희망 온도가 중앙값에 섞이지 않게 콘솔에서 지우고 시작해요. 둘이 동시에 테스트할 땐 서로 알려요.
- **B(웹) 단독 테스트**: 콘솔에서 `hvacState` 값을 바꾸면 M07이 따라 바뀌어야 해요. `passengers/front_right`를 직접 만들면 M02에서 조수석이 "사용 중"이 돼야 해요.
- **A(차량) 단독 테스트**
  - 온도: `passengers/rear_left`(`desiredTemp: 22`), `passengers/front_right`(`desiredTemp: 24`) 생성 → 23℃로 바뀌어야 해요.
  - 불편: `requests`에 `{ seat: "rear_left", uid: "console", type: "REPORT_DISCOMFORT", value: "nausea", status: "pending" }` 추가 → 차량 화면 알림이 떠야 해요.

---

## 8. 보안 규칙 초안 (4단계 적용 예정)

Firestore 테스트 모드는 생성 30일 후(12월 중순) 만료돼요. **2차 PT(12/3~4) 전에 적용**해요.

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /sessions/{sid} {
      function signedIn() { return request.auth != null; }
      function carUidOf() {
        return get(/databases/$(database)/documents/sessions/$(sid)).data.carUid;
      }

      allow read: if signedIn();
      // 차량 앱: 자기 uid를 carUid로 넣어서 생성
      allow create: if signedIn() && request.resource.data.carUid == request.auth.uid;
      // 차량 앱만 수정, carUid는 변경 불가
      allow update: if signedIn() && request.auth.uid == resource.data.carUid
                    && request.resource.data.carUid == resource.data.carUid;
      allow delete: if false;

      match /passengers/{seat} {
        allow read: if signedIn();
        // 폰: 자기 uid로만 쓰기 (빈 좌석 생성, 내 좌석 수정)
        allow create: if signedIn() && request.resource.data.uid == request.auth.uid;
        allow update: if signedIn() && resource.data.uid == request.auth.uid
                      && request.resource.data.uid == request.auth.uid;
        allow delete: if signedIn() && resource.data.uid == request.auth.uid;
      }

      match /requests/{rid} {
        allow read: if signedIn();
        // 폰: 자기 uid, pending 상태로만 생성
        allow create: if signedIn() && request.resource.data.uid == request.auth.uid
                      && request.resource.data.status == 'pending';
        // 차량 앱: 상태 변경
        allow update: if signedIn() && request.auth.uid == carUidOf();
        allow delete: if false;
      }
    }
  }
}
```

- 적용 후 테스트: 폰 웹 개발자 도구에서 `hvacState`를 쓰려고 하면 `permission-denied`가 나야 해요.
- 콘솔에서 만든 `test01`을 차량 앱으로 계속 쓰려면 `carUid`를 차량 앱 uid로 바꿔야 해요.

---

## 9. 변경 이력

| 버전 | 날짜 | 내용 |
|---|---|---|
| v0.1 | 2026-11-12 | 초안 |
| v0.2 | 2026-11-12 | 좌석별 공조 제약 반영 |
| v0.3 | 2026-11-13 | 디자인(M01~M09) 반영. 풍량·조명 제외, 희망 온도 중앙값을 실내 전체에 적용(`tempStatus`), 외기는 차량 앱만 제어, QR 1개 + 앱에서 좌석 선택, 이름 입력(M00), 민감도 4단계·시각 케어·휴식 간격, 불편 알리기(`REPORT_DISCOMFORT`) |
| v0.3.1 | 2026-11-13 | `sessions.carUid` 추가 (보안 규칙에서 차량 앱/폰 구별), 보안 규칙 초안 추가 |
