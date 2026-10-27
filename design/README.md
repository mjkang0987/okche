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
- Kotlin 패키지 `kr.pikaworks.okche.ui.theme`는 앱 ID를 `kr.pikaworks.okche`로 가정한 값이다. 앱 ID가 바뀌면 `generate.py`의 `KOTLIN_PACKAGE`를 고치고 다시 만든다.

## 글꼴

글자 스타일에는 글꼴 종류만 적는다: `display`(고운바탕 — 화면 제목의 조선식 이름, 아이 이름, 축하 문구)와 `sans`(디자인 기준 Noto Sans KR, 앱에서는 시스템 글꼴을 써도 됨 — 나머지). 주석의 글꼴 이름은 `tokens.json`의 `type.families`에서 가져온다. 실제 글꼴 파일 연결은 iOS·안드로이드 프로젝트를 만드는 단계에서 한다.
