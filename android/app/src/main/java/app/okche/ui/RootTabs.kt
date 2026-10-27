package app.okche.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.okche.R
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheType

private enum class Tab(val label: Int, val icon: ImageVector) {
    Record(R.string.tab_record, Icons.Outlined.Description),
    Main(R.string.tab_main, Icons.Outlined.FavoriteBorder),
    Sillok(R.string.tab_sillok, Icons.Outlined.BarChart),
    Album(R.string.tab_album, Icons.Outlined.Image),
    More(R.string.tab_more, Icons.Outlined.MoreHoriz),
}

/** 하단 탭 5개. 순서는 디자인 기준(일기 → 문안 → 실록 → 어진첩 → 더보기), 첫 화면은 일기. */
@Composable
fun RootTabs() {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        containerColor = OkcheColor.Hanji,
        bottomBar = {
            // 디자인의 탭 막대: 흰 바탕, 위 가는 선(line), 선택 알약 표시 없음.
            NavigationBar(
                Modifier.drawBehind { drawLine(OkcheColor.Line, Offset.Zero, Offset(size.width, 0f), 1.dp.toPx()) },
                containerColor = OkcheColor.Paper,
                tonalElevation = 0.dp,
            ) {
                Tab.entries.forEachIndexed { index, tab ->
                    val isSelected = index == selected
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            // 큰 글자에서도 한 줄(낱말 중간에서 안 끊김), 칸보다 길면 글자를 줄인다.
                            val style = OkcheType.Caption.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            val color = LocalContentColor.current
                            BasicText(
                                stringResource(tab.label),
                                style = style,
                                color = { color },
                                softWrap = false,
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(maxFontSize = style.fontSize),
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OkcheColor.Namsaek,
                            selectedTextColor = OkcheColor.Namsaek,
                            unselectedIconColor = OkcheColor.InkMuted,
                            unselectedTextColor = OkcheColor.InkMuted,
                            indicatorColor = Color.Transparent,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        val modifier = Modifier.padding(padding)
        when (Tab.entries[selected]) {
            Tab.Record -> RecordTab(modifier)
            Tab.Main -> MainTab(modifier)
            Tab.Sillok -> SillokTab(modifier)
            Tab.Album -> AlbumTab(modifier)
            Tab.More -> MoreTab(modifier)
        }
    }
}
