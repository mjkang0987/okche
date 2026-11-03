package app.okche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.okche.R
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
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
fun MoreTab(modifier: Modifier = Modifier, onEditPet: (String) -> Unit = {}) {
    val context = LocalContext.current
    var pets by remember { mutableStateOf(emptyList<Pet>()) }
    LaunchedEffect(Unit) { pets = OkcheDatabase.get(context).dao().pets() }
    TabScreen(stringResource(R.string.tab_more), modifier) {
        // shortcut: 아이 정보로 가는 임시 줄(아이마다 한 줄), 6-3 아이 바꾸기·더보기 화면을 만들 때 디자인 More대로 바꾼다.
        if (pets.isNotEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
                    .padding(horizontal = 14.dp),
            ) {
                pets.forEachIndexed { i, pet ->
                    if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .clickable(role = Role.Button) { onEditPet(pet.id) },
                        horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${pet.name} · 아이 정보", Modifier.weight(1f), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = OkcheColor.LineStrong)
                    }
                }
            }
        }
    }
}
