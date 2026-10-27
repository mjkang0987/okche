// 자동 생성 파일 — 직접 고치지 말 것.
// design/tokens.json을 고친 뒤 `python3 design/generate.py`를 실행한다.
// 원본: 옥체 디자인 토큰 v1

package kr.pikaworks.okche.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object OkcheColor {
    // 페이지 바탕(한지). 모든 화면의 기본 배경.
    val Hanji = Color(0xFFFBF7EF)
    // 카드·시트·입력칸 바탕. 한지 위에 한 겹 올린 면.
    val Paper = Color(0xFFFFFFFF)
    // 본문·제목 글자(먹색). hanji 위 14.1:1.
    val Ink = Color(0xFF2B2522)
    // 보조 글자: 쉬운 말 부제, 날짜, 단위. hanji 위 5.4:1.
    val InkMuted = Color(0xFF6E645E)
    // 카드 구분선·그래프 바닥선 같은 장식용 가는 선(글자·컨트롤 테두리에 쓰지 않음).
    val Line = Color(0xFFDCD3C6)
    // 입력칸·라디오·체크박스 테두리, 포커스 아닌 컨트롤 윤곽. hanji 위 3.9:1.
    val LineStrong = Color(0xFF857A70)
    // 키컬러(쪽빛 남색): 주요 버튼, 선택된 칩, 탭 활성, 그래프 막대. 흰 글자 8.7:1.
    val Namsaek = Color(0xFF2F4B7C)
    // 현판 면(아이 카드·실록 제목·다음 탕약 같은 강조 카드와 앱 아이콘 바탕), 누른 상태 버튼, namsaek-soft 위 강조 글자(10.3:1).
    val NamsaekDeep = Color(0xFF1E3257)
    // 현판 면(namsaek-deep) 위 보조 글자. 6.4:1.
    val OnDeepMuted = Color(0xFFAEB8CF)
    // 아이 카드·선택 배경 같은 옅은 면. 위 글자는 namsaek 또는 namsaek-deep.
    val NamsaekSoft = Color(0xFFE1E7F2)
    // 금색 포인트: 기념일 배지, 사료·약 바꾼 날 표시선, 축하 장식. 면으로만 쓰고 위 글자는 ink(6.2:1). 흰 글자 금지(2.4:1).
    val Geum = Color(0xFFC9A14A)
    // 금색 계열을 글자로 써야 할 때(표시선 라벨 등). hanji 위 5.4:1.
    val GeumDeep = Color(0xFF7E6222)
    // 현판 면 위 제목·아이 이름 글자(8.1:1), 금박 그러데이션의 밝은 쪽.
    val GeumLight = Color(0xFFE7CB8A)
    // 금박 그러데이션의 어두운 쪽(geum-light → geum → geum-shade, 왼쪽 위에서 오른쪽 아래로). 글자에 단독으로 쓰지 않음.
    val GeumShade = Color(0xFFA9823A)
    // 응급 안내·삭제 확인처럼 꼭 경고해야 하는 글자에만. 키컬러나 장식으로 쓰지 않음. hanji 위 6.1:1.
    val Yeonji = Color(0xFFA23B32)
}

object OkcheSpacing {
    // 아이콘과 글자 사이.
    val Space1 = 4.dp
    // 칩 사이, 조선식 이름과 쉬운 말 사이.
    val Space2 = 8.dp
    // 목록 항목 안쪽 위아래.
    val Space3 = 12.dp
    // 카드 안쪽 여백, 화면 좌우 여백.
    val Space4 = 16.dp
    // 섹션 사이.
    val Space6 = 24.dp
    // 화면 위 큰 제목 아래.
    val Space8 = 32.dp
}

object OkcheRadius {
    // 입력칸, 작은 배지.
    val RadiusSm = 8.dp
    // 버튼, 기록 큰 버튼.
    val RadiusMd = 14.dp
    // 카드, 바텀시트 위 모서리.
    val RadiusLg = 20.dp
    // 칩, 기념일 배지.
    val RadiusPill = 999.dp
}

enum class OkcheFontFamily {
    // 고운바탕 — 화면 제목의 조선식 이름, 아이 이름, 축하 문구
    Display,
    // 시스템 글꼴 — 나머지 전부
    Sans,
}

data class OkcheTextStyle(
    val family: OkcheFontFamily,
    val fontSize: TextUnit,
    val lineHeight: TextUnit,
    val fontWeight: FontWeight,
)

object OkcheType {
    val Display = OkcheTextStyle(OkcheFontFamily.Display, 28.sp, 36.sp, FontWeight.W700)
    val Title = OkcheTextStyle(OkcheFontFamily.Display, 22.sp, 30.sp, FontWeight.W700)
    val Heading = OkcheTextStyle(OkcheFontFamily.Sans, 18.sp, 26.sp, FontWeight.W700)
    val Body = OkcheTextStyle(OkcheFontFamily.Sans, 16.sp, 24.sp, FontWeight.W400)
    val Label = OkcheTextStyle(OkcheFontFamily.Sans, 14.sp, 20.sp, FontWeight.W500)
    val Caption = OkcheTextStyle(OkcheFontFamily.Sans, 12.sp, 16.sp, FontWeight.W400)
    val Number = OkcheTextStyle(OkcheFontFamily.Sans, 32.sp, 38.sp, FontWeight.W700)
}
