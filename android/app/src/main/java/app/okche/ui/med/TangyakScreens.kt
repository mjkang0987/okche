package app.okche.ui.med

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkcheFooter
import app.okche.ui.OkcheFooterButton
import app.okche.ui.OkcheMenuPicker
import app.okche.ui.OkcheMultiChips
import app.okche.ui.OkchePickerField
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheSwitchRow
import app.okche.ui.OkcheTextField
import app.okche.ui.food.SnackPickCard
import app.okche.ui.okcheSwitchColors
import app.okche.ui.pet.FormHeader
import app.okche.ui.pet.ordinal
import app.okche.ui.pet.toWholeNumber
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import app.okche.ui.goldFrame
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 탕약(보드 Tangyak·TangyakEmpty): 더보기 '탕약 · 약 알림'. 모든 아이의 먹고 있는 약 카드(아이마다 따로), '+ 약 추가'. '다음 탕약' 띠는 8-3, 카드 고치기는 8-2b. */
@Composable
fun TangyakScreen(pets: List<Pet>, seal: @Composable () -> Unit, onAdd: () -> Unit, onEdit: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val loadedSchedules by remember { dao.medSchedulesFlow() }.collectAsState(null)
    val schedules = loadedSchedules.orEmpty()
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())
    val loadedRecords by remember { dao.medRecordsFlow() }.collectAsState(null)
    val records = loadedRecords.orEmpty()
    var failed by remember { mutableStateOf(false) }
    val cards = medCards(schedules, pets)
    val toasts = app.okche.ui.LocalUndoToasts.current
    /** 띠 알림의 '되돌리기' = 방금 남긴 기록 지우기(확인 창 → 지움 → '기록을 지웠어요 · 되돌리기'). */
    var deleting by remember { mutableStateOf<app.okche.store.Record?>(null) }
    /** 띄운 알림 — 이 화면이 사라지면 닫는다(되돌리기가 이 화면의 확인 창을 쓰므로). */
    var posted by remember { mutableStateOf<app.okche.ui.UndoToastItem?>(null) }
    androidx.compose.runtime.DisposableEffect(Unit) { onDispose { posted?.let { toasts?.dismiss(it) } } }
    // shortcut: 켜 둔 채 6시를 넘겨도 다시 그리기 전까지 어제 차례가 보임, 일기 탭과 같이 날 바뀜을 다룰 때 함께.
    // 일정·기록을 다 읽기 전엔 띠를 그리지 않음(이미 준 차례가 잠깐 남은 차례로 보이지 않게).
    val next = if (loadedSchedules == null || loadedRecords == null) null else nextSlot(schedules, records, pets, OkcheDay.of(System.currentTimeMillis()))
    /** 기록 직후 1초는 띠를 막는다(두 번 눌러 같은 차례·다음 차례가 또 기록되지 않게). */
    var giving by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("탕약", "약 · 알림", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack, trailing = seal)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 4.dp, bottom = OkcheSpacing.Space6 + app.okche.ui.LocalUndoInset.current),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            // 다음 탕약 띠(보드 Tangyak, 2026-11-05 승인) — 오늘 남은 차례가 없으면 숨김.
            next?.let { (slot, pet) ->
                val med = meds.firstOrNull { it.id == slot.schedule.medicationId } ?: return@let
                val amount = amountText(slot.schedule.amount, slot.schedule.unit)
                val time = clockText(slot.due)
                NextMedBand(time, "${pet.name} · ${med.name} $amount", "$time ${pet.name} ${med.name} $amount 줬어요", busy = giving) {
                    // 일기 탕약 버튼과 같은 기록·알림(되돌리기 = 지우기 확인 창).
                    if (giving) return@NextMedBand
                    giving = true
                    scope.launch {
                        launch { kotlinx.coroutines.delay(1000); giving = false }
                        runCatching { recordSlot(dao, slot) }
                            .onSuccess { saved ->
                                failed = false
                                val toast = app.okche.ui.UndoToastItem("${med.name} 줌 · ${hhmm(saved.occurredAt)}") {
                                    toasts?.show(null)
                                    // 그 사이 고쳤을 수 있어 지금 값으로.
                                    deleting = records.firstOrNull { r -> r.id == saved.id }
                                }
                                posted = toast
                                toasts?.show(toast)
                            }
                            .onFailure {
                                failed = true
                                @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                            }
                    }
                }
            }
            if (cards.isEmpty()) {
                Text(
                    "아직 먹이는 약이 없어요. 약을 추가하면 일기 탕약 줄과 알림에 나와요.",
                    Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
                    color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(),
                )
            } else {
                Text("먹고 있는 약", Modifier.padding(top = 4.dp).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
                for ((s, pet) in cards) {
                    val med = meds.firstOrNull { it.id == s.medicationId } ?: continue
                    TangyakCard(s, pet, med, stockText(med, schedules, records), onEdit = { onEdit(s.id) }) { on ->
                        scope.launch {
                            failed = runCatching { setReminder(dao, s, on) }.isFailure
                            if (failed) view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                        }
                    }
                }
                if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            }
            OkchePrimaryButton("+ 약 추가", onAdd)
        }
    }
    deleting?.let { app.okche.ui.record.DeleteRecordDialog(dao, it, pets) { deleting = null } }
}

/** 다음 탕약 띠(보드 Tangyak): 남색 바탕 금테 112, '다음 탕약 / 오후 9:00 / 콩이 · 프레드니솔론 1알' + 한지색 '줬어요'. 큰 글자에선 버튼을 아래로. */
@Composable
private fun NextMedBand(time: String, detail: String, label: String, busy: Boolean = false, onGive: () -> Unit) {
    val modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 112.dp)
        .background(OkcheColor.NamsaekDeep)
        .goldFrame(inset = 12f, stroke = 1.1f) { size ->
            androidx.compose.ui.graphics.Brush.linearGradient(
                0f to OkcheColor.GeumLight, 0.5f to OkcheColor.Geum, 1f to OkcheColor.GeumShade,
                start = Offset.Zero, end = Offset(size.width, size.height),
            )
        }
        .padding(horizontal = 28.dp, vertical = 18.dp) // 금테(12 안쪽) 안에 머물고 보드 높이 112
    val texts = @Composable { m: Modifier ->
        Column(m, verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("다음 탕약", Modifier.semantics { heading() }, color = OkcheColor.OnDeepMuted, style = OkcheType.Label.toTextStyle(FontWeight.Medium))
            // 보드는 본문 글꼴 22 굵게(제목 글꼴 아님).
            Text(time, color = OkcheColor.GeumLight, style = OkcheType.Body.toTextStyle(FontWeight.Bold).copy(fontSize = 22.sp, lineHeight = 30.sp))
            Text(detail, color = OkcheColor.Hanji, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
    }
    val button = @Composable {
        Box(
            Modifier
                .heightIn(min = 52.dp)
                .background(OkcheColor.Hanji, RoundedCornerShape(OkcheRadius.RadiusMd))
                .clickable(enabled = !busy, role = Role.Button, onClick = onGive)
                .semantics { contentDescription = label }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text("줬어요", Modifier.clearAndSetSemantics {}, color = OkcheColor.NamsaekDeep, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        }
    }
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) {
        Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) { texts(Modifier); button() }
    } else {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) { texts(Modifier.weight(1f)); button() }
    }
}

/** 탕약 카드: 약 이름 + '콩이 · 이틀에 한 번 · 1알', 알림 스위치(바로 저장), 시간 알약·먹는 날 글자, 약방 줄. */
@Composable
private fun TangyakCard(s: MedSchedule, pet: Pet, med: Medication, stock: String, onEdit: () -> Unit, onReminder: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 설명이 길면 글이 줄을 넘기고 스위치는 오른쪽 그대로(보드). 아주 큰 글자에선 스위치를 아래로.
        // 이름 부분을 누르면 고치기(TalkBack '프레드니솔론 · 콩이 · 이틀에 한 번 · 1알 · 고치기').
        val title = @Composable { m: Modifier ->
            Row(
                m.heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onEdit)
                    .semantics { contentDescription = "${med.name} · ${cardDetail(s, pet.name)} · 고치기"; heading() },
                horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f, fill = false).clearAndSetSemantics {}, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(med.name, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                    Text(cardDetail(s, pet.name), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                }
                androidx.compose.material3.Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = OkcheColor.LineStrong)
            }
        }
        val toggle = @Composable {
            if (s.frequency != MedFrequency.asNeeded) {
                // 줄 전체가 스위치 하나(TalkBack이 이름과 함께 한 번에 읽게 — OkcheSwitchRow와 같은 방식).
                Row(
                    Modifier.heightIn(min = 48.dp).toggleable(s.reminder, role = Role.Switch, onValueChange = onReminder)
                        .semantics { contentDescription = "${med.name} ${pet.name} 알림" },
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("알림", Modifier.clearAndSetSemantics {}, color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                    Switch(s.reminder, null, colors = okcheSwitchColors())
                }
            }
        }
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { title(Modifier); toggle() }
        } else {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                title(Modifier.weight(1f))
                toggle()
            }
        }
        val pill = @Composable { text: String ->
            Text(text, Modifier.background(OkcheColor.NamsaekSoft, CircleShape).padding(horizontal = 10.dp, vertical = 4.dp),
                color = OkcheColor.NamsaekDeep, style = OkcheType.Label.toTextStyle())
        }
        val side = @Composable { text: String -> Text(text, Modifier.padding(vertical = 4.dp), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal)) }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            when (s.frequency) {
                MedFrequency.asNeeded -> side("필요할 때만")
                MedFrequency.everyNHours -> nextDue(s)?.let { next ->
                    val at = Instant.ofEpochMilli(next).atZone(ZoneId.systemDefault())
                    pill(clock("%02d:%02d".format(at.hour, at.minute)))
                    side("다음 ${at.monthValue}/${at.dayOfMonth}")
                }
                else -> {
                    for (t in s.times.sorted()) pill(clock(t))
                    doseDayText(s, OkcheDay.of(System.currentTimeMillis()))?.let { side(it) }
                }
            }
        }
        androidx.compose.material3.HorizontalDivider(color = OkcheColor.Line)
        Text(
            buildAnnotatedString {
                append("약방")
                withStyle(SpanStyle(fontSize = 0.85.em)) { append(" · $stock") }
            },
            color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
        )
    }
}

/**
 * 약 추가(보드 MedAdd·MedAddWeekday·MedAddHours·MedAddAsNeeded): 누가 먹나요(여럿), 약방에서 고르기, 한 번에 얼마나,
 * 얼마나 자주 5가지(주기마다 칸), 알림, 먹이는 방법, '저장하기' — 고른 아이마다 한 줄.
 */
@Composable
fun MedAddScreen(
    pets: List<Pet>, current: Pet?, onBack: () -> Unit,
    /** 있으면 약 고치기(보드 MedChange) — 아이·약은 고정, '이 약 그만 먹이기'(8-2b). [original]은 그 줄의 지금 값. */
    editing: MedSchedule? = null, original: ScheduleFields? = null, schedules: List<MedSchedule> = emptyList(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())
    val records by remember { dao.medRecordsFlow() }.collectAsState(emptyList())
    val alive = pets.filter { it.memorializedAt == null }
    // 처음 값: 지금 보는 아이(추억이면 처음 모신 살아 있는 아이), 시작하는 날 오늘(6시 기준).
    var f by remember {
        mutableStateOf(
            original ?: ScheduleFields(
                petIds = listOfNotNull((current?.takeIf { it.memorializedAt == null } ?: alive.firstOrNull())?.id),
                startDate = LocalDate.parse(OkcheDay.of(System.currentTimeMillis())),
            ),
        )
    }
    var firstAtTouched by remember { mutableStateOf(false) }
    var stopping by remember { mutableStateOf(false) }
    /** 한 번 만진 칸 — 칸 아래 오류는 만진 뒤부터(열자마자 빨간 글자가 뜨지 않게). */
    var touched by remember { mutableStateOf(setOf<String>()) }
    val allErrors = scheduleErrors(f)
    val errors = allErrors.filterKeys { it in touched }
    // 첫 번째로 남은 것(위에서부터: pets, med, dose, everyDays, weekdays, everyHours, times — scheduleErrors가 이 순서로 채움) — 있으면 저장 버튼은 흐리게.
    val missingKey = allErrors.keys.firstOrNull()
    val missing = missingKey?.let { k -> allErrors.getValue(k).let { m -> scheduleLabels[k]?.let { app.okche.ui.okcheNamed(it, m) } ?: m } }
    val anchors = remember { app.okche.ui.pet.Anchors() }
    var adding by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val listed = meds.filter { it.deletedAt == null }.sortedBy { it.createdAt }
    // 고른 약의 단위 — 고를 때 기억해 둔다(방금 들인 약이 목록에 아직 안 떠도 맞게).
    var pickedUnit by remember { mutableStateOf(editing?.unit) }
    // shortcut: 화면을 돌리면 적던 칸이 처음 값으로 돌아감, 불편하다는 말이 나오면 칸을 rememberSaveable로.
    val unit = pickedUnit ?: meds.firstOrNull { it.id == f.medicationId }?.unit ?: app.okche.store.DoseUnit.pill

    fun edit(key: String, change: ScheduleFields.() -> ScheduleFields) {
        if (key == "firstAt") firstAtTouched = true
        var next = f.change()
        // 고치기에서 '처음 먹일 때'를 손대지 않았으면 간격·늦게 줬을 때를 바꿀 때 다시 채운다(6-1).
        // 처음과 같으면 처음 채운 값 그대로(시각이 흘러 값이 바뀌어 새 줄이 생기지 않게).
        if (editing != null && original != null && !firstAtTouched && next.frequency == MedFrequency.everyNHours && key in setOf("everyHours", "shiftOnLate", "frequency")) {
            val same = next.everyHours == original.everyHours && next.shiftOnLate == original.shiftOnLate && next.frequency == original.frequency
            next = next.copy(firstAt = if (same) original.firstAt else firstAtPrefill(editing, next.everyHours.toWholeNumber() ?: editing.everyHours ?: 8, next.shiftOnLate, schedules, records))
        }
        f = next
        touched = touched + key
    }

    fun pick(med: Medication) {
        pickedUnit = med.unit
        f = f.copy(medicationId = med.id, dose = medNumber(med.doseAmount)) // 그 약의 '한 번에'
        touched = touched + "med" + "dose"
    }

    fun save() {
        if (busy || missing != null) return
        busy = true
        scope.launch {
            failed = runCatching { if (editing != null && original != null) changeSchedule(dao, editing, original, f) else addSchedules(dao, f, unit) }.isFailure
            busy = false
            if (failed) view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.") else onBack()
        }
    }

    if (adding) MedFormSheet(null, pets, records, picking = true, onAdded = { pick(it) }) { adding = false }
    val toasts = app.okche.ui.LocalUndoToasts.current
    if (stopping && editing != null) {
        val med = meds.firstOrNull { it.id == editing.medicationId }
        app.okche.ui.OkcheConfirmDialog(
            "${app.okche.ui.pet.withObjectParticle(med?.name.orEmpty())} 그만 먹일까요?", "그만 먹이기",
            onCancel = { stopping = false },
            onConfirm = {
                stopping = false
                if (!busy) {
                    busy = true
                    scope.launch {
                        failed = runCatching { stopSchedule(dao, editing) }.isFailure
                        busy = false
                        if (failed) {
                            view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                        } else {
                            onBack()
                            toasts?.removed("${app.okche.ui.pet.withObjectParticle(med?.name ?: "약")} 그만 먹이기로 했어요") { resumeSchedule(dao, editing) }
                        }
                    }
                }
            },
            destructive = true,
            message = "${pets.firstOrNull { it.id == editing.petId }?.name.orEmpty()}의 탕약 줄과 알림에서 바로 빠지고, 오늘 아직 안 준 차례도 함께 빠져요. 지난 기록과 약방의 약은 그대로 남아요.",
            confirmLook = app.okche.ui.ButtonLook.Danger,
        )
    }
    // 아래 고정 버튼이 기기 아래 막대·자판에 가리지 않게 safeDrawing, 되돌리기 알림이 떠 있으면 그만큼 위로.
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding().padding(bottom = app.okche.ui.LocalUndoInset.current)) {
        FormHeader("탕약", if (editing == null) "약 추가" else "약 고치기", Icons.AutoMirrored.Outlined.ArrowBack, "탕약으로 돌아가기", onBack)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 8.dp, bottom = OkcheSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (editing != null) {
                // 고치기 위: '콩이 · 프레드니솔론 알약'(아이·약은 바꿀 수 없음).
                val med = meds.firstOrNull { it.id == editing.medicationId }
                val petName = pets.firstOrNull { it.id == editing.petId }?.name.orEmpty()
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OkcheFieldLabel("누가 · 어떤 약", Modifier.clearAndSetSemantics {})
                    Text(
                        buildAnnotatedString {
                            append("$petName · ${med?.name.orEmpty()}")
                            med?.let { withStyle(SpanStyle(color = OkcheColor.InkMuted, fontWeight = FontWeight.Normal, fontSize = 0.875.em)) { append("  ${medFormTitle(it.form)}") } }
                        },
                        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 16.dp, vertical = 14.dp)
                            .semantics { contentDescription = "누가 · 어떤 약, $petName ${med?.name.orEmpty()} ${med?.let { medFormTitle(it.form) }.orEmpty()}" },
                        color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold),
                    )
                    Note("아이나 약을 바꾸려면 이 약을 그만 먹이고 새로 추가해 주세요.")
                }
            } else Column(anchors.of("pets"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OkcheMultiChips("누가 먹나요?", alive.map { it.id to it.name }, f.petIds, { id ->
                    edit("pets") { copy(petIds = if (id in petIds) petIds - id else petIds + id) }
                }, hint = "(여럿 고를 수 있어요)")
                Note("여럿 고르면 아이마다 따로 만들어져요. 양·시간은 나중에 아이마다 고칠 수 있어요.")
                errors["pets"]?.let { OkcheErrorText(it) }
            }
            if (editing == null) Column(anchors.of("med"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OkcheFieldLabel("약방에서 고르기", Modifier.semantics { heading() })
                for (med in listed) {
                    val left = medRemaining(med, records)?.let { "남은 ${medNumber(maxOf(it, 0.0))}${unitTitle(med.unit)}" }
                    SnackPickCard(med.name, listOfNotNull(medFormTitle(med.form), left).joinToString(" · "), f.medicationId == med.id) { pick(med) }
                }
                Text(
                    "+ 약방에 새 약 들이기",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { adding = true }.padding(vertical = 12.dp),
                    color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
                )
                errors["med"]?.let { OkcheErrorText(it) }
            }
            OkcheTextField(
                f.dose, { v -> edit("dose") { copy(dose = v) } }, "한 번에 얼마나", anchors.of("dose").width(160.dp), suffix = unitTitle(unit), description = unitTitle(unit),
                error = errors["dose"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Frequency(f, errors, ::edit, anchors::of)
            OkcheMenuPicker("먹이는 방법", howTos, f.howTo, { v -> edit("howTo") { copy(howTo = v) } }, inline = false, hint = "(선택)")
            if (editing != null) Note("횟수·시간·양을 바꾸면 지금부터 새로 세요. 지난 기록은 그대로예요.")
            // 그만 먹이기는 저장 버튼 바로 밑에서 잘못 누르지 않게 내용 맨 아래(아래 고정 칸 밖).
            if (editing != null) {
                Text(
                    "이 약 그만 먹이기",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, enabled = !busy) { stopping = true }.padding(vertical = 12.dp),
                    color = OkcheColor.Yeonji, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        OkcheFooter {
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            // 남은 것 줄을 누르면 그 칸으로 가서 칸 아래 오류를 보여 줌.
            OkcheFooterButton("저장하기", ::save, missing) {
                missingKey?.let { k ->
                    touched = touched + k
                    scope.launch { anchors.goTo(k, focusable = k == "dose" || k == "everyDays" || k == "everyHours") }
                }
            }
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))

/** 얼마나 자주 + 주기마다 칸 + 먹일 시간·알림. */
@Composable
private fun Frequency(f: ScheduleFields, errors: Map<String, String>, edit: (String, ScheduleFields.() -> ScheduleFields) -> Unit, anchor: (String) -> Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    // 간격 + 날짜 칸: 나란히, 아주 큰 글자에선 위아래로.
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val pair = @Composable { key: String, left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit ->
        if (stacked) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { left(Modifier.width(if (key == "everyDays") 150.dp else 160.dp)); right(Modifier.weight(1f)) }
        }
    }
    val zone = ZoneId.systemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OkcheChoiceChips("얼마나 자주", medFrequencies, f.frequency, { v -> edit("frequency") { copy(frequency = v) } })
        when (f.frequency) {
            MedFrequency.everyNDays -> {
                pair("everyDays", { m ->
                    OkcheTextField(
                        f.everyDays, { v -> edit("everyDays") { copy(everyDays = v) } }, "간격", anchor("everyDays").then(m), suffix = "일마다", description = "일마다",
                        error = errors["everyDays"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }) { m ->
                    OkchePickerField(
                        "${f.startDate.monthValue}월 ${f.startDate.dayOfMonth}일", "시작하는 날",
                        {
                            DatePickerDialog(context, { _, y, m, d -> edit("startDate") { copy(startDate = LocalDate.of(y, m + 1, d)) } },
                                f.startDate.year, f.startDate.monthValue - 1, f.startDate.dayOfMonth).show()
                        },
                        m, icon = Icons.Outlined.CalendarMonth,
                    )
                }
                Note("이틀에 한 번이면 2, 일주일에 한 번이면 7. 먹는 날에만 일기 탕약 줄과 알림에 나와요.")
            }
            MedFrequency.weekdays -> {
                Column(anchor("weekdays"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OkcheMultiChips("먹는 요일", (1..7).map { it to weekdayNames[it - 1] }, f.weekdays, { d ->
                        edit("weekdays") { copy(weekdays = if (d in weekdays) weekdays - d else weekdays + d) }
                    })
                    errors["weekdays"]?.let { OkcheErrorText(it) }
                }
                val picked = f.weekdays.sorted().map { weekdayNames[it - 1] }
                val counts = listOf("", "한", "두", "세", "네", "다섯", "여섯", "일곱")
                Note("고른 요일에만 일기 탕약 줄과 알림에 나와요." + if (picked.isEmpty()) "" else " 지금은 ${picked.joinToString("·")}, 일주일에 ${counts[picked.size]} 번.")
            }
            MedFrequency.everyNHours -> {
                val first = Instant.ofEpochMilli(f.firstAt).atZone(zone)
                pair("everyHours", { m ->
                    OkcheTextField(
                        f.everyHours, { v -> edit("everyHours") { copy(everyHours = v) } }, "간격", anchor("everyHours").then(m), suffix = "시간마다", description = "시간마다",
                        error = errors["everyHours"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }) { m ->
                    OkchePickerField(
                        first.format(DateTimeFormatter.ofPattern("M/d a h:mm", Locale.KOREAN)), "처음 먹일 때",
                        {
                            DatePickerDialog(context, { _, y, m, d ->
                                TimePickerDialog(context, { _, h, min ->
                                    edit("firstAt") { copy(firstAt = LocalDateTime.of(y, m + 1, d, h, min).atZone(zone).toInstant().toEpochMilli()) }
                                }, first.hour, first.minute, false).show()
                            }, first.year, first.monthValue - 1, first.dayOfMonth).show()
                        },
                        m, icon = Icons.Outlined.CalendarMonth,
                    )
                }
                f.everyHours.toWholeNumber()?.takeIf { it >= 1 }?.let { HoursPreview(hoursPreview(f.firstAt, it)) }
                Note("시간이 날마다 바뀌어서 먹일 시간은 따로 정하지 않아요.")
                OkcheChoiceCards("늦게 줬을 때 다음 차례는", listOf(true to "준 시각부터 다시 세기", false to "처음 정한 시간대로"), f.shiftOnLate, { v ->
                    edit("shiftOnLate") { copy(shiftOnLate = v) }
                })
                Note(if (f.shiftOnLate) "늦게 준 만큼 다음 차례도 밀려요." else "늦게 줘도 다음 차례 시간은 그대로예요.")
            }
            MedFrequency.asNeeded -> Note("필요할 때만 주는 약은 일기 탕약 줄과 알림에 나오지 않아요. 줄 때 오늘 먹일 약 시트의 '+ 예정에 없던 약 남기기'로 남겨요.")
            MedFrequency.daily -> Unit
        }
    }
    if (f.frequency == MedFrequency.asNeeded) return
    Column(anchor("times"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.frequency != MedFrequency.everyNHours) {
            OkcheFieldLabel(if (f.frequency == MedFrequency.daily) "먹일 시간" else "먹는 날 먹일 시간", Modifier.semantics { heading() })
            TimeFieldsRow(
                Modifier,
                f.times,
                onPick = { index, time ->
                    // 칸이 사라지는 게 보이고, TalkBack에도 알린다.
                    if (time in f.times && f.times.indexOf(time) != index) view.announceForAccessibility("이미 있는 시간이라 하나로 합쳤어요")
                    edit("times") {
                        // 같은 시간이 이미 있으면 그 칸을 빼서 겹치지 않게.
                        copy(times = if (time in times && times.indexOf(time) != index) times.filterIndexed { i, _ -> i != index } else times.mapIndexed { i, t -> if (i == index) time else t })
                    }
                },
                onAdd = {
                    // 보드처럼 08:00 다음은 21:00, 그 뒤로는 비어 있는 정시.
                    val candidates = listOf("08:00", "21:00") + (0 until 24).map { "%02d:00".format((12 + it) % 24) }
                    candidates.firstOrNull { it !in f.times }?.let { next -> edit("times") { copy(times = times + next) } }
                },
                onRemove = { index -> if (f.times.size > 1) edit("times") { copy(times = times.filterIndexed { i, _ -> i != index }) } },
            )
            errors["times"]?.let { OkcheErrorText(it) }
        }
        OkcheSwitchRow(
            if (f.frequency == MedFrequency.everyNHours) "차례마다 알림 보내기" else "시간마다 알림 보내기", "", f.reminder,
            { v -> edit("reminder") { copy(reminder = v) } },
        )
        OkcheMenuPicker("알림 보낼 때", medLeadChoices, f.leadMinutes, { v -> edit("lead") { copy(leadMinutes = v) } })
    }
}

/** 먹일 시간 칸 줄: 시간 칸 여러 개 옆으로(넘치면 다음 줄) + 점선 '+'. 칸을 누르면 시각 고르기, 길게 누르면 빼기(둘 이상일 때). */
@Composable
private fun TimeFieldsRow(modifier: Modifier, times: List<String>, onPick: (Int, String) -> Unit, onAdd: () -> Unit, onRemove: (Int) -> Unit) {
    val context = LocalContext.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        times.forEachIndexed { index, time ->
            val p = time.split(":").mapNotNull { it.toIntOrNull() }
            Box(Modifier.widthIn(min = 120.dp)) {
                OkchePickerField(
                    clock(time), "${ordinal(index + 1)} 시간",
                    {
                        TimePickerDialog(context, { _, h, m -> onPick(index, "%02d:%02d".format(h, m)) }, p.getOrElse(0) { 8 }, p.getOrElse(1) { 0 }, false).apply {
                            if (times.size > 1) setButton(android.content.DialogInterface.BUTTON_NEUTRAL, "이 시간 빼기") { _, _ -> onRemove(index) }
                        }.show()
                    },
                    showLabel = false,
                )
            }
        }
        val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
        Box(
            Modifier.size(width = 48.dp, height = 52.dp)
                .drawWithContent {
                    drawContent()
                    val w = 1.5.dp.toPx()
                    drawRoundRect(OkcheColor.Namsaek, Offset(w / 2, w / 2), Size(size.width - w, size.height - w), CornerRadius(OkcheRadius.RadiusMd.toPx() - w / 2),
                        style = Stroke(w, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx()))))
                }
                .clickable(role = Role.Button, onClickLabel = "시간 하나 더", onClick = onAdd)
                .semantics { contentDescription = "시간 하나 더" },
            contentAlignment = Alignment.Center,
        ) { Text("+", Modifier.clearAndSetSemantics {}, color = OkcheColor.Namsaek, style = OkcheType.Heading.toTextStyle(FontWeight.Normal)) }
    }
}

/** '이렇게 돌아가요': 몇 시간마다의 다음 몇 번. */
@Composable
private fun HoursPreview(lines: List<String>) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(14.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("이렇게 돌아가요", Modifier.semantics { heading() }, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle())
        for (line in lines) Text(line, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
    }
}

/** 남은 것 줄의 칸 이름(iOS와 같음). 아이·약은 문구가 이미 말해 줘서 없음. */
private val scheduleLabels = mapOf("dose" to "한 번에", "everyDays" to "며칠마다 간격", "everyHours" to "몇 시간마다 간격", "times" to "먹일 시간")

/** 약 고치기(보드 MedChange): 그 줄·기록을 다 읽은 뒤 약 추가 화면을 고치기로 연다. 그 사이 그만 먹였으면 탕약으로. */
@Composable
fun MedEditScreen(pets: List<Pet>, scheduleId: String, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState<List<MedSchedule>, List<MedSchedule>?>(null)
    val records by remember { dao.medRecordsFlow() }.collectAsState<List<app.okche.store.Record>, List<app.okche.store.Record>?>(null)
    val all = schedules ?: return
    val recs = records ?: return
    val editing = remember(all.isNotEmpty()) { all.firstOrNull { it.id == scheduleId && it.activeUntil == null } }
    if (editing == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onBack() }
        return
    }
    val original = remember { scheduleFieldsOf(editing, firstAtPrefill(editing, editing.everyHours ?: 8, editing.shiftOnLate, all, recs)) }
    MedAddScreen(pets, null, onBack, editing = editing, original = original, schedules = all)
}
