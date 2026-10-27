package app.okche.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.okche.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

// 탭 첫 화면들. 내용은 6단계부터 채운다.

@Composable
fun RecordTab(modifier: Modifier = Modifier) {
    // 위 20은 디자인 일기 화면 값(토큰에 20이 없음).
    TabScreen(stringResource(R.string.record_title), modifier, stringResource(R.string.record_subtitle), topPadding = 20.dp)
}

@Composable
fun MainTab(modifier: Modifier = Modifier) {
    // shortcut: 켜 둔 채 날이 바뀌어도 그대로고 하루 기준(새벽 6시)도 아직 안 따름, 6단계 문안 내용에서 고친다.
    // 앱 문구가 한국어뿐이라 날짜도 한국어로(기기 언어가 영어여도).
    val today = LocalDate.now().format(DateTimeFormatter.ofPattern("M월 d일 EEEE", Locale.KOREAN))
    TabScreen(stringResource(R.string.tab_main), modifier, today)
}

@Composable
fun SillokTab(modifier: Modifier = Modifier) {
    // 디자인은 '콩이실록'처럼 아이 이름이 붙는다. 아이가 생기는 6단계에서 바꾼다.
    TabScreen(stringResource(R.string.tab_sillok), modifier)
}

@Composable
fun AlbumTab(modifier: Modifier = Modifier) {
    TabScreen(stringResource(R.string.tab_album), modifier, stringResource(R.string.album_subtitle))
}

@Composable
fun MoreTab(modifier: Modifier = Modifier) {
    TabScreen(stringResource(R.string.tab_more), modifier)
}
