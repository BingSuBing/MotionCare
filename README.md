# carfortable (편안타)

차량 동승자를 위한 개인 맞춤형 멀미 완화·이동 케어 서비스
(현대자동차그룹 Pleos 차량용 앱 개발 공모전)

동승자는 앱 설치 없이 **QR 코드 하나로** 차량에 연결해, 자기 자리에서 멀미 상태를 알리고 온도·풍량을 요청할 수 있어요.
차량 앱은 요청을 받아 **Vehicle SDK로 실제 공조를 제어**하고, 경로에 맞춰 휴식 장소를 안내해요.

## 주요 기능
- **QR 좌석 연결**: 중앙 디스플레이의 좌석별 QR을 찍으면 동승자 화면이 바로 열림 (iPhone·Android 모두 지원)
- **좌석별 공조 요청**: 폰에서 온도·풍량 요청 → 차량 앱이 Vehicle SDK로 실제 공조 설정
- **멀미 알림**: 동승자가 "멀미나요" 입력 → 차량 화면 알림 + TTS 안내 + 공조 제안
- **휴식 장소 추천**: 설정한 휴식 간격에 맞춰 경로 주변 휴게소 추천 및 경유지 추가
- **시각적 멀미 완화**: 폰의 가속도·자이로 센서로 차량 움직임을 화면에 표시 (점/수평선 모드)

## 구조

```
[중앙 디스플레이 앱]          [Firebase]              [동승자 폰]
 Pleos Connect (AAOS)   ⇄   Firestore 세션   ⇄   웹앱 (설치 불필요, 기본)
 - Vehicle SDK 공조 제어      익명 로그인            Android 앱 (선택)
 - 내비 / 휴식 장소           Hosting
 - QR 표시 / TTS
```

공조는 **차량 앱만** 실제로 제어해요. 폰은 요청을 Firestore에 기록하고, 차량 앱이 이를 받아 실행한 뒤 실제 상태를 다시 기록해요.

| 모듈 | 설명 |
| --- | --- |
| `app` | Pleos Connect(AAOS) 중앙 디스플레이 앱 (가로) |
| `mobile` | QR로 연동하는 동승자 Android 앱 (세로, 선택) |
| `shared` | 공통 데이터 모델 및 연동 코드 |
| `web` | QR로 연동하는 동승자 웹앱 (iPhone·Android, 설치 불필요) *(예정)* |
| `docs` | 기획서, 데이터 구조, 스크린샷 |
| `apk` | 제출용 APK |

## 기술 스택
| 구분 | 사용 기술 |
| --- | --- |
| 차량 앱 | Kotlin, Jetpack Compose, AAOS |
| Pleos SDK | Vehicle (`ai.pleos.playground:Vehicle:2.3.2`), NaviHelper, TTS *(예정)* |
| 동승자 웹 | React, TypeScript, Vite *(예정)* |
| 백엔드 | Firebase Firestore, Anonymous Auth, Hosting |
| 기타 | ZXing (QR 생성), 카카오 로컬 API (휴게소 검색, 예정) |

## 개발 환경
- Android Studio, Kotlin 2.2.21 (K2 모드)
- minSdk 28
- Pleos Connect v2.1.0 Emulator (API 34)

## 실행 방법
(추후 작성)

### 동승자 웹 (`web`)
(추후 작성)

## 진행 상황
- [x] Pleos 에뮬레이터 환경 구성
- [x] Vehicle SDK 연동 (도어 상태 조회)
- [x] Firebase 연동 (익명 로그인, Firestore 쓰기)
- [ ] HVAC 온도 조회·설정
- [ ] 세션 생성 + 좌석별 QR 표시
- [ ] 동승자 웹: QR 접속 → 온도 요청
- [ ] 내비 경로 연동 및 휴게소 추천
- [ ] 멀미 알림 + TTS
- [ ] 시각적 멀미 완화 모드

## 시연 영상
(추후 추가)
