# 편안타 (Pyeonanta)

## 1. 앱 이름 및 한 줄 소개

**편안타** — 편안타는 모션 케어 기능으로 차량 동승자의 멀미 완화를 돕고, 탑승자 개개인의 선호를 반영한 실내 환경 조율과 맞춤형 휴식 서비스를 제공하는 차량용 동승자 케어 애플리케이션입니다.


## 2. APK 파일 위치 / GitHub Release

| 항목 | 위치 |
| --- | --- |
| 차량 앱 APK | [`apk/carfortable-car.apk`](apk/carfortable-car.apk) |
| GitHub Release | https://github.com/BingSuBing/MotionCare/releases |
| 동승자 웹 (설치 불필요) | https://motioncare-fec56.web.app |
| 시연 영상 | https://youtu.be/MuWwv1rr6QY?si=FVjcQIrYka3o3r8s |

## 3. 테스트 환경

| 구분 | 버전 / 규격 |
| --- | --- |
| 에뮬레이터 | **Pleos Connect v2.1.0 Emulator** (Android Automotive, API 34, x86_64) |
| 화면 규격 | **2560 × 1440 px, 160 dpi (mdpi), 가로(landscape)** |
| Virtual Console | Pleos Connect Virtual Console 2.1.0 이상 (공조 전원·값 조작) |
| 차량 앱 | minSdk 28, targetSdk 35, compileSdk 36 |
| Pleos SDK | Vehicle 2.3.2 (공조), NaviHelper 3.0.5 (위치·경로·경유지) |
| 개발 도구 | Android Studio, Kotlin 2.2.21 (K2 모드) |
| 동승자 | 스마트폰(iPhone·Android)의 Chrome·Safari 최신 버전, 또는 PC 브라우저 |

## 4. 필요한 설정

- **인터넷 연결**: Firebase 세션, 지도, 휴식처 검색에 필요해요.
- **공조(에어컨) 전원 켜기 (필수)**: 에뮬레이터는 공조가 꺼진 상태로 시작해요. 꺼져 있으면 편안타가 온도를 바꿀 수 없어서, 앱 실행 전에 Virtual Console에서 켜야 해요. (아래 5-2)
- **동승자 기기**: 스마트폰 1대 이상. 없으면 PC 브라우저 시크릿 창으로 대신할 수 있고, 여러 명은 시크릿 창을 여러 개 열어 각각 참여해요.
- 소스에서 빌드할 때만 필요한 설정은 [개발용 빌드](#개발용-빌드)를 참고해요.

## 5. 실행 방법

### 5-1. 에뮬레이터 켜기

1. Android Studio → **Device Manager** → **Pleos Connect v2.1.0** ▶ 실행
2. 차량 화면이 다 뜰 때까지 기다려요. 화면이 멈추거나 이상하면 Device Manager → ⋮ → **Cold Boot Now**

### 5-2. Virtual Console 열고 공조 켜기 (필수)

1. 터미널에서 포트를 연결해요.
   ```bash
   # macOS
   ~/Library/Android/sdk/platform-tools/adb forward tcp:8088 tcp:8088
   # Windows (PowerShell)
   & "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" forward tcp:8088 tcp:8088
   ```
2. 브라우저에서 http://127.0.0.1:8088 을 열면 Pleos Connect Virtual Console이 떠요.
3. 아래쪽 **CLIMATE** 영역에서 **Power** 버튼을 눌러 켜요.
4. CLIMATE 오른쪽 위가 "ON / 23.5° / 23.5°"처럼 바뀌고, 에뮬레이터 화면 맨 아래 양쪽의 "꺼짐"이 온도 숫자로 바뀌면 실행 준비가 됩니다.

### 5-3. 편안타 앱 설치·실행

```bash
adb install -r apk/carfortable-car.apk
```

- 에뮬레이터 창에 APK 파일을 끌어다 놓아도 설치돼요.
- 에뮬레이터 아래 ⊞(앱 목록) → **MotionCare**를 실행해요.
- 첫 화면에 **QR 코드와 6자리 참여 코드**가 나와요. (release APK는 실행할 때마다 새 코드를 만들어요)

> 같은 패키지의 다른 빌드(debug)가 이미 설치돼 있으면 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`이 나요. `adb uninstall com.carfortable` 후 다시 설치해요.

### 5-4. 동승자 참여 (폰)

1. 폰 기본 카메라로 차량 화면의 QR을 찍어요. 폰이 없으면 PC 브라우저에서 `https://motioncare-fec56.web.app/join?session=<참여 코드>`로 접속해요.
2. 이름 → 좌석 → 멀미 정도 → 실내 취향(희망 온도 등)을 입력하고 제출해요.
3. 차량 화면의 탑승자 목록에 이름과 ✓가 표시돼요.

### 5-5. 실행 흐름

1. 차량: **[모두의 편안함 시작하기 →]**
2. **추천 온도**: 화면 위 "탑승자 추천 온도 ○℃로 맞출까요?" → **[적용]** → 에뮬레이터 아래 온도와 Virtual Console의 DRIVER/PASSENGER 온도가 같이 바뀌어요.
3. **불편 알림**: 폰 [불편해요] → "메스꺼워요" → 차량에 알림 + 추천 온도 재계산. **[외기로 바꾸고 확인]**을 누르면 외기 유입으로 전환돼요.
4. **휴식처**: 폰 "잠깐 쉬고 싶어요" → 차량 **[휴식처 찾기]** → 목록에서 **[경유지로 추가]** → Pleos 내비가 해당 휴게소로 안내하고, 폰에 휴게소 이름과 거리가 떠요.
5. **탑승자 탭**: 이름 수정·연결 해제 / **실내 조율 탭**: 탑승자별 희망 온도와 현재 온도
6. 상단 **[여정 종료]** → 폰에 "이동이 끝났어요", 차량은 첫 QR 화면으로 돌아가요.

### 문제 해결

| 증상 | 해결 |
| --- | --- |
| 에뮬레이터 아래 온도가 "꺼짐" | Virtual Console CLIMATE에서 Power 켜기 (5-2) |
| [적용]을 눌러도 온도가 안 바뀜 | 위와 같음. 그래도 안 되면 Cold Boot 후 5-2부터 다시 |
| http://127.0.0.1:8088 이 안 열림 | 에뮬레이터를 켠 상태에서 `adb forward` 명령을 다시 실행 |
| APK 설치 시 `Can't find service: package` | 에뮬레이터가 덜 켜진 상태이므로 잠시 기다리거나 Cold Boot 후 다시 설치 |
| Pleos 내비 화면에 배경 지도가 안 보임 | 에뮬레이터 내비 앱의 한계이다. 경로선·안내는 정상이고, 편안타 앱 안의 지도는 정상 표시 |

---

## 개발용 빌드

### `local.properties` (아래 요소들을 채운 뒤 실행해야 함)

| 키 | 필수 | 설명 |
| --- | --- | --- |
| `devSessionId` | 선택 | debug 빌드의 개인 세션 ID. 시작할 때 그 세션의 예전 `passengers`, `requests`를 자동으로 비워요. 없으면 공용 `test01` |
| `kakaoRestKey` | 선택 | debug 빌드의 카카오 로컬 REST API 키 (휴게소 검색). 없어도 빌드되고, 휴식처 검색만 최근 결과·내비 검색으로 대신해요 |
| `kakaoRestKeyRelease` | 선택 | release 빌드(공개 APK)용 키. 비우면 키 없이 빌드돼요.|

### 차량 앱 (`app`)

1. Android Studio에서 프로젝트를 열고 Gradle Sync를 해요.
2. 실행 대상을 Pleos Connect 에뮬레이터로 선택하고 ▶ **Run**.
3. release APK: **Build → Generate Signed App Bundle / APK → APK → release** → `apk/carfortable-car.apk`로 복사

### 동승자 웹 (`web`)

```bash
cd web
npm install
npm run dev          # http://localhost:5173/join?session=test01
```

- 배포: `npm --prefix web run build` → `firebase deploy --only hosting`
- 컴포넌트 미리보기: `http://localhost:5173/dev/ui`

> `test01`은 공용 세션이라 차량 앱이 다시 만들어도 하위 문서가 남으므로 가능하다면 개인 세션 ID로 만들어진 QR로 접속해주세요.

---

## 주요 기능

- **QR 하나로 연결**: 차 1대에 QR 1개. 찍으면 동승자 웹이 열리고, 이름 입력 후 **웹에서 좌석을 선택**해요. 회원가입 없이 익명 로그인으로 폰마다 구별해요.
- **멀미를 배려한 추천 온도**: 각자의 희망 온도(18~26℃)를 **멀미 민감도 가중평균**으로 합쳐요. 멀미가 심한 사람일수록 결과가 그 사람이 원한 차 내 환경에 가까워져요. 희망 온도 변경·불편 요청 때 바로 다시 가중평균을 계산하고, **운전자가 [적용]을 눌러야** 운전석·조수석 두 구역에 적용돼요. ([알고리즘](docs/temp-recommendation.md))
- **불편 알리기**: 메스꺼워요 / 너무 추워요 / 너무 더워요 / 바람이 너무 세요 / 잠깐 쉬고 싶어요 → 모든 차량 화면 위에 알림. 메스꺼움을 알리면 그 사람의 멀미 가중치가 올라가요. 또한, 기본적으로 30분마다 새로운 가중치로 갱신합니다.
- **환기 제안**: 메스꺼워요 + 내기 순환 중이면 [외기로 바꾸고 확인]을 원터치로 제안해요.
- **휴식처 찾기·경유지 추가**: 잠깐 쉬고 싶어요 → 카카오 로컬로 20km 안 휴게소·졸음쉼터 검색 → 지도에 표시 → Pleos 내비에 경유지 추가. 폰에는 "○○휴게소 · 약 N분 거리"가 떠요.
- **시각 케어**: 폰 센서로 차의 움직임을 화면 가장자리의 점·수평선·빛으로 보여줘요. 편안타 앱 안에서 사용자가 다운로드 한 영상을 보면서도 사용할 수 있어요.
- **탑승자 관리**: 운전자가 탑승자별 민감도·희망 온도를 보고, 이름 수정·연결 해제, 여정 종료를 해요.

> 에뮬레이터에서 확인한 공조 제약(뒷좌석 전용 온도 구역 없음)에 맞춰 **실내 전체 온도 + 외기/내기**를 제어해요.

## 구조

```
[중앙 디스플레이 앱]               [Firebase]              [동승자 폰]
 Pleos Connect (AAOS)       ⇄   Firestore 세션    ⇄   웹앱 (설치 불필요)
 - 세션 생성, QR 표시            익명 로그인            - 이름·좌석 선택
 - 추천 온도 계산 → 운전자 확인   Hosting                - 희망 온도·멀미 정도
 - 공조·외기/내기 (Vehicle SDK)                          - 불편 알리기, 시각 케어
 - 휴게소 검색 → 경유지 (NaviHelper)
```

공조는 **차량 앱만** 실제로 제어해요. 폰은 희망 온도와 요청을 Firestore에 기록하고, 차량 앱이 이를 처리한 뒤 실제 상태(`hvacState`, `route`)를 다시 기록해요.

```
sessions/{sessionId}            ← 차량 앱 (carUid, driverName, carNumber, hvacState, route)
├─ passengers/{seat}            ← 폰 (이름, 희망 온도, 민감도, 시각 케어, 휴식 간격)
└─ requests/{requestId}         ← 폰이 생성, 차량 앱이 처리 (불편 알리기)
```

데이터 구조의 기준 문서는 [`docs/firestore-schema.md`](docs/firestore-schema.md)예요.

| 모듈 | 설명 |
| --- | --- |
| `app` | Pleos Connect(AAOS) 중앙 디스플레이 앱 (가로) |
| `shared` | 공용 데이터 모델, 추천 온도 알고리즘(+ 단위 테스트) |
| `web` | 동승자 웹앱 (React, 세로, 설치 불필요) |
| `docs` | 데이터 구조, 추천 온도 알고리즘, 웹 화면 명세 |
| `apk` | 제출용 APK |

## 기술 스택

| 구분 | 사용 기술 |
| --- | --- |
| 차량 앱 | Kotlin, Jetpack Compose, Android Automotive |
| Pleos SDK | Vehicle 2.3.2 (HVAC 온도·외기/내기·전원), NaviHelper 3.0.5 (현재 위치·경로·경유지) |
| 지도·장소 | MapLibre + OpenFreeMap (지도), 카카오 로컬 API (휴게소 검색) |
| 기타 | ZXing (QR 생성) |
| 동승자 웹 | React 19, TypeScript, Vite, React Router, lucide-react |
| 백엔드 | Firebase Firestore, Anonymous Auth, Hosting |


> 현대자동차그룹 Pleos 차량용 앱 개발 공모전 출품작입니다.