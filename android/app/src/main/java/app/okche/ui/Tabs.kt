package app.okche.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.okche.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 탭 첫 화면들. 내용은 6단계부터 채운다. 일기·문안·실록·어진첩 머리 오른쪽에 낙관형 표시(6-3, [seal]).

@Composable
fun MainTab(modifier: Modifier = Modifier, seal: @Composable () -> Unit = {}) {
    // shortcut: 켜 둔 채 날이 바뀌어도 그대로고 하루 기준(새벽 6시)도 아직 안 따름, 6단계 문안 내용에서 고친다.
    // 앱 문구가 한국어뿐이라 날짜도 한국어로(기기 언어가 영어여도).
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    TabScreen(stringResource(R.string.tab_main), modifier, today, trailing = seal)
}

/** '콩이실록'(이름 + 실록, 호칭 없음 — 보드 Sillok). 아이가 없으면 '실록'. */
@Composable
fun SillokTab(modifier: Modifier = Modifier, petName: String? = null, seal: @Composable () -> Unit = {}) {
    TabScreen(petName?.let { "${it}실록" } ?: stringResource(R.string.tab_sillok), modifier, trailing = seal)
}

@Composable
fun AlbumTab(modifier: Modifier = Modifier, seal: @Composable () -> Unit = {}) {
    TabScreen(stringResource(R.string.tab_album), modifier, stringResource(R.string.album_subtitle), trailing = seal)
}

/** 더보기. 지금은 아이 관리(6-3)만 — 다른 줄(사료·다과·탕약·약방·등사·백업·의서·기별·결제)은 각 단계에서. */
@Composable
fun MoreTab(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    TabScreen(stringResource(R.string.tab_more), modifier, content = content)
}
