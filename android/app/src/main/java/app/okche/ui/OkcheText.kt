package app.okche.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.AlignmentLine
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import app.okche.R
import app.okche.ui.theme.OkcheFontFamily
import app.okche.ui.theme.OkcheTextStyle

private val GowunBatang = FontFamily(Font(R.font.gowun_batang_bold, FontWeight.Bold))

/** `OkcheType` → Compose 글자 스타일. sp라 시스템 글자 크기를 따른다(안드로이드 14+는 큰 글자를 덜 키움). */
fun OkcheTextStyle.toTextStyle(weight: FontWeight = fontWeight) = TextStyle(
    fontFamily = when (family) {
        OkcheFontFamily.Display -> GowunBatang
        OkcheFontFamily.Sans -> FontFamily.Default // 디자인 README: 앱에서는 시스템 글꼴을 써도 됨
    },
    fontSize = fontSize,
    lineHeight = lineHeight,
    fontWeight = weight,
)

/**
 * 한 줄 글자의 자리를 토큰 줄 높이로 잡는다. Compose는 한 줄을 글꼴 기본 높이(고운바탕 28 → 41dp)보다 작게 만들지 않아,
 * 넘치는 만큼 위아래를 반씩 줄인다 — 웹(디자인) line-height·iOS와 같은 방식. 기준선도 함께 옮겨 밑줄 맞춤이 유지된다.
 * shortcut: 한 줄 전용(maxLines = 1), 여러 줄 고운바탕 글(축하 문구 등)을 만들 때 줄마다 맞추는 방법으로 바꾼다.
 */
fun Modifier.singleLineHeight(lineHeight: TextUnit) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val target = lineHeight.roundToPx()
    val offset = maxOf(0, placeable.height - target) / 2
    val height = placeable.height - offset * 2
    val baseline = placeable[FirstBaseline]
    val lines = if (baseline == AlignmentLine.Unspecified) emptyMap() else mapOf<AlignmentLine, Int>(FirstBaseline to baseline - offset)
    layout(placeable.width, height, lines) { placeable.place(0, -offset) }
}
