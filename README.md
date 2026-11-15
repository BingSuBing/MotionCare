# carfortable (편안타)

차량 동승자를 위한 개인 맞춤형 멀미 완화·이동 케어 서비스
(현대자동차그룹 Pleos 차량용 앱 개발 공모전)

동승자는 앱 설치 없이 **QR 코드 하나로** 차량에 연결해, 자기 자리에서 희망 온도와 취향을 알리고 불편할 때 가볍게 요청할 수 있어요.
차량 앱은 모든 동승자의 **희망 온도 중앙값**을 계산해 **Vehicle SDK로 실제 공조를 제어**하고, 불편 요청을 운전자에게 알려요.

- 동승자 웹: https://motioncare-fec56.web.app
- QR 주소 형식: `https://motioncare-fec56.web.app/join?session=<세션ID>`

## 주요 기능

- **QR 하나로 연결**: 중앙 디스플레이의 QR(차 1대에 1개)을 찍으면 동승자 웹이 열려요. 이름을 입력하고 **웹에서 좌석을 선택**해요. 회원가입 없이 익명 로그인으로 폰마다 구별해요. (iPhone·Android 모두 지원)
- **모두의 희망 온도**: 동승자가 각자 희망 온도(18~26℃)를 정하면, 차량 앱이 **중앙값**을 계산해 운전석·조수석 두 구역에 똑같이 적용해요. 뒷좌석 전용 구역이 없어서 실내 전체를 한 온도로 맞춰요.
- **외기/내기 전환**: "답답해요", "메스꺼워요" 요청이 오면 차량 앱이 운전자에게 환기(외기 유입)를 제안해요.
- **불편 알리기**: 메스꺼워요 / 너무 추워요 / 너무 더워요 / 답답해요 / 잠깐 쉬고 싶어요 → 차량 화면 알림, 운전자가 확인하면 폰에 "운전자 확인됨" 표시
- **케어 프로필**: 멀미 민감도(4단계), 시각 케어 방식(점 / 수평선 / 최소 신호), 정기 휴식 알림(30·60·90분)
- **휴식 장소 추천** *(예정)*: 휴식 간격과 경로에 맞춰 휴게소 추천 및 경유지 추가
- **시각적 멀미 완화** *(예정)*: 폰의 가속도·자이로 센서로 차량 움직임을 화면 가장자리에 표시

> 풍량·바람 방향·조명은 다루지 않아요. 에뮬레이터에서 확인한 차량 공조 제약에 맞춰 **온도 + 외기/내기**에 집중해요.

## 구조

```
[중앙 디스플레이 앱]            [Firebase]               [동승자 폰]
 Pleos Connect (AAOS)    ⇄   Firestore 세션    ⇄   웹앱 (설치 불필요)
 - 세션 생성, QR 표시          익명 로그인             - 이름·좌석 선택
 - 희망 온도 중앙값 → 공조     Hosting                 - 희망 온도·취향 입력
 - 외기/내기, 불편 요청 처리                            - 불편 알리기
```

공조는 **차량 앱만** 실제로 제어해요. 폰은 희망 온도와 요청을 Firestore에 기록하고, 차량 앱이 이를 받아 실행한 뒤 실제 상태(`hvacState`)를 다시 기록해요.

```
sessions/{sessionId}            ← 차량 앱이 생성 (carUid, tripTitle, driverName, hvacState)
├─ passengers/{seat}            ← 폰이 기록 (이름, 희망 온도, 민감도, 시각 케어, 휴식 간격)
└─ requests/{requestId}         ← 폰이 생성, 차량 앱이 처리 (불편 알리기)
```

데이터 구조의 기준 문서는 [`docs/firestore-schema.md`](docs/firestore-schema.md)예요.

| 모듈 | 설명 |
| --- | --- |
| `app` | Pleos Connect(AAOS) 중앙 디스플레이 앱 (가로) |
| `shared` | 공용 데이터 모델 (`com.carfortable.shared.FirestoreModels`) |
| `web` | 동승자 웹앱 (React, 세로, 설치 불필요) |
| `mobile` | 동승자 Android 앱 (선택) |
| `docs` | 데이터 구조, 웹 화면 명세, 디자인 캡처 |
| `apk` | 제출용 APK |

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 차량 앱 | Kotlin, Jetpack Compose, AAOS |
| Pleos SDK | Vehicle (`ai.pleos.playground:Vehicle:2.3.2`, HVAC 온도·외기/내기·전원), NaviHelper·TTS *(예정)* |
| 동승자 웹 | React 19, TypeScript, Vite, React Router, lucide-react |
| 백엔드 | Firebase Firestore, Anonymous Auth, Hosting |
| 기타 | ZXing (QR 생성, 예정), 카카오 로컬 API (휴게소 검색), Pleos NaviHelper (경로·경유지), MapLibre + OpenFreeMap (지도) |

## 개발 환경

- Android Studio, Kotlin 2.2.21 (K2 모드)
- minSdk 28
- Pleos Connect v2.1.0 Emulator (API 34)
- Node.js 20.19 이상 (웹)

## 실행 방법

### 차량 앱 (`app`)

1. Android Studio에서 프로젝트를 열고, Pleos Connect 에뮬레이터를 실행해요.
2. 에뮬레이터를 새로 켰다면 Virtual Console을 한 번 열어 공조 값을 채워요.
   ```bash
   adb forward tcp:8088 tcp:8088
   # 브라우저에서 http://127.0.0.1:8088
   ```
3. `app` 모듈을 실행하면 세션(debug 빌드는 `test01`)이 만들어지고 QR 주소가 화면에 표시돼요.
   - 혼자 테스트할 땐 `local.properties`에 `devSessionId=subin01`처럼 개인 세션 ID를 적어요. 시작할 때 그 세션의 예전 `passengers`, `requests`를 자동으로 비워요.
   - 휴식처 검색에는 카카오 REST API 키가 필요해요. `local.properties`에 `kakaoRestKey=…`(debug용)를 적어요. 키가 없어도 빌드는 되고, 휴식처 검색만 최근 결과나 내비 검색으로 대신해요.
   - release 빌드(공개 APK)는 `kakaoRestKeyRelease`만 써요. 공개 APK의 키는 누구나 꺼낼 수 있으니, 개발용과 다른 키를 발급하고 카카오 콘솔에서 허용 IP·사용량을 제한한 뒤 넣어요. 제출 후에는 재발급해요.
   - release 빌드(제출용 APK)는 실행할 때마다 무작위 6자리 세션 ID를 만들어요.

> 온도가 안 바뀌면: 하단 바가 꺼짐이면 공조 전원이 꺼진 상태예요. 앱도 하단 바도 반응이 없으면 에뮬레이터를 Cold Boot 해요.

### 동승자 웹 (`web`)

```bash
cd web
npm install
npm run dev
```

- 로컬: `http://localhost:5173/join?session=test01`
- 배포: `npm --prefix web run build` → `firebase deploy --only hosting`
- 개발용 컴포넌트 미리보기: `http://localhost:5173/dev/ui`

> 테스트 전에 Firebase 콘솔에서 `sessions/test01/passengers`, `requests`의 예전 문서를 지우고 시작해요. 차량 앱이 세션을 다시 만들어도 하위 문서는 남아요.

## 진행 상황

- [x] Pleos 에뮬레이터 환경 구성
- [x] Vehicle SDK 연동 (도어 상태 조회)
- [x] Firebase 연동 (익명 로그인, Firestore 쓰기)
- [x] HVAC 온도 조회·설정 (운전석·조수석 두 구역, 공조 전원 자동 켜기, 실제 값 확인)
- [x] 세션 생성 (`carUid` 기록)
- [x] 희망 온도 중앙값 → 차량 공조 적용
- [x] 동승자 웹: QR 접속 → 이름·좌석 선택 → 취향 입력·제출 → 이동 중 홈
- [x] 동승자 웹: 불편 알리기 → 전달 완료
- [x] 차량 앱: 불편 요청 처리 (알림, 운전자 확인)
- [x] 차량 앱: 피그마 가로 화면 + QR 표시 + 외기/내기 토글
- [x] 실제 기기 통합 테스트, 1차 시연 영상
- [x] 내비 경로 연동 및 휴게소 추천
- [ ] 멀미 알림 TTS
- [x] 시각적 멀미 완화 모드
- [x] Firestore 보안 규칙 적용

## 팀

| 역할 | 담당 |
| --- | --- |
| 차량 앱 (Kotlin, Pleos SDK) | [@babypotato129](https://github.com/babypotato129) |
| 동승자 웹, Firebase, 데이터 구조 | [@leesubeeeeen](https://github.com/leesubeeeeen) |

## 시연 영상

(추후 추가)
