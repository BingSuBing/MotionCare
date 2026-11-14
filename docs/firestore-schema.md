# Firestore 스키마 (MotionCare / 편안타)

> 이 문서가 **기준**이에요. 차량 앱(Kotlin, `shared/src/main/java/com/carfortable/shared/FirestoreModels.kt`)과 웹(TS, `web/src/types.ts`)은 여기 적힌 이름을 그대로 써요.
> 필드 이름이나 값을 바꿀 때는 **이 문서부터 고치고**, PR 제목에 `[schema]`를 붙여요.

- 버전: **v0.3.3** (작성: 이수빈) — 추천 온도 알고리즘(30분마다 가중치 재계산, 운전자 확인 후 적용)
- 공조 범위: **온도(추천 온도: 멀미 가중평균, 운전자 확인 후 적용) + 외기/내기 전환**. 풍량·바람 방향·조명은 다루지 않아요.

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
QR 스캔 → 이름 입력 → 초대 받기
       → [처음 설정 1/3] 좌석 → [2/3] 멀미 정도 → [3/3] 희망 온도·풍량·휴식 알림 → 제출 완료
       → 메인(이동 중) ⇄ 시각 케어 설정 / 불편 알리기 → 전달 완료
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

### 3.2 온도: 추천 온도 (30분마다 가중치를 바꿔 다시 계산)

**한 줄 요약**: 차량 앱이 **30분마다** 탑승자별 가중치를 다시 정하고, 희망 온도의 **가중평균**으로 추천 온도를 계산해요. 멀미를 많이 느끼는 사람일수록 가중치가 커서 추천 온도가 그 사람 쪽으로 더 가까워져요. 적용은 **운전자가 확인한 뒤에** 해요.

1. 동승자는 **희망 온도**(`passengers/{seat}.desiredTemp`, 18~26℃, 1℃ 단위)를 정해요. 메인에서도 바로 바꿀 수 있어요.
2. **계산은 차량 앱만** 해요 (웹은 결과만 읽어요). 코드: `shared/.../TempRecommendation.kt`의 `recommendTemp()`
3. **가중치** (회차마다 다시 계산)

   | 항목 | 가중치 |
   |---|---|
   | 멀미 민감도 `sensitivity` | 없음 1 / 낮음 1.5 / 보통 2 / **높음 3** (미입력 1) |
   | 추가 항목 `sicknessTriggers` | 1개당 +0.25 |
   | 최근 30분 "메스꺼워요" | 1회당 +2 (최대 +4) |
   | 최근 30분 "잠깐 쉬고 싶어요" | +1 |

   - 최근 30분 "너무 추워요"면 그 사람의 희망 온도를 +1℃, "너무 더워요"면 −1℃로 보고 계산해요 (가장 마지막 요청만).
4. **가중평균**: 각자의 희망 온도에 가중치를 곱해서 더한 뒤, 가중치 합으로 나눠요. 가중치가 모두 같으면 일반 평균과 같아요.
   - 예: 현아(높음, 3) 22℃, 윤지(없음, 1) 25℃, 성윤(없음, 1) 26℃
     → (22×3 + 25×1 + 26×1) ÷ 5 = 23.4 → **23.5℃** (그냥 평균 24.3℃보다 현아 쪽)
   - 예: 현아(보통 2 + 최근 메스꺼워요 2 = 4) 20℃, 윤지(높음, 3) 25℃
     → (20×4 + 25×3) ÷ 7 = 22.1 → **22℃**
5. **안전장치**: 0.5℃ 단위로 반올림하고 18~26℃ 안에서만 추천해요. 한 번에 **최대 2℃까지만** 바꿔요 (예: 지금 26℃, 계산 20℃ → 이번엔 24℃).
6. **언제 계산하나**
   - **정기**: 30분마다 (`nextReviewAt`)
   - **즉시**: 탑승자가 처음 제출했을 때, "메스꺼워요"가 들어왔을 때
7. **운전자 확인** (`TempApprovalSync`)
   - 지금 온도와 0.5℃ 이상 다르면 `medianTemp`에 추천 온도, `tempStatus: "pending"` 기록 → 차량 화면에 [적용] / [나중에]
   - [적용] → 운전석·조수석 두 구역 적용 → `"applied"`
   - [나중에] → 공조 그대로 → `"driver_set"` (다음 회차에 다시 계산)
   - 차이가 0.5℃ 미만이면 제안하지 않고 `reviewedAt`, `nextReviewAt`만 갱신
8. 가중치 숫자는 `RecommendRule`에 모여 있어서 시연하면서 쉽게 조정할 수 있어요.

### 3.3 외기/내기

- **폰에는 외기 버튼이 없어요.** 차량 앱(운전자)만 바꿔요.
- 동승자가 "메스꺼워요"를 보내면, 차량 앱이 운전자에게 **외기 전환을 원터치로 제안**해요.
- 현재 상태는 `hvacState.airIntake`에 기록하고, 폰 M07에서 "외기 유입 중 / 내기 순환 중"으로 보여줘요.

---

## 4. `sessions/{sessionId}`

| 필드 | 타입 | 쓰는 쪽 | 설명 |
|---|---|---|---|
| `carUid` | string | 차량 | 세션을 만든 **차량 앱의 익명 uid**. 생성 시 한 번만 쓰고 바꾸지 않아요. 보안 규칙에서 차량 앱인지 확인할 때 써요 |
| `createdAt` | Timestamp | 차량 | 세션 생성 시각 |
| `active` | boolean | 차량 | 이동 중이면 `true`, 끝나면 `false`. 웹은 `false`면 "이동이 끝났어요" 화면을 보여줘요 |
| `tripTitle` | string | 차량 | M01 카드 제목. 예: "우리 가족 · 서울 → 강릉" |
| `driverName` | string | 차량 | "재현 님의 초대를 받았어요", 좌석 화면 운전석 칸 |
| `carNumber` | string \| 없음 | 차량 | 차량 번호. 예: "136가9762". "136가9762의 편안타에 참여할게요" (없으면 생략) |
| `hvacState` | map | 차량 | 현재 공조 상태 (아래 4.1) |
| `route` | map \| 없음 | 차량 | 2단계. 경로 정보 (아래 4.2) |

### 4.1 `hvacState`

| 필드 | 타입 | 설명 |
|---|---|---|
| `driverTemp` | number | 운전석 구역 실제 온도(℃), SDK에서 읽은 값 |
| `passengerTemp` | number | 조수석 구역 실제 온도(℃), SDK에서 읽은 값 |
| `airIntake` | string | `outside`(외기 유입) \| `recirc`(내기 순환), SDK에서 읽은 값 |
| `medianTemp` | number \| null | **추천 온도** (가중평균, 3.2). 이름은 호환을 위해 그대로 둬요 |
| `voteCount` | number | 계산에 쓴 희망 온도 개수 |
| `leadSeat` | string \| null | 이번 회차 가중치가 가장 큰 좌석 (단독 1위일 때만). 웹: "현아 님의 멀미를 더 고려했어요" |
| `reviewedAt` | Timestamp \| null | 마지막 계산 시각 |
| `nextReviewAt` | Timestamp \| null | 다음 정기 계산 시각 (보통 30분 뒤). 웹: "다음 조정 11:30" |
| `tempStatus` | string | `pending`(운전자 확인 대기) \| `applied`(적용됨) \| `driver_set`(운전자가 보류·직접 설정) |
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
| `sensitivity` | string \| null | `none` \| `low` \| `medium` \| `high` | 설정 2/3 | 없음 / 낮음 / 보통 / 높음 |
| `sicknessTriggers` | string[] | `video`, `screen` | 설정 2/3 | "영상 시청 시 멀미가 잘 나요", "차량 이동 중 화면 보기 불편해요" |
| `desiredTemp` | number \| null | 18~26, 1℃ 단위 | 설정 3/3, 메인 | 희망 온도. `null`이면 아직 안 정함. 메인에서 바로 바꿀 수 있어요 |
| `fanPreference` | number \| null | 1 \| 2 \| 3 | 설정 3/3 | 희망 풍량(1단·약하게 / 2단 / 3단). **차량을 직접 제어하지 않고** 취향 조율(가중치) 참고용 |
| `avoidDirectWind` | boolean \| null | | 설정 3/3 | "직접 바람은 싫어요"면 `true`, "괜찮아요"면 `false` (참고용) |
| `visualMode` | string | `off` \| `dot` \| `horizon` \| `minimal` | 시각 케어 | 사용 안 함 / 움직이는 점 / 수평선 / 최소 시각 신호. **처음 설정에서 빠지고 메인에서 켜요** (기본 `off`) |
| `visualIntensity` | number | 0~100 | 시각 케어 | 시각 안내 강도(%), 기본 30 |
| `restIntervalMin` | number \| null | 30 \| 60 \| 90 | 설정 3/3, 메인 | 정기 휴식 알림 간격. `null`이면 꺼짐 |
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
- 기본값: `sensitivity: null`, `sicknessTriggers: []`, `desiredTemp: null`, `fanPreference: null`, `avoidDirectWind: null`, `visualMode: "off"`, `visualIntensity: 30`, `restIntervalMin: 60`, `submittedAt: null`

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
| `windy` | 바람이 너무 세요 | 알림 (운전자가 풍량·방향을 직접 조정) |
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
  driverName : "재현"
  carNumber  : "136가9762"
  hvacState  : { driverTemp: 22, passengerTemp: 22, airIntake: "recirc",
                 medianTemp: null, voteCount: 0, tempStatus: "applied",
                 updatedAt: (timestamp) 지금 }
```

- **테스트 전 정리**: 차량 앱이 `test01`을 다시 만들어도(`set`) 하위 `passengers`, `requests` 문서는 남아요. 예전 희망 온도가 중앙값에 섞이지 않게 콘솔에서 지우고 시작해요. 둘이 동시에 테스트할 땐 서로 알려요.
- **B(웹) 단독 테스트**: 콘솔에서 `hvacState` 값을 바꾸면 M07이 따라 바뀌어야 해요. `passengers/front_right`를 직접 만들면 M02에서 조수석이 "사용 중"이 돼야 해요.
- **추천 온도 테스트**: 콘솔에서 `passengers`에 민감도와 희망 온도가 다른 문서 2~3개를 만들고, 차량 앱에서 즉시 계산(또는 30분 대기) → 차량 화면에 확인 요청이 떠야 해요.
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
        // 본인 또는 차량(운전자가 내보내기)
        allow delete: if signedIn() && (resource.data.uid == request.auth.uid
                      || request.auth.uid == carUidOf());
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
| v0.3.2 | 2026-11-14 | 모바일 디자인 수정 반영: `sessions.carNumber`, `passengers.fanPreference`·`avoidDirectWind` 추가(참고용), `visualMode` 기본 `off`(시각 케어 분리), 불편 종류 `stuffy` → `windy`(바람이 너무 세요) |
| v0.3.3 | 2026-11-14 | 추천 온도: 30분마다 가중치 재계산(멀미가 큰 사람에게 큰 가중치), 가중평균, 최대 2℃ 변화, 운전자 확인 후 적용. `hvacState.leadSeat`·`reviewedAt`·`nextReviewAt` 추가. 보안 규칙: 차량이 탑승자 삭제 가능 |
