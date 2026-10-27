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

토큰 이름 `ink-muted`, `space-4`, `radius-lg`가 Swift에선 `OkcheColor.inkMuted`, `OkcheSpacing.space4`, `OkcheRadius.radiusLg`, Kotlin에선 `OkcheColor.InkMuted`, `OkcheSpacing.Space4`, `OkcheRadius.RadiusLg`가 된다. 이름은 영어 소문자·숫자·하이픈만 받는다 (다른 글자가 있으면 생성이 멈춘다).

## 글꼴

글자 스타일에는 글꼴 종류만 적는다: `display`(고운바탕 — 화면 제목의 조선식 이름, 아이 이름, 축하 문구)와 `sans`(시스템 글꼴 — 나머지). 실제 글꼴 파일 연결은 iOS·안드로이드 프로젝트를 만드는 단계에서 한다.
