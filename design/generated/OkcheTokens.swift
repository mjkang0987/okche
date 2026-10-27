// 자동 생성 파일 — 직접 고치지 말 것.
// design/tokens.json을 고친 뒤 `python3 design/generate.py`를 실행한다.
// 원본: 옥체 디자인 토큰 v1

import SwiftUI

enum OkcheColor {
    /// 페이지 바탕(한지). 모든 화면의 기본 배경.
    static let hanji = Color(.sRGB, red: 251 / 255, green: 247 / 255, blue: 239 / 255, opacity: 1) // #FBF7EF
    /// 카드·시트·입력칸 바탕. 한지 위에 한 겹 올린 면.
    static let paper = Color(.sRGB, red: 255 / 255, green: 255 / 255, blue: 255 / 255, opacity: 1) // #FFFFFF
    /// 본문·제목 글자(먹색). hanji 위 14.1:1.
    static let ink = Color(.sRGB, red: 43 / 255, green: 37 / 255, blue: 34 / 255, opacity: 1) // #2B2522
    /// 보조 글자: 쉬운 말 부제, 날짜, 단위. hanji 위 5.4:1.
    static let inkMuted = Color(.sRGB, red: 110 / 255, green: 100 / 255, blue: 94 / 255, opacity: 1) // #6E645E
    /// 카드 구분선·그래프 바닥선 같은 장식용 가는 선(글자·컨트롤 테두리에 쓰지 않음).
    static let line = Color(.sRGB, red: 220 / 255, green: 211 / 255, blue: 198 / 255, opacity: 1) // #DCD3C6
    /// 입력칸·라디오·체크박스 테두리, 포커스 아닌 컨트롤 윤곽. hanji 위 3.9:1.
    static let lineStrong = Color(.sRGB, red: 133 / 255, green: 122 / 255, blue: 112 / 255, opacity: 1) // #857A70
    /// 키컬러(쪽빛 남색): 주요 버튼, 선택된 칩, 탭 활성, 그래프 막대. 흰 글자 8.7:1.
    static let namsaek = Color(.sRGB, red: 47 / 255, green: 75 / 255, blue: 124 / 255, opacity: 1) // #2F4B7C
    /// 현판 면(아이 카드·실록 제목·다음 탕약 같은 강조 카드와 앱 아이콘 바탕), 누른 상태 버튼, namsaek-soft 위 강조 글자(10.3:1).
    static let namsaekDeep = Color(.sRGB, red: 30 / 255, green: 50 / 255, blue: 87 / 255, opacity: 1) // #1E3257
    /// 현판 면(namsaek-deep) 위 보조 글자. 6.4:1.
    static let onDeepMuted = Color(.sRGB, red: 174 / 255, green: 184 / 255, blue: 207 / 255, opacity: 1) // #AEB8CF
    /// 아이 카드·선택 배경 같은 옅은 면. 위 글자는 namsaek 또는 namsaek-deep.
    static let namsaekSoft = Color(.sRGB, red: 225 / 255, green: 231 / 255, blue: 242 / 255, opacity: 1) // #E1E7F2
    /// 금색 포인트: 기념일 배지, 사료·약 바꾼 날 표시선, 축하 장식. 면으로만 쓰고 위 글자는 ink(6.2:1). 흰 글자 금지(2.4:1).
    static let geum = Color(.sRGB, red: 201 / 255, green: 161 / 255, blue: 74 / 255, opacity: 1) // #C9A14A
    /// 금색 계열을 글자로 써야 할 때(표시선 라벨 등). hanji 위 5.4:1.
    static let geumDeep = Color(.sRGB, red: 126 / 255, green: 98 / 255, blue: 34 / 255, opacity: 1) // #7E6222
    /// 현판 면 위 제목·아이 이름 글자(8.1:1), 금박 그러데이션의 밝은 쪽.
    static let geumLight = Color(.sRGB, red: 231 / 255, green: 203 / 255, blue: 138 / 255, opacity: 1) // #E7CB8A
    /// 금박 그러데이션의 어두운 쪽(geum-light → geum → geum-shade, 왼쪽 위에서 오른쪽 아래로). 글자에 단독으로 쓰지 않음.
    static let geumShade = Color(.sRGB, red: 169 / 255, green: 130 / 255, blue: 58 / 255, opacity: 1) // #A9823A
    /// 응급 안내·삭제 확인처럼 꼭 경고해야 하는 글자에만. 키컬러나 장식으로 쓰지 않음. hanji 위 6.1:1.
    static let yeonji = Color(.sRGB, red: 162 / 255, green: 59 / 255, blue: 50 / 255, opacity: 1) // #A23B32
}

enum OkcheSpacing {
    /// 아이콘과 글자 사이.
    static let space1: CGFloat = 4
    /// 칩 사이, 조선식 이름과 쉬운 말 사이.
    static let space2: CGFloat = 8
    /// 목록 항목 안쪽 위아래.
    static let space3: CGFloat = 12
    /// 카드 안쪽 여백, 화면 좌우 여백.
    static let space4: CGFloat = 16
    /// 섹션 사이.
    static let space6: CGFloat = 24
    /// 화면 위 큰 제목 아래.
    static let space8: CGFloat = 32
}

enum OkcheRadius {
    /// 입력칸, 작은 배지.
    static let radiusSm: CGFloat = 8
    /// 버튼, 기록 큰 버튼.
    static let radiusMd: CGFloat = 14
    /// 카드, 바텀시트 위 모서리.
    static let radiusLg: CGFloat = 20
    /// 칩, 기념일 배지.
    static let radiusPill: CGFloat = 999
}

enum OkcheFontFamily {
    /// Gowun Batang — 화면 제목의 조선식 이름, 아이 이름, 축하 문구
    case display
    /// Noto Sans KR — 나머지 전부 (앱에서는 시스템 글꼴을 써도 됨)
    case sans
}

struct OkcheTextStyle {
    let family: OkcheFontFamily
    let size: CGFloat
    let lineHeight: CGFloat
    let weight: Font.Weight
}

enum OkcheType {
    static let display = OkcheTextStyle(family: .display, size: 28, lineHeight: 36, weight: .bold)
    static let title = OkcheTextStyle(family: .display, size: 22, lineHeight: 30, weight: .bold)
    static let heading = OkcheTextStyle(family: .sans, size: 18, lineHeight: 26, weight: .bold)
    static let body = OkcheTextStyle(family: .sans, size: 16, lineHeight: 24, weight: .regular)
    static let label = OkcheTextStyle(family: .sans, size: 14, lineHeight: 20, weight: .medium)
    static let caption = OkcheTextStyle(family: .sans, size: 12, lineHeight: 16, weight: .regular)
    static let number = OkcheTextStyle(family: .sans, size: 32, lineHeight: 38, weight: .bold)
}
