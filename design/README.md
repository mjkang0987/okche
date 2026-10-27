# design — 디자인 토큰

옥체 디자인 시스템의 색·글자·간격·모서리 값을 iOS와 안드로이드가 똑같이 쓰도록 한 곳에서 관리한다.

| 파일 | 내용 |
|---|---|
| `tokens.json` | 값의 기준. 옥체 디자인 시스템(https://claude.ai/artifact/AFcUxgYi9YPd6CUABakcH9)의 `tokens.json`을 그대로 복사한 것 (버전 1792980009-1583) |
| `generate.py` | `tokens.json` → `generated/` 코드 생성 (파이썬 표준 라이브러리만) |
| `test_generate.py` | 생성 스크립트 확인 |
| `generated/OkcheTokens.swift` | SwiftUI용 (`OkcheColor`, `OkcheSpacing`, `OkcheRadius`, `OkcheType`) |
| `generated/OkcheTokens.kt` | Jetpack Compose용 (같은 이름의 `object`) |

## 값 바꾸는 법

1. 디자인 시스템에서 먼저 바꾼다 (디자인 → 코드 순서).
2. 바뀐 `tokens.json`을 이 폴더에 덮어쓴다.
3. 다시 만든다.

```sh
python3 design/generate.py
python3 -m unittest discover -s design
```

`generated/`는 직접 고치지 않는다. 생성 파일이 `tokens.json`과 맞는지만 보려면 `python3 design/generate.py --check` (다르면 종료 코드 1).

## 이름 규칙

토큰 이름 `ink-muted`, `space-4`, `radius-lg`가 Swift에선 `OkcheColor.inkMuted`, `OkcheSpacing.space4`, `OkcheRadius.radiusLg`, Kotlin에선 `OkcheColor.InkMuted`, `OkcheSpacing.Space4`, `OkcheRadius.RadiusLg`가 된다.

토큰 이름은 그대로 코드 이름이 되므로 아래에 맞지 않으면 생성이 멈춘다(종료 코드 2).

- 영어 소문자로 시작하고, 소문자·숫자를 하이픈 하나로 이은 것 (`--`, 끝 하이픈 안 됨)
- Swift 예약어(`default`, `in`, `self` …)가 아닐 것
- 하이픈을 지운 코드 이름이 같은 묶음 안에서 겹치지 않을 것 (`space-1-0`과 `space-10`은 둘 다 `space10`)

## 프로젝트에 넣을 때

- 두 파일은 앱 타깃에 직접 넣는다. Swift 선언은 기본 접근 수준(internal)이라, 별도 모듈로 빼면 `public`이 필요하다.
- Kotlin 패키지는 `app.okche.ui.theme` (앱 ID `app.okche`).

## 글꼴

글자 스타일에는 글꼴 종류만 적는다: `display`(고운바탕 — 화면 제목의 조선식 이름, 아이 이름, 축하 문구)와 `sans`(디자인 기준 Noto Sans KR, 앱에서는 시스템 글꼴을 써도 됨 — 나머지). 주석의 글꼴 이름은 `tokens.json`의 `type.families`에서 가져온다. 실제 글꼴 파일 연결은 iOS·안드로이드 프로젝트를 만드는 단계에서 한다.

## 디자인 가이드 검사 (`canvas-check/`)

디자인 캔버스의 화면이 디자인 시스템 README·`tokens.json` 값을 지키는지 본다. 화면을 고칠 때마다 돌리고, 0이 아니면 고친다.

| 파일 | 하는 일 |
|---|---|
| `guide.js` | 화면을 실제로 그려서 글자 크기·높이·모서리·색을 잰다. 가이드에 없는 값을 화면별로 적고 `guide.json`에 남긴다. |
| `guidefix.py` | 자주 나오는 어긋남을 가이드 값으로 바꾼다(여러 번 돌려도 같은 결과). 캔버스 생성 스크립트를 다시 돌렸다면 마지막에 돌린다. |

```sh
DESIGN=<캔버스 project 폴더>/ RUNTIME=<캔버스 dc-runtime.js> MODULES=<react가 든 node_modules> OUT=<결과 폴더> node design/canvas-check/guide.js
DESIGN=<캔버스 project 폴더>/ python3 design/canvas-check/guidefix.py
```

검사하는 값: 글자 12·14·16·18·22·28·32, 버튼 높이 52·40(아이콘만 있는 버튼 44), 바로 기록 버튼 56 이상, 입력칸·셀렉트 52, 모서리 8·14·20·999, 색은 토큰 15개.

**예외** (가이드를 어긴 게 아니라 일부러 다르게 둔 것):

- 쉬운 말 0.85배 크기(13.6·11.9·10.2 등) — 가이드가 정한 크기
- 그래프 막대·눈금(모서리 3), 홈 화면 위젯 모서리 22(휴대폰이 정함)
- 실록 PDF 미리보기의 작은 글자 — 종이 한 장을 줄여 보여 주는 그림
- 아이 등록의 털색 동그라미 — 실제 털 색
- 체형 사진 찍기 화면의 어두운 색·셔터 — 카메라 화면
- 사진 칸·메모 여러 줄 칸 — 사진 크기·여러 줄이라 높이가 다름
- 온보딩의 큰 '옥체' 글자 — 앱 이름 글자 로고
- 일기 혈당 입력칸 56 — 같은 줄의 기록 버튼(56)과 높이를 맞춤
- 화면 흐름도(Flow)·앱 아이콘 보드 — 앱 화면이 아님
