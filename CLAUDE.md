# MotionCare (편안타) — Claude 작업 안내

현대자동차그룹 차량용 앱 공모전 출품작이에요. 차량 동승자의 멀미 완화·이동 케어 서비스예요.
2인 팀: **A = 차량 앱(Kotlin, AAOS/Pleos)**, **B = 웹 + Firebase(TypeScript)**. 이 저장소에서 Claude는 주로 **B의 웹(`/web`)** 작업을 도와요.

## 꼭 먼저 읽을 문서

| 문서 | 내용 |
|---|---|
| `docs/firestore-schema.md` | **데이터 구조의 유일한 기준 (v0.3)**. 필드 이름·값은 여기 적힌 그대로 써요 |
| `docs/web-screens.md` | 동승자 웹 화면(M00~M09) 명세: 경로, 읽고 쓰는 데이터, 피그마와 다르게 구현할 부분 |
| `docs/design/mobile/*.png` | 피그마 화면 캡처 (M01~M09, UI Kit). **화면을 만들 때 반드시 해당 PNG를 열어 보고** 레이아웃을 맞춰요 |

## 서비스 구조

```
[차량 앱 (AAOS)] ⇄ [Firestore sessions/{id}] ⇄ [동승자 폰 웹앱 (React, Firebase Hosting)]
```

- 공조는 **차량 앱만** 실제로 제어해요. 폰은 Firestore에 희망 온도·요청을 쓰기만 해요.
- 온도: 동승자 희망 온도(`passengers/{seat}.desiredTemp`)의 **중앙값**을 차량 앱이 실내 전체에 적용해요.
- 외기/내기: 폰에 버튼이 없어요. 불편 요청(답답해요/메스꺼워요)이 오면 차량 앱이 운전자에게 제안해요.
- **풍량·바람·조명 기능은 없어요.** 피그마에 남아 있어도 구현하지 않아요.
- QR은 차 1대에 1개: `/join?session=<id>`. 좌석은 웹에서 고르고, 사람은 **익명 로그인 uid**로 구별해요.
- 공용 Kotlin 모델: `shared/src/main/java/com/carfortable/shared/FirestoreModels.kt` (패키지 `com.carfortable.shared`). 웹 `types.ts`와 1:1로 맞춰요.
- 배포 주소: `https://motioncare-fec56.web.app` (QR: `https://motioncare-fec56.web.app/join?session=<id>`)

## 웹 기술 스택과 폴더

- React 19 + Vite + TypeScript, react-router-dom, Firebase(Auth 익명 로그인, Firestore), lucide-react 아이콘
- 배포: Firebase Hosting (`firebase.json`의 public = `web/dist`, SPA rewrite)

```
web/src/
├ firebase.ts          ← auth, db 내보내기 (수정 불필요)
├ types.ts             ← 스키마 v0.3 타입·문구·헬퍼 (medianTemp, cabinTemp, fmtTemp, paths 등)
├ styles/tokens.css    ← 편안타 UI Kit 토큰 (색·글꼴·간격·둥글기)
├ styles/ui.css        ← 공통 컴포넌트 스타일
├ components/ui/       ← Screen, TopBar, ProgressBar, PageHeading, Button, Card, StatusPill,
│                         Seal, OptionRow, Chip, Segmented, Toggle, TempStepper, Notice
├ hooks/               ← useAnonAuth, useSession 등 Firebase 훅
└ pages/               ← 화면 (UiPreview = /dev/ui 개발용 미리보기)
```

## 코딩 규칙

1. **스키마 우선**: Firestore 필드는 `types.ts`의 타입과 `paths` 헬퍼로만 접근해요. 문자열 경로를 직접 쓰지 않아요. 스키마를 바꿔야 하면 코드를 고치지 말고 **먼저 사용자에게 물어봐요** (A의 차량 앱과 맞춰야 해요).
2. **디자인 우선**: 새 화면은 `components/ui`의 공통 컴포넌트로 조립해요. 색·간격은 `tokens.css` 변수만 써요 (hex 직접 사용 금지). 필요한 컴포넌트가 없으면 `components/ui`에 추가하고 `/dev/ui`에도 예시를 넣어요.
3. 화면 문구는 피그마 PNG를 그대로 따르되, `docs/web-screens.md`의 "피그마와 다른 점"을 반영해요. 사람 이름("수빈", "건호")은 하드코딩하지 말고 데이터에서 가져와요.
4. 모바일 세로(폭 360~430px) 기준. 버튼 높이 58px, 탭 영역 넉넉하게.
5. 시간은 `serverTimestamp()`로 써요. 좌석 등록은 `runTransaction`으로 해요.
6. 로딩·세션 없음·이동 종료(`active === false`)·오류 상태를 `Notice`로 항상 처리해요.
7. 한국어 UI. 코드 주석도 한국어로 짧게.

## 작업 절차 (매번)

1. `git checkout dev && git pull origin dev` 후 `git checkout -b feature/<작업이름>` (이미 해당 브랜치면 생략)
2. 관련 문서와 PNG를 읽고, 짧은 구현 계획을 먼저 말해요.
3. 구현
4. **검증**: `npm --prefix web run build`와 `npm --prefix web run lint`가 에러 없이 통과해야 해요. 실패하면 고쳐요.
5. 커밋 메시지는 `feat: ...`, `fix: ...`, `docs: ...` 형식으로 짧게, 한국어로.
6. `git push -u origin <브랜치>` 후, **PR 제목과 본문(마크다운)**을 출력해요. base는 항상 `dev`.
7. 마지막에 사용자가 직접 확인할 것(브라우저 주소, Firebase 콘솔에서 바꿔 볼 값)을 짧게 알려줘요.

## 하지 말 것

- `main` 브랜치에 직접 커밋·푸시 금지. PR은 항상 `dev`로.
- `firebase deploy`는 사용자가 요청할 때만.
- Firestore 보안 규칙(`firestore.rules`)과 Firebase 콘솔 설정은 건드리지 않아요 (4단계 작업).
- `app/`, `mobile/`(A의 Kotlin 코드)는 수정하지 않아요. `shared/`의 `FirestoreModels.kt`는 사용자가 승인한 스키마 변경(`[schema]` PR)에서만 `types.ts`와 함께 고쳐요.
- `local.properties`, 키 파일은 열거나 커밋하지 않아요.

## 테스트 환경

- 개발용 고정 세션: `test01` → `http://localhost:5173/join?session=test01`
- 차량 앱도 `test01`을 써요. 테스트 전 콘솔에서 `sessions/test01/passengers`, `requests`의 예전 문서를 지우고, 동시에 테스트할 땐 서로 알려요.
- 차량 앱 없이 테스트: Firebase 콘솔에서 `sessions/test01/hvacState` 값을 직접 바꿔 보면 웹이 따라 바뀌어야 해요.
- 폰 여러 대 흉내: 시크릿 창을 여러 개 열면 각각 다른 익명 uid가 생겨요.
