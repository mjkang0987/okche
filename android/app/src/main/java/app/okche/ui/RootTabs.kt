package app.okche.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.okche.R
import app.okche.store.OkcheDatabase
import app.okche.store.lastPetId
import app.okche.store.setLastPetId
import app.okche.ui.pet.PetEditFlow
import app.okche.ui.pet.PetManageSection
import app.okche.ui.pet.PetRegisterScreen
import app.okche.ui.pet.PetSeal
import app.okche.ui.pet.PetSwitchSheet
import app.okche.ui.pet.currentPet
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { OkcheDatabase.get(context).dao() }
    // 아이·휴양·지금 보는 아이는 바뀌는 대로 따라간다(6-3). 아이와 lastPetId를 둘 다 읽기 전(null)에는
    // 낙관 자리와 아이 관리를 비워 둔다(첫 아이가 잠깐 보였다 바뀌거나 '아직 모신 아이가 없어요'가 스치지 않게).
    val loaded by remember { combine(dao.petsFlow(), context.lastPetId) { p, id -> p to id } }.collectAsState(null)
    val pets = loaded?.first
    val rests by remember { dao.openRestPeriodsFlow() }.collectAsState(emptyList())
    val restingIds = rests.mapTo(mutableSetOf()) { it.petId }
    val current = loaded?.let { (p, id) -> currentPet(p, id) }
    // 탭 아래 화면(하단 탭 없음 — 디자인 '화면 짜임새'): 아이 정보(6-2), 아이 등록(6-3).
    var editPetId by rememberSaveable { mutableStateOf<String?>(null) }
    var registering by rememberSaveable { mutableStateOf(false) }
    var switching by rememberSaveable { mutableStateOf(false) }
    editPetId?.let { id ->
        PetEditFlow(id, onClose = { editPetId = null })
        return
    }
    if (registering) {
        PetRegisterScreen(onClose = { registering = false }, onSaved = { registering = false })
        return
    }
    if (switching && pets != null) {
        PetSwitchSheet(
            pets, current, restingIds,
            onPick = { id ->
                switching = false
                scope.launch { context.setLastPetId(id) }
            },
            onEdit = {
                switching = false
                editPetId = current?.id
            },
            onRegister = {
                switching = false
                registering = true
            },
            onDismiss = { switching = false },
        )
    }
    val seal: @Composable () -> Unit = {
        if (pets != null) PetSeal(current, onSwitch = { switching = true }, onRegister = { registering = true })
    }
    Scaffold(
        containerColor = OkcheColor.Hanji,
        bottomBar = {
            // 디자인의 탭 막대: 흰 바탕, 위 가는 선(line), 선택 알약 표시 없음.
            NavigationBar(
                Modifier.drawWithContent {
                    drawContent()
                    val stroke = 1.dp.toPx()
                    drawLine(OkcheColor.Line, Offset(0f, stroke / 2), Offset(size.width, stroke / 2), stroke)
                },
                containerColor = OkcheColor.Paper,
                tonalElevation = 0.dp,
            ) {
                Tab.entries.forEachIndexed { index, tab ->
                    val isSelected = index == selected
                    // 키보드로 옮겨 갈 때 포커스가 잘 보이게(알약 표시를 없애 기본 포커스 표시는 대비가 낮음).
                    val interaction = remember { MutableInteractionSource() }
                    val focused by interaction.collectIsFocusedAsState()
                    NavigationBarItem(
                        modifier = if (focused) {
                            Modifier.border(2.dp, OkcheColor.Namsaek, RoundedCornerShape(OkcheRadius.RadiusMd))
                        } else {
                            Modifier
                        },
                        interactionSource = interaction,
                        selected = isSelected,
                        onClick = { selected = index },
                        icon = { Icon(tab.icon, contentDescription = null) },
                        label = {
                            // 큰 글자에서도 한 줄(낱말 중간에서 안 끊김), 칸보다 길면 글자를 줄인다.
                            // 칸 높이는 줄 높이로 고정 — 글자가 줄어도 아이콘이 다른 탭과 같은 높이에 있게.
                            val style = OkcheType.Caption.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal)
                            Box(Modifier.height(with(LocalDensity.current) { style.lineHeight.toDp() }), contentAlignment = Alignment.Center) {
                                Text(
                                    stringResource(tab.label),
                                    style = style,
                                    softWrap = false,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    autoSize = TextAutoSize.StepBased(minFontSize = style.fontSize * 0.6f, maxFontSize = style.fontSize),
                                )
                            }
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
            Tab.Record -> RecordTab(modifier, seal)
            Tab.Main -> MainTab(modifier, seal)
            Tab.Sillok -> SillokTab(modifier, current?.name, seal)
            Tab.Album -> AlbumTab(modifier, seal)
            Tab.More -> MoreTab(modifier) {
                if (pets != null) PetManageSection(pets, restingIds, onEditPet = { editPetId = it }, onRegister = { registering = true })
            }
        }
    }
}
