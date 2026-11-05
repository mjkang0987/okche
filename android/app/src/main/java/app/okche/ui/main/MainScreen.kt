package app.okche.ui.main

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.ButtonLook
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.TabScreen
import app.okche.ui.food.FoodCheckCard
import app.okche.ui.goldFrame
import app.okche.ui.toTextStyle
import app.okche.ui.pet.companionDays
import app.okche.ui.pet.petTitle
import app.okche.ui.record.DetailTarget
import app.okche.ui.record.RecordDetailSheet
import app.okche.ui.record.kindName
import app.okche.ui.record.recordTime
import app.okche.ui.record.recordValue
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import java.time.Instant
import java.time.ZoneId
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay

/** 문안 탭(보드 Main·MainEmpty·MainNoPet): 인사, 아이 카드, '기록하기', 오늘의 승정원일기. 추억 아이는 RootTabs에서 추억 화면(6-4). */
@Composable
fun MainTab(
    pets: List<Pet>?,
    current: Pet?,
    modifier: Modifier = Modifier,
    seal: @Composable () -> Unit = {},
    onRecord: () -> Unit = {},
    onRegister: () -> Unit = {},
    onOpenSura: (String) -> Unit = {},
) {
    // 1분마다·앱으로 돌아올 때 — 켜 둔 채 6시를 넘기면 날짜·오늘 기록이 새 날로.
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            now = System.currentTimeMillis()
        }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) now = System.currentTimeMillis() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val day = OkcheDay.of(now)
    val today = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).toLocalDate()
    TabScreen("문안", modifier, mainDayTitle(day), trailing = seal) {
        when {
            pets == null -> Unit // 아이 목록을 읽기 전엔 비워 둔다('아직 모신 아이가 없어요'가 스치지 않게).
            current == null -> {
                Text(
                    "아직 모신 아이가 없어요. 아기씨를 모시면 여기에서 날마다 안부를 여쭤요.",
                    color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
                )
                OkchePrimaryButton("+ 새 아기씨 모시기", onRegister, look = ButtonLook.Dashed)
            }
            else -> {
                Text(
                    "${petTitle(current, today)}, 오늘도 강녕하시옵소서",
                    // 머리와의 사이 16에서 6을 줄인다(보드 margin-top -6). 음수 padding은 Compose에서 안 돼 자리만 옮긴다.
                    Modifier.layout { measurable, constraints ->
                        val placeable = measurable.measure(constraints)
                        val up = 6.dp.roundToPx()
                        layout(placeable.width, placeable.height - up) { placeable.place(0, -up) }
                    },
                    color = OkcheColor.NamsaekDeep,
                    style = OkcheType.Display.toTextStyle(FontWeight.Normal).copy(fontSize = 16.sp, lineHeight = 22.sp),
                )
                PetCard(current, today)
                FoodCheckCard(current, day)
                OkchePrimaryButton("기록하기", onRecord)
                TodayRecords(pets, current, day, onOpenSura)
            }
        }
    }
}

/** 아이 카드(보드 Main): 남색 바탕 금테 148, 프로필 사진 자리 96(사진은 11단계), 표시 알약·호칭·동행·종 줄. 큰 글자에선 사진 아래로 글자. */
@Composable
private fun PetCard(pet: Pet, today: java.time.LocalDate) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 148.dp)
        .background(OkcheColor.NamsaekDeep)
        .goldFrame(inset = 12f, stroke = 1.1f) { size ->
            Brush.linearGradient(
                0f to OkcheColor.GeumLight, 0.5f to OkcheColor.Geum, 1f to OkcheColor.GeumShade,
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
        }
        // 금테(12 안쪽 + 매듭 9) 안에 머물면서 알약이 있어도 148.
        .padding(horizontal = 28.dp, vertical = 21.dp)
        .semantics { contentDescription = "${pet.name} 카드" }
    val photo = @Composable {
        Box(
            Modifier
                .size(96.dp)
                .background(OkcheColor.Namsaek)
                .border(1.dp, OkcheColor.Geum)
                .semantics { contentDescription = "${pet.name} 프로필 사진"; role = Role.Image },
            contentAlignment = Alignment.Center,
        ) {
            // 자리는 96 그대로라 큰 글자에선 글자를 줄여 넣는다.
            val caption = OkcheType.Caption.toTextStyle()
            Text(
                "프로필 사진", Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {}, color = OkcheColor.GeumLight, style = caption,
                maxLines = 1, softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = caption.fontSize * 0.4f, maxFontSize = caption.fontSize),
            )
        }
    }
    val texts = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            mainPill(pet.birthDate, pet.birthEstimated, pet.familyDate, today.toString())?.let {
                Text(
                    it,
                    Modifier.background(OkcheColor.Geum, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp),
                    color = OkcheColor.Ink, style = OkcheType.Caption.toTextStyle(FontWeight.Bold),
                )
            }
            Text(petTitle(pet, today), Modifier.semantics { heading() }, color = OkcheColor.GeumLight, style = OkcheType.Title.toTextStyle())
            companionDays(pet.familyDate, today)?.let {
                Text("동행 %,d일".format(it), color = OkcheColor.Hanji, style = OkcheType.Body.toTextStyle())
            }
            Text(mainDetail(pet, today), color = OkcheColor.OnDeepMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
    }
    if (LocalDensity.current.fontScale >= 1.3f) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { photo(); texts() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) { photo(); texts() }
    }
}

/** 오늘의 승정원일기(보드 Main·MainEmpty): 그 아이의 오늘 기록, 줄을 누르면 고치기 시트(수라는 수라 화면). */
@Composable
private fun TodayRecords(pets: List<Pet>, pet: Pet, day: String, onOpenSura: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    // 처음 읽기 전(null)엔 줄도 안내도 그리지 않는다('아직 오늘 남긴 기록이 없어요'가 스치지 않게).
    val records by remember(day) { dao.dayRecordsFlow(day) }.collectAsState<List<Record>, List<Record>?>(null)
    var editing by remember { mutableStateOf<DetailTarget?>(null) }
    var snackEditing by remember { mutableStateOf<Record?>(null) }
    snackEditing?.let { app.okche.ui.food.SnackRecordSheet(pet, it) { snackEditing = null } }
    var medEditing by remember { mutableStateOf<Record?>(null) }
    medEditing?.let { app.okche.ui.med.MedExtraSheet(pet, it) { medEditing = null } } // 탕약은 기록 하나 고치기 시트(8-3b)
    editing?.let { RecordDetailSheet(it, pets, onSaved = { _, _ -> }, onClose = { editing = null }) }
    // '예정' 줄을 누르면 오늘 먹일 약 시트(8-3c).
    var medSheet by remember { mutableStateOf(false) }
    if (medSheet) app.okche.ui.med.MedTodaySheet(pet) { medSheet = false }
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(null)
    // 몇 시간마다 밀림은 앞날 기록도 보니 탕약 기록 전체로.
    val medRecords by remember { dao.medRecordsFlow() }.collectAsState(null)
    // 오늘 남은 탕약 차례는 '21:00  탕약 · 약 … 예정' 줄로 기록 사이에 시간 순(보드 Main). 다 읽기 전엔 그리지 않음.
    val rows: List<Pair<Long, Any>>? = records?.let { recs ->
        val s = schedules ?: return@let null
        val m = medRecords ?: return@let null
        (mainTodayRecords(recs, pet.id, day).map { it.occurredAt to it as Any } +
            app.okche.ui.med.remainingSlots(s, m, pet.id, day).map { it.due to it as Any }).sortedBy { it.first }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        // 큰 글자에서 한 줄에 안 들어가면 '오늘 기록'을 아래로, 제목은 낱말 가운데서 끊지 않고 글자를 줄인다.
        FlowRow(Modifier.semantics(mergeDescendants = true) { heading() }, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val title = OkcheType.Display.toTextStyle().copy(fontSize = 18.sp, lineHeight = 26.sp)
            Text(
                "오늘의 승정원일기",
                Modifier.alignByBaseline(),
                color = OkcheColor.Ink,
                style = title,
                maxLines = 1,
                softWrap = false,
                autoSize = TextAutoSize.StepBased(minFontSize = title.fontSize / 2, maxFontSize = title.fontSize),
            )
            Text("오늘 기록", Modifier.alignByBaseline(), color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
        }
        if (rows?.isEmpty() == true) {
            Text(
                "아직 오늘 남긴 기록이 없어요.",
                Modifier.padding(top = 8.dp, bottom = 4.dp),
                color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(),
            )
        } else if (rows != null) {
            rows.forEachIndexed { index, (_, item) ->
                if (item is app.okche.ui.med.MedSlot) PendingRow(item) { medSheet = true }
                else if (item is Record) RecordRow(item) {
                    val record = item
                    when (record.kind) {
                        RecordKind.meal -> onOpenSura(pet.id)
                        RecordKind.snack -> snackEditing = record
                        RecordKind.med -> medEditing = record
                        else -> editing = DetailTarget(record.kind, editing = record)
                    }
                }
                if (index < rows.lastIndex) HorizontalDivider(color = OkcheColor.Line)
            }
        }
    }
}

/** 예정 줄 52: '21:00  탕약 · 약 … 예정 ›', 누르면 오늘 먹일 약 시트. */
@Composable
private fun PendingRow(slot: app.okche.ui.med.MedSlot, onClick: () -> Unit) {
    val time = app.okche.ui.med.hhmm(slot.due)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$time 탕약 예정 · 오늘 먹일 약 보기"; role = Role.Button },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = OkcheColor.InkMuted)) { append(time) }
                append("  탕약")
                withStyle(SpanStyle(color = OkcheColor.InkMuted, fontSize = 0.85.em, fontWeight = FontWeight.Normal)) { append(" · 약") }
            },
            Modifier.weight(1f),
            color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
        )
        Text("예정", color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = OkcheColor.Namsaek)
    }
}

/** 오늘 기록 줄 52: '07:20  소피 · 소변' · 값 · 연필. 화면 읽기 이름 '07:20 소피 보통 · 고치기'. */
@Composable
private fun RecordRow(record: Record, onClick: () -> Unit) {
    val value = recordValue(record, app.okche.ui.record.rememberSnacks(), app.okche.ui.record.rememberMeds())
    val label = "${recordTime(record)} ${kindName(record.kind)}${if (value.isEmpty()) "" else " $value"} · 고치기"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = OkcheColor.InkMuted)) { append(recordTime(record)) }
                append("  ${kindName(record.kind)}")
                plainWord(record.kind)?.let { withStyle(SpanStyle(color = OkcheColor.InkMuted, fontSize = 0.85.em, fontWeight = FontWeight.Normal)) { append(" · $it") } }
            },
            Modifier.weight(1f).clearAndSetSemantics {},
            color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
        )
        Text(value, Modifier.clearAndSetSemantics {}, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
        Icon(Icons.Outlined.Edit, null, Modifier.size(20.dp), tint = OkcheColor.Namsaek)
    }
}
