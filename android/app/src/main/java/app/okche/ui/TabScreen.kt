package app.okche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType

/** 탭 첫 화면 틀: 제목 28 + 쉬운 말 14(디자인 '화면 짜임새' 규칙), 한지 바탕, 좌우 16. iOS TabScreen과 같음. */
@Composable
fun TabScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    // 화면 위 여백. 디자인은 탭 첫 화면 24, 일기만 20.
    topPadding: Dp = OkcheSpacing.Space6,
    content: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .background(OkcheColor.Hanji)
            .verticalScroll(rememberScrollState())
            .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = topPadding, bottom = OkcheSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
    ) {
        // 큰 글자에서 한 줄에 안 들어가면 쉬운 말이 아래 줄로 내려간다. TalkBack은 한 머리말로 읽는다.
        FlowRow(
            Modifier.semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
        ) {
            // 제목은 낱말 중간에서 끊지 않고, 너무 길면 한 줄 안에서 글자를 줄인다(iOS minimumScaleFactor 0.5와 같음).
            val display = OkcheType.Display.toTextStyle()
            BasicText(
                title,
                Modifier.testTag("tabTitle").alignByBaseline().singleLineHeight(display.lineHeight),
                style = display.copy(color = OkcheColor.Ink),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                autoSize = TextAutoSize.StepBased(minFontSize = display.fontSize / 2, maxFontSize = display.fontSize),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    Modifier.alignByBaseline(),
                    style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                    color = OkcheColor.InkMuted,
                )
            }
        }
        content()
    }
}
