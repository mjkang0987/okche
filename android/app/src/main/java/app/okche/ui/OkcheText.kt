package app.okche.ui

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
