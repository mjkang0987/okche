package app.okche.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.okche.R

// 탭 첫 화면들. 내용은 6단계부터 채운다(문안은 `main/MainScreen.kt`). 일기·문안·실록·어진첩 머리 오른쪽에 낙관형 표시(6-3, [seal]).

/** '콩이실록'(이름 + 실록, 호칭 없음 — 보드 Sillok). 아이가 없으면 '실록'. */
@Composable
fun SillokTab(modifier: Modifier = Modifier, petName: String? = null, seal: @Composable () -> Unit = {}) {
    TabScreen(petName?.let { "${it}실록" } ?: stringResource(R.string.tab_sillok), modifier, trailing = seal)
}

@Composable
fun AlbumTab(modifier: Modifier = Modifier, seal: @Composable () -> Unit = {}) {
    TabScreen(stringResource(R.string.tab_album), modifier, stringResource(R.string.album_subtitle), trailing = seal)
}

/** 더보기: 아이 관리(6-3) + 메뉴 줄(RootTabs). */
@Composable
fun MoreTab(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    TabScreen(stringResource(R.string.tab_more), modifier, content = content)
}
