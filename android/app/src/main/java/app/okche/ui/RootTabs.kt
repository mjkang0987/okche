package app.okche.ui

import android.widget.Toast
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.layout.onSizeChanged
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
import app.okche.store.setOnboardingDone
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import app.okche.ui.food.FoodChangeSheet
import app.okche.ui.food.FoodMenuRow
import app.okche.ui.main.MainTab
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.pet.MemorialScreen
import app.okche.ui.pet.PetEditFlow
import app.okche.ui.pet.PetManageSection
import app.okche.ui.pet.PetRegisterScreen
import app.okche.ui.pet.PetSeal
import app.okche.ui.pet.PetSwitchSheet
import app.okche.ui.record.RecordLogScreen
import app.okche.ui.record.RecordTab
import app.okche.ui.record.SuraScreen
import app.okche.ui.record.rememberQuickRecordState
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

/** 하단 탭 5개 + 앱 하나의 되돌리기 알림 자리(탭 막대가 보이면 그 위, 없으면 화면 아래). */
@Composable
fun RootTabs() {
    var item by remember { mutableStateOf<UndoToastItem?>(null) }
    var toastHeight by remember { mutableStateOf(0.dp) }
    val barHeight = remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val view = androidx.compose.ui.platform.LocalView.current
    val appScope = rememberCoroutineScope()
    val toasts = remember { UndoToasts({ item = it }, appScope, view) }
    androidx.compose.runtime.CompositionLocalProvider(LocalUndoToasts provides toasts, LocalUndoInset provides if (item != null) toastHeight else 0.dp) {
        Box(Modifier.fillMaxSize()) {
            RootTabsContent(barHeight)
            UndoToast(
                item, onDismiss = { item = null }, onUndo = { item?.undo?.invoke() },
                modifier = Modifier.align(Alignment.BottomCenter)
                    .then(if (barHeight.value > 0.dp) Modifier.padding(bottom = barHeight.value) else Modifier.navigationBarsPadding())
                    .onSizeChanged { toastHeight = with(density) { it.height.toDp() } },
            )
        }
    }
}

/** 하단 탭 5개. 순서는 디자인 기준(일기 → 문안 → 실록 → 어진첩 → 더보기), 첫 화면은 일기. */
@Composable
private fun RootTabsContent(barHeight: androidx.compose.runtime.MutableState<androidx.compose.ui.unit.Dp>) {
    var selected by rememberSaveable { mutableIntStateOf(0) }
    val density = LocalDensity.current
    val toasts = LocalUndoToasts.current
    // 다른 탭으로 가면 옛 되돌리기를 남기지 않음.
    androidx.compose.runtime.LaunchedEffect(selected) { toasts?.show(null) }
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
    var logOpen by rememberSaveable { mutableStateOf(false) }
    var suraOpen by rememberSaveable { mutableStateOf(false) }
    /** 기록 줄 연필로 연 아이 — 지금 보는 아이가 그 아이로 바뀌기 전엔 '볼 아이 없음'으로 닫지 않게. */
    var suraFor by rememberSaveable { mutableStateOf<String?>(null) }
    var foodPetId by rememberSaveable { mutableStateOf<String?>(null) }
    var snackListOpen by rememberSaveable { mutableStateOf(false) }
    var yakbangOpen by rememberSaveable { mutableStateOf(false) }
    var tangyakOpen by rememberSaveable { mutableStateOf(false) }
    var medAddOpen by rememberSaveable { mutableStateOf(false) }
    var medEditId by rememberSaveable { mutableStateOf<String?>(null) }
    val quickState = rememberQuickRecordState()
    // 이 기기만의 설정 쓰기(DataStore): 실패하면 말없이 넘기지 않고 알린다.
    fun saveSetting(block: suspend () -> Unit) {
        scope.launch {
            runCatching { block() }.onFailure { Toast.makeText(context, "저장하지 못했어요. 다시 해 주세요.", Toast.LENGTH_SHORT).show() }
        }
    }
    val switchSheet: @Composable () -> Unit = {
        if (switching && pets != null) {
            PetSwitchSheet(
                pets, current, restingIds,
                onPick = { id ->
                    switching = false
                    saveSetting { context.setLastPetId(id) }
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
    }
    editPetId?.let { id ->
        PetEditFlow(
            id,
            onClose = { editPetId = null },
            // 간직하면 문안의 추억 화면(지금 보는 아이로), 지우면 더보기 — 마지막 아이를 지워도 시작하기로 돌아가지 않게(6-4).
            // 수라·기록 목록 위에서 열었으면 그 화면도 닫는다(볼 아이가 없는 빈 화면으로 돌아가지 않게).
            onKept = {
                // 지금 보는 아이를 먼저 바꾼 뒤 문안으로(다른 아이의 문안이 잠깐 보이지 않게).
                scope.launch {
                    runCatching { context.setLastPetId(id) }.onFailure { Toast.makeText(context, "저장하지 못했어요. 다시 해 주세요.", Toast.LENGTH_SHORT).show() }
                    editPetId = null
                    suraOpen = false
                    logOpen = false
                    selected = Tab.Main.ordinal
                }
            },
            onDeleted = {
                editPetId = null
                suraOpen = false
                logOpen = false
                selected = Tab.More.ordinal
                saveSetting { context.setOnboardingDone(true) }
            },
        )
        return
    }
    if (registering) {
        PetRegisterScreen(onClose = { registering = false }, onSaved = { registering = false })
        return
    }
    if (medEditId != null && pets != null) {
        app.okche.ui.med.MedEditScreen(pets, medEditId!!) { medEditId = null }
        return
    }
    if (medAddOpen && pets != null) {
        app.okche.ui.med.MedAddScreen(pets, current, onBack = { medAddOpen = false })
        return
    }
    if (tangyakOpen && pets != null) {
        app.okche.ui.med.TangyakScreen(pets, seal = { PetSeal(current, onSwitch = { switching = true }, onRegister = { registering = true }) },
            onAdd = { medAddOpen = true }, onEdit = { medEditId = it }) { tangyakOpen = false }
        switchSheet()
        return
    }
    if (yakbangOpen && pets != null) {
        app.okche.ui.med.YakbangScreen(pets, seal = { PetSeal(current, onSwitch = { switching = true }, onRegister = { registering = true }) }) { yakbangOpen = false }
        switchSheet()
        return
    }
    if (snackListOpen) {
        app.okche.ui.food.SnackListScreen(current, seal = { if (pets != null) PetSeal(current, onSwitch = { switching = true }, onRegister = { registering = true }) }) { snackListOpen = false }
        switchSheet()
        return
    }
    if (suraOpen) {
        SuraScreen(current, openedFor = suraFor, seal = { if (pets != null) PetSeal(current, onSwitch = { switching = true }, onRegister = { registering = true }) }, onBack = { suraOpen = false })
        switchSheet()
        return
    }
    // 아이 목록을 읽은 뒤에 그린다(다시 살아난 직후 '누군지 모름'이 스치지 않게).
    if (logOpen && pets != null) {
        RecordLogScreen(pets, current, onBack = { logOpen = false }, onOpenSura = { id -> saveSetting { context.setLastPetId(id) }; suraFor = id; suraOpen = true })
        return
    }
    switchSheet()
    val seal: @Composable () -> Unit = {
        if (pets != null) PetSeal(current, onSwitch = { switching = true }, onRegister = { registering = true })
    }
    Scaffold(
        containerColor = OkcheColor.Hanji,
        bottomBar = {
            // 디자인의 탭 막대: 흰 바탕, 위 가는 선(line), 선택 알약 표시 없음.
            // 알림이 탭 막대 위에 뜨게 높이를 알림 자리에 알림(탭 막대가 없는 화면이면 0).
            androidx.compose.runtime.DisposableEffect(Unit) { onDispose { barHeight.value = 0.dp } }
            NavigationBar(
                Modifier.onSizeChanged { barHeight.value = with(density) { it.height.toDp() } }.drawWithContent {
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
            Tab.Record -> RecordTab(
                modifier, seal, pets, current,
                onPickPet = { id -> saveSetting { context.setLastPetId(id) } },
                onOpenLog = { logOpen = true },
                onOpenSura = { id ->
                    if (id != null) saveSetting { context.setLastPetId(id) }
                    suraFor = id
                    suraOpen = true
                },
                state = quickState,
            )
            Tab.Main -> if (current?.memorializedAt != null) {
                MemorialScreen(current, modifier, seal)
            } else {
                MainTab(
                    pets, current, modifier, seal,
                    onRecord = { selected = Tab.Record.ordinal },
                    onRegister = { registering = true },
                    onOpenSura = { id -> suraFor = id; suraOpen = true },
                )
            }
            Tab.Sillok -> SillokTab(modifier, current?.name, seal)
            Tab.Album -> AlbumTab(modifier, seal)
            Tab.More -> MoreTab(modifier) {
                if (pets != null) PetManageSection(pets, restingIds, onEditPet = { editPetId = it }, onRegister = { registering = true })
                // 더보기 메뉴(보드 More) — 다른 줄(다과·탕약·약방·등사·백업·의서·기별·결제)은 각 단계에서.
                // 아이가 없거나 지금 보는 아이가 추억이면 사료 줄 없음(바꿀 아이가 없음). 다과는 모든 아이 공통이라 늘 있음.
                val foodPet = current?.takeIf { it.memorializedAt == null }
                Column(
                    Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(horizontal = OkcheSpacing.Space4)
                        .semantics { isTraversalGroup = true },
                ) {
                    if (foodPet != null) {
                        FoodMenuRow(foodPet) { foodPetId = foodPet.id }
                        androidx.compose.material3.HorizontalDivider(color = OkcheColor.Line)
                    }
                    app.okche.ui.food.MoreMenuRow("다과", note = "간식 목록") { snackListOpen = true }
                    androidx.compose.material3.HorizontalDivider(color = OkcheColor.Line)
                    app.okche.ui.food.MoreMenuRow("탕약", note = "약 알림") { tangyakOpen = true }
                    androidx.compose.material3.HorizontalDivider(color = OkcheColor.Line)
                    app.okche.ui.food.MoreMenuRow("약방", note = "약 목록") { yakbangOpen = true }
                }
                if (foodPet != null && foodPetId == foodPet.id) FoodChangeSheet(foodPet) { foodPetId = null }
            }
        }
    }
}
