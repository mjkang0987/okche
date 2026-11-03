package app.okche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType

/** 탭 첫 화면 틀: 제목 28 + 쉬운 말 14(디자인 '화면 짜임새' 규칙), 한지 바탕, 좌우 16. 머리 오른쪽 자리 [trailing]에 낙관형 표시(6-3). iOS TabScreen과 같음. */
@Composable
fun TabScreen(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {},
    content: @Composable () -> Unit = {},
) {
    // 위 여백은 다섯 탭 모두 24(일기 보드 20 대신 — 2026-11-04 사용자).
    Column(
        modifier
            .fillMaxSize()
            .background(OkcheColor.Hanji)
            .verticalScroll(rememberScrollState())
            .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space6, bottom = OkcheSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
    ) {
        // 머리 오른쪽 자리가 한 줄에 안 들어가면 제목 아래로 내려간다.
        // 제목 첫 줄은 언제나 위에서 4(낙관 44 가운데) — 낙관이 없거나 쉬운 말이 아래 줄로 내려가도 탭을 오갈 때 제목이 튀지 않게(2026-11-04 사용자).
        FlowRow(
            Modifier.fillMaxWidth().heightIn(min = 44.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            itemVerticalAlignment = Alignment.Top,
        ) {
            // 큰 글자에서 한 줄에 안 들어가면 쉬운 말이 아래 줄로 내려간다. TalkBack은 한 머리말로 읽는다.
            // 낙관 옆 남는 폭만 쓰게 해서 쉬운 말이 먼저 내려가고, 제목도 안 들어갈 때만 낙관이 아래 줄로.
            FlowRow(
                Modifier.weight(1f, fill = false).padding(top = 4.dp, end = OkcheSpacing.Space3).semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
                verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
            ) {
                // 제목은 낱말 중간에서 끊지 않고, 너무 길면 한 줄 안에서 글자를 줄인다(iOS minimumScaleFactor 0.5와 같음).
                val display = OkcheType.Display.toTextStyle()
                Text(
                    title,
                    Modifier.testTag("tabTitle").alignByBaseline().singleLineHeight(display.lineHeight),
                    color = OkcheColor.Ink,
                    style = display,
                    softWrap = false,
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
            trailing()
        }
        content()
    }
}
