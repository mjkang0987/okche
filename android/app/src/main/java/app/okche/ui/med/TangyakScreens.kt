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
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 탕약(보드 Tangyak·TangyakEmpty): 더보기 '탕약 · 약 알림'. 모든 아이의 먹고 있는 약 카드(아이마다 따로), '+ 약 추가'. '다음 탕약' 띠는 8-3, 카드 고치기는 8-2b. */
@Composable
fun TangyakScreen(pets: List<Pet>, seal: @Composable () -> Unit, onAdd: () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(emptyList())
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())
    val records by remember { dao.medRecordsFlow() }.collectAsState(emptyList())
    var failed by remember { mutableStateOf(false) }
    val cards = medCards(schedules, pets)
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("탕약", "약 · 알림", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack, trailing = seal)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 4.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
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
                    TangyakCard(s, pet, med, stockText(med, schedules, records)) { on ->
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
}

/** 탕약 카드: 약 이름 + '콩이 · 이틀에 한 번 · 1알', 알림 스위치(바로 저장), 시간 알약·먹는 날 글자, 약방 줄. */
@Composable
private fun TangyakCard(s: MedSchedule, pet: Pet, med: Medication, stock: String, onReminder: (Boolean) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        // 설명이 길면 글이 줄을 넘기고 스위치는 오른쪽 그대로(보드). 아주 큰 글자에선 스위치를 아래로.
        val title = @Composable { m: Modifier ->
            Column(m.heightIn(min = 44.dp).semantics(mergeDescendants = true) { heading() }, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(med.name, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                Text(cardDetail(s, pet.name), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
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
fun MedAddScreen(pets: List<Pet>, current: Pet?, onBack: () -> Unit) {
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
            ScheduleFields(
                petIds = listOfNotNull((current?.takeIf { it.memorializedAt == null } ?: alive.firstOrNull())?.id),
                startDate = LocalDate.parse(OkcheDay.of(System.currentTimeMillis())),
            ),
        )
    }
    var errors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var adding by rememberSaveable { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val anchors = remember { app.okche.ui.pet.Anchors() }
    val listed = meds.filter { it.deletedAt == null }.sortedBy { it.createdAt }
    // 고른 약의 단위 — 고를 때 기억해 둔다(방금 들인 약이 목록에 아직 안 떠도 맞게).
    var pickedUnit by remember { mutableStateOf<app.okche.store.DoseUnit?>(null) }
    // shortcut: 화면을 돌리면 적던 칸이 처음 값으로 돌아감, 불편하다는 말이 나오면 칸을 rememberSaveable로.
    val unit = pickedUnit ?: meds.firstOrNull { it.id == f.medicationId }?.unit ?: app.okche.store.DoseUnit.pill

    fun edit(key: String, change: ScheduleFields.() -> ScheduleFields) {
        f = f.change()
        errors = errors - key
    }

    fun pick(med: Medication) {
        pickedUnit = med.unit
        f = f.copy(medicationId = med.id, dose = medNumber(med.doseAmount)) // 그 약의 '한 번에'
        errors = errors - "med" - "dose"
    }

    fun save() {
        if (busy) return
        errors = scheduleErrors(f)
        if (errors.isNotEmpty()) {
            // 화면 위에서부터 첫 오류 칸으로 옮겨 가 눈에 보이게.
            val first = errors.keys.first()
            scope.launch { anchors.goTo(first, focusable = false) }
            view.announceForAccessibility(if (errors.size == 1) errors.getValue(first) else "고칠 곳 ${errors.size}개. ${errors.getValue(first)}")
            return
        }
        busy = true
        scope.launch {
            failed = runCatching { addSchedules(dao, f, unit) }.isFailure
            busy = false
            if (failed) view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.") else onBack()
        }
    }

    if (adding) MedFormSheet(null, pets, records, picking = true, onAdded = { pick(it) }) { adding = false }
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("탕약", "약 추가", Icons.AutoMirrored.Outlined.ArrowBack, "탕약으로 돌아가기", onBack)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 8.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Column(anchors.of("pets"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OkcheMultiChips("누가 먹나요?", alive.map { it.id to it.name }, f.petIds, { id ->
                    edit("pets") { copy(petIds = if (id in petIds) petIds - id else petIds + id) }
                }, hint = "(여럿 고를 수 있어요)")
                Note("여럿 고르면 아이마다 따로 만들어져요. 양·시간은 나중에 아이마다 고칠 수 있어요.")
                errors["pets"]?.let { OkcheErrorText(it) }
            }
            Column(anchors.of("med"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                f.dose, { v -> edit("dose") { copy(dose = v) } }, "한 번에 얼마나", Modifier.width(160.dp).then(anchors.of("dose")), suffix = unitTitle(unit), description = unitTitle(unit),
                error = errors["dose"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
            Frequency(f, errors, anchors, ::edit)
            OkcheMenuPicker("먹이는 방법", howTos, f.howTo, { v -> edit("howTo") { copy(howTo = v) } }, inline = false, hint = "(선택)")
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton("저장하기", ::save)
        }
    }
}

@Composable
private fun Note(text: String) = Text(text, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))

/** 얼마나 자주 + 주기마다 칸 + 먹일 시간·알림. */
@Composable
private fun Frequency(f: ScheduleFields, errors: Map<String, String>, anchors: app.okche.ui.pet.Anchors, edit: (String, ScheduleFields.() -> ScheduleFields) -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    // 간격 + 날짜 칸: 나란히, 아주 큰 글자에선 위아래로.
    val stacked = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f
    val pair = @Composable { key: String, left: @Composable (Modifier) -> Unit, right: @Composable (Modifier) -> Unit ->
        if (stacked) {
            Column(anchors.of(key), verticalArrangement = Arrangement.spacedBy(12.dp)) { left(Modifier.fillMaxWidth()); right(Modifier.fillMaxWidth()) }
        } else {
            Row(anchors.of(key), horizontalArrangement = Arrangement.spacedBy(8.dp)) { left(Modifier.width(if (key == "everyDays") 150.dp else 160.dp)); right(Modifier.weight(1f)) }
        }
    }
    val zone = ZoneId.systemDefault()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OkcheChoiceChips("얼마나 자주", medFrequencies, f.frequency, { v -> edit("frequency") { copy(frequency = v) } })
        when (f.frequency) {
            MedFrequency.everyNDays -> {
                pair("everyDays", { m ->
                    OkcheTextField(
                        f.everyDays, { v -> edit("everyDays") { copy(everyDays = v) } }, "간격", m, suffix = "일마다", description = "일마다",
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
                // 스크롤 표시는 늘 있는 칸에(오류 글자는 오류가 날 때 생겨 바로 옮겨 갈 수 없음).
                Column(anchors.of("weekdays"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
                        f.everyHours, { v -> edit("everyHours") { copy(everyHours = v) } }, "간격", m, suffix = "시간마다", description = "시간마다",
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (f.frequency != MedFrequency.everyNHours) {
            OkcheFieldLabel(if (f.frequency == MedFrequency.daily) "먹일 시간" else "먹는 날 먹일 시간", Modifier.semantics { heading() })
            TimeFieldsRow(
                anchors.of("times"),
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
