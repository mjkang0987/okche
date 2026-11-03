package app.okche.ui.record

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.okche.R
import app.okche.store.OkcheDao
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheConfirmDialog
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.TabScreen
import app.okche.ui.pet.FormHeader
import app.okche.ui.pet.RadioRow
import app.okche.ui.pet.orderedPets
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch

// 일기 탭 화면(plan.md 7-1, 보드 Record·Log·LogEmpty·Assign·DeleteConfirm). iOS `RecordViews.swift`와 같은 짜임.

/**
 * 일기 탭 첫 화면: '누구 기록인가요?' 칩, 바로 기록 줄, 방금 남긴 기록. 탕약·옥체·사진·⋯ 자세히는 그 단계에서.
 * [pets]가 null이면 아직 못 읽음(칩을 비워 둔다).
 */
@Composable
fun RecordTab(
    modifier: Modifier,
    seal: @Composable () -> Unit,
    pets: List<Pet>?,
    current: Pet?,
    onPickPet: (String) -> Unit,
    onOpenLog: () -> Unit,
    state: QuickRecordState,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    var unknownPicked by state.unknownPicked
    var justSaved by state.justSaved
    var failed by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<DetailTarget?>(null) }
    // 지금 보는 아이가 칩에 없으면(추억·아이 없음) '누군지 몰라요'(2026-11-03 사용자).
    val who = current?.takeIf { !unknownPicked && it.memorializedAt == null }?.id
    // 위 20은 디자인 일기 화면 값(토큰에 20이 없음).
    TabScreen(stringResource(R.string.record_title), modifier, stringResource(R.string.record_subtitle), topPadding = 20.dp, trailing = seal) {
        if (pets != null) {
            OkcheChoiceChips(
                "누구 기록인가요?",
                pets.filter { it.memorializedAt == null }.map { it.id to it.name } + ("" to "누군지 몰라요"),
                who ?: "",
                { id ->
                    // 칩에서 아이를 고르면 지금 보는 아이도 바뀐다(다른 탭도).
                    unknownPicked = id.isEmpty()
                    if (id.isNotEmpty()) onPickPet(id)
                },
            )
        }
        // 아이를 읽기 전에 누르면 '누군지 모름'으로 저장되니 읽은 뒤에 그린다.
        if (pets != null) {
            QuickRecordCard(onDetail = { detail = DetailTarget(it, petId = who) }) { row, value ->
                scope.launch {
                    runCatching { saveQuickRecord(dao, row, value, who) }
                        .onSuccess {
                            justSaved = it.id
                            failed = false
                            @Suppress("DEPRECATION") view.announceForAccessibility("아뢰었사옵니다")
                        }
                        .onFailure { failed = true }
                }
            }
            Text("누르면 바로 기록돼요. 빛깔·모양 같은 자세한 기록은 ⋯ 버튼으로.", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
        // 아이를 읽기 전엔 이름 대신 '누군지 모름'이 잠깐 보이니 함께 기다린다.
        if (pets != null) RecentRecord(dao, pets, justSaved, onOpenLog)
    }
    detail?.let { target ->
        RecordDetailSheet(target, pets.orEmpty(), onSaved = { record, isNew ->
            if (isNew) {
                justSaved = record.id
                @Suppress("DEPRECATION") view.announceForAccessibility("아뢰었사옵니다")
            }
        }, onClose = { detail = null })
    }
}

/**
 * 일기 탭 상태: '누군지 몰라요'를 골랐는지(이 화면에서만 — 지금 보는 아이는 그대로), 방금 누른 기록 id('아뢰었사옵니다').
 * 탭을 바꾸거나 기록 목록을 다녀와도 남게 RootTabs가 쥔다(iOS는 탭·push라 그대로 남음).
 */
class QuickRecordState(val unknownPicked: MutableState<Boolean>, val justSaved: MutableState<String?>)

@Composable
fun rememberQuickRecordState(): QuickRecordState {
    val unknownPicked = rememberSaveable { mutableStateOf(false) }
    val justSaved = rememberSaveable { mutableStateOf<String?>(null) }
    return remember(unknownPicked, justSaved) { QuickRecordState(unknownPicked, justSaved) }
}

/** 디자인 아이콘(물방울·알약·물방울 둘·그릇)에 가까운 머티리얼 아이콘. */
private fun kindIcon(kind: RecordKind): ImageVector = when (kind) {
    RecordKind.vomit -> Icons.Outlined.WaterDrop
    RecordKind.stool -> Icons.Outlined.Rectangle
    RecordKind.urine -> Icons.Outlined.Opacity
    else -> Icons.Outlined.Restaurant
}

/** 바로 기록 줄 넷(구토·매화·소피·수라): 아이콘·종류 이름 + 56 높이 버튼 셋 + ⋯ 자세히(수라는 7-3), 흰 카드 안 줄 사이 선. 큰 글자에선 이름 아래로 버튼. */
@Composable
private fun QuickRecordCard(onDetail: (RecordKind) -> Unit, onTap: (QuickRow, String) -> Unit) {
    // 큰 글자에선 이름 아래로 버튼. 그보다 작아도 폭이 모자라면 버튼이 다음 줄로(FlowRow).
    val large = LocalDensity.current.fontScale >= 1.3f
    Column(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        quickRows.forEachIndexed { i, row ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
            val label = @Composable {
                Row(
                    Modifier.then(if (large) Modifier else Modifier.width(62.dp)).semantics(mergeDescendants = true) { heading() },
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(kindIcon(row.kind), null, Modifier.size(22.dp), tint = OkcheColor.NamsaekDeep)
                    Text(kindName(row.kind), color = OkcheColor.NamsaekDeep, style = OkcheType.Body.toTextStyle(FontWeight.Bold), softWrap = false)
                }
            }
            val buttons = @Composable {
                for ((value, title) in row.choices) QuickButton(title, "${kindName(row.kind)} $title · 바로 기록") { onTap(row, value) }
            }
            val more = @Composable { MoreButton(row.kind, onDetail) }
            if (large) {
                Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.weight(1f)) { label() }
                        more()
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { buttons() }
                }
            } else {
                Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    label()
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { buttons() }
                    more()
                }
            }
        }
    }
}

@Composable
private fun QuickButton(title: String, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
    Box(
        Modifier
            .widthIn(min = 52.dp)
            .heightIn(min = 56.dp)
            .clip(shape)
            .background(OkcheColor.Paper, shape)
            .border(1.5.dp, OkcheColor.LineStrong, shape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium), textAlign = TextAlign.Center)
    }
}

/** ⋯ 자세히 남기기(점선 44) — 자세히 시트가 있는 종류만. */
@Composable
private fun MoreButton(kind: RecordKind, onDetail: (RecordKind) -> Unit) {
    if (detailFields(kind).isEmpty()) return
    val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
    val dash = with(LocalDensity.current) { PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) }
    Box(
        Modifier
            .size(44.dp)
            .clip(shape)
            .background(OkcheColor.Paper)
            .drawBehind {
                val w = 1.5.dp.toPx()
                drawRoundRect(
                    OkcheColor.LineStrong, topLeft = Offset(w / 2, w / 2), size = Size(size.width - w, size.height - w),
                    cornerRadius = CornerRadius(OkcheRadius.RadiusMd.toPx()), style = Stroke(w, pathEffect = dash),
                )
            }
            .clickable(role = Role.Button) { onDetail(kind) }
            .semantics { contentDescription = "${kindName(kind)} 자세히 남기기" },
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Outlined.MoreHoriz, null, Modifier.size(22.dp), tint = OkcheColor.Namsaek)
    }
}

/** 기록 글자: '콩이 · 매화'(이름 + 종류, 누군지 모르면 '누군지 모름'). */
private fun petName(record: Record, pets: List<Pet>) = record.petId?.let { id -> pets.firstOrNull { it.id == id }?.name } ?: "누군지 모름"

private fun recordTitle(record: Record, pets: List<Pet>) = "${petName(record, pets)} · ${kindName(record.kind)}"

/** 화면 읽기 이름에 쓰는 '콩이 매화'(누군지 모르면 종류만 — 보드 Log). */
private fun recordSpeech(record: Record, pets: List<Pet>) =
    if (record.petId == null) kindName(record.kind) else "${petName(record, pets)} ${kindName(record.kind)}"

/** 방금 남긴 기록: 오늘 이 기기에서 가장 늦게 남긴 기록 하나, 누른 직후에만 '아뢰었사옵니다'. 오늘 기록이 없으면 숨김. */
@Composable
private fun RecentRecord(dao: OkcheDao, pets: List<Pet>, justSaved: String?, onOpenLog: () -> Unit) {
    // shortcut: 켜 둔 채 6시를 넘겨도 다시 그리기 전까지 어제가 오늘로 보임, 문안 탭(7-0)에서 날 바뀜을 함께 다룬다.
    val today = remember { OkcheDay.of(System.currentTimeMillis()) }
    val latest by remember(today) { dao.latestRecordFlow(today) }.collectAsState(null)
    var deleting by remember { mutableStateOf<Record?>(null) }
    var editing by remember { mutableStateOf<DetailTarget?>(null) }
    // 창은 기록이 사라져도(지운 뒤 Flow가 먼저 비어도) 닫히게 먼저 둔다.
    deleting?.let { DeleteRecordDialog(dao, it, pets) { deleting = null } }
    editing?.let { RecordDetailSheet(it, pets, onSaved = { _, _ -> }, onClose = { editing = null }) }
    val record = latest ?: return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, itemVerticalAlignment = Alignment.CenterVertically) {
            Text("방금 남긴 기록", Modifier.padding(end = OkcheSpacing.Space2).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
            Text(
                "오늘 일기 전체",
                Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onOpenLog).wrapContentHeight(Alignment.CenterVertically),
                color = OkcheColor.Namsaek, style = OkcheType.Label.toTextStyle(),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd))
                .padding(start = 14.dp, end = 6.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = OkcheColor.InkMuted)) { append(recordTime(record)) }
                        append("  ${recordTitle(record, pets)} ${recordValue(record)}")
                    },
                    color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
                )
                if (record.id == justSaved) {
                    Text("아뢰었사옵니다", color = OkcheColor.GeumDeep, style = OkcheType.Display.toTextStyle().copy(fontSize = 14.sp, lineHeight = 20.sp))
                }
            }
            EditIcon(record, pets, OkcheColor.Namsaek) { editing = it }
            DeleteIcon("${recordTime(record)} ${recordSpeech(record, pets)} 기록 지우기", OkcheColor.InkMuted) { deleting = record }
        }
    }
}

@Composable
private fun DeleteIcon(label: String, tint: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    IconButton(onClick, Modifier.size(44.dp)) { Icon(Icons.Outlined.DeleteOutline, label, Modifier.size(22.dp), tint = tint) }
}

/** 고치기(연필) — 자세히 시트가 있는 종류만(수라는 7-3). */
@Composable
private fun EditIcon(record: Record, pets: List<Pet>, tint: androidx.compose.ui.graphics.Color, onEdit: (DetailTarget) -> Unit) {
    if (detailFields(record.kind).isEmpty()) return
    IconButton({ onEdit(DetailTarget(record.kind, editing = record)) }, Modifier.size(44.dp)) {
        Icon(Icons.Outlined.Edit, "${recordTime(record)} ${recordSpeech(record, pets)} 기록 고치기", Modifier.size(22.dp), tint = tint)
    }
}

/** 기록 지우기 확인 창(보드 DeleteConfirm): 기록 요약 칸이 안내 글보다 위. 지우면 `deletedAt`·`data` 비움. */
@Composable
private fun DeleteRecordDialog(dao: OkcheDao, record: Record, pets: List<Pet>, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    OkcheConfirmDialog(
        "이 기록을 지울까요?", "지우기",
        onCancel = onClose,
        onConfirm = {
            if (busy) return@OkcheConfirmDialog
            busy = true
            scope.launch {
                runCatching { deleteRecord(dao, record) }
                    .onSuccess { onClose() }
                    .onFailure { failed = true }
                busy = false
            }
        },
        destructive = true, confirmLook = ButtonLook.Danger,
    ) {
        Text(
            "${recordTime(record)} · ${recordTitle(record, pets)} ${recordValue(record)}",
            Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 14.dp, vertical = 12.dp),
            color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
        )
        Text("지운 기록은 되돌릴 수 없고, 실록 통계에서도 빠져요.", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        if (failed) OkcheErrorText("지우지 못했어요. 다시 해 주세요.")
    }
}

/** 기록 목록(보드 Log·LogEmpty): 날짜 이동, '누구 기록' 칩, 그날 요약, 기록 줄(고치기·지우기·누구였는지 정하기). 수라 고치기는 7-3. 하단 탭 없음. */
@Composable
fun RecordLogScreen(pets: List<Pet>, current: Pet?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val today = remember { OkcheDay.of(System.currentTimeMillis()) }
    var day by rememberSaveable { mutableStateOf(today) }
    // '누구 기록' 칩: ''이면 모두.
    var filter by rememberSaveable { mutableStateOf("") }
    val all by remember(day) { dao.dayRecordsFlow(day) }.collectAsState(emptyList())
    val records = if (filter.isEmpty()) all else all.filter { it.petId == filter }
    var deleting by remember { mutableStateOf<Record?>(null) }
    var assigning by remember { mutableStateOf<Record?>(null) }
    var editing by remember { mutableStateOf<DetailTarget?>(null) }
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("승정원일기", "기록 목록", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack)
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            DateBar(day, today) { day = it }
            // 묶음 이름은 화면에 안 보이고 TalkBack만 읽는다(보드: legend sr-only).
            OkcheChoiceChips(
                "누구 기록", listOf("" to "모두") + orderedPets(pets).map { it.id to it.name }, filter, { filter = it },
                hideLabel = true,
            )
            daySummary(records)?.let { Text(it, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal)) }
            if (records.isEmpty()) {
                Text(
                    "이날 남긴 기록이 없어요.",
                    Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(16.dp),
                    color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(),
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                    for (record in records) LogRow(record, pets, onDelete = { deleting = record }, onAssign = { assigning = record }, onEdit = { editing = it })
                }
            }
        }
    }
    deleting?.let { DeleteRecordDialog(dao, it, pets) { deleting = null } }
    assigning?.let { AssignSheet(dao, it, pets, current) { assigning = null } }
    editing?.let { RecordDetailSheet(it, pets, onSaved = { _, _ -> }, onClose = { editing = null }) }
}

/** 날짜 이동: 전날·다음 날(오늘에서 다음 날은 안 눌림), 6시 기준 날. */
@Composable
private fun DateBar(day: String, today: String, onDay: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton({ onDay(shiftDay(day, -1)) }, Modifier.size(44.dp)) { Icon(Icons.Outlined.ChevronLeft, "전날", tint = OkcheColor.Ink) }
        Text(
            dayTitle(day, today), Modifier.weight(1f).semantics { heading() },
            color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold), textAlign = TextAlign.Center,
        )
        val canNext = day < today
        IconButton({ onDay(shiftDay(day, 1)) }, Modifier.size(44.dp).alpha(if (canNext) 1f else 0.35f), enabled = canNext) {
            Icon(Icons.Outlined.ChevronRight, "다음 날", tint = OkcheColor.Ink)
        }
    }
}

@Composable
private fun LogRow(record: Record, pets: List<Pet>, onDelete: () -> Unit, onAssign: () -> Unit, onEdit: (DetailTarget) -> Unit) {
    val unknown = record.petId == null
    val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(OkcheColor.Paper)
            // 누군지 모르는 기록: 왼쪽 남색 띠 4.
            .drawBehind { if (unknown) drawRect(OkcheColor.Namsaek, size = Size(4.dp.toPx(), size.height)) }
            .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(recordTime(record), Modifier.widthIn(min = 44.dp), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        Column(Modifier.weight(1f)) {
            Text(recordTitle(record, pets), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
            if (unknown) {
                Text(
                    "누구였는지 정하기",
                    Modifier.heightIn(min = 32.dp).clickable(role = Role.Button, onClick = onAssign).wrapContentHeight(Alignment.CenterVertically),
                    color = OkcheColor.Namsaek, style = OkcheType.Label.toTextStyle(),
                )
            } else {
                recordValue(record).takeIf { it.isNotEmpty() }?.let {
                    Text(it, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                }
            }
        }
        EditIcon(record, pets, OkcheColor.Ink, onEdit)
        DeleteIcon("${recordTime(record)} ${recordSpeech(record, pets)} 기록 지우기", OkcheColor.Ink, onDelete)
    }
}

/** 누구였는지 정하기(보드 Assign): 기록 요약, 아이들 + 누군지 몰라요 라디오, '정하기'. 누군지 모르는 기록이면 지금 보는 아이를 먼저 골라 둔다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssignSheet(dao: OkcheDao, record: Record, pets: List<Pet>, current: Pet?, onClose: () -> Unit) {
    val scope = rememberCoroutineScope()
    val choices = pets.filter { it.memorializedAt == null }
    var picked by rememberSaveable(record.id) {
        mutableStateOf(record.petId ?: current?.id?.takeIf { id -> choices.any { it.id == id } })
    }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("누구였나요?", Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            // '오늘 02:14 · 구토 · 사료'
            val dayText = if (record.day == OkcheDay.of(System.currentTimeMillis())) "오늘" else dayTitle(record.day, "")
            Text(
                listOf("$dayText ${recordTime(record)}", kindName(record.kind), recordValue(record)).filter { it.isNotEmpty() }.joinToString(" · "),
                color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(),
            )
            Column(
                Modifier.selectableGroup().semantics { contentDescription = "이 기록의 아이" },
                verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            ) {
                for (pet in choices) RadioRow(pet.name, picked == pet.id, { picked = pet.id })
                RadioRow("누군지 몰라요", picked == null, { picked = null })
            }
            Text(
                "정하면 그 아이 실록 통계에 들어가요. 모르는 채로 두면 어느 실록에도 안 들어가요.",
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton("정하기", {
                if (busy) return@OkchePrimaryButton
                busy = true
                scope.launch {
                    runCatching { assignRecord(dao, record, picked) }
                        .onSuccess { onClose() }
                        .onFailure { failed = true }
                    busy = false
                }
            })
        }
    }
}
