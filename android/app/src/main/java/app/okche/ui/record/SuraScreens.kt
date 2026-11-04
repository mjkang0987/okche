package app.okche.ui.record

import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.dismissMealHint
import app.okche.store.mealHintDismissed
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkchePickerField
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheTextField
import app.okche.ui.pet.FormHeader
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 수라 화면(보드 Sura·SuraNoPlan·Feeder): 급여 방식, 끼니 시간, 끼니 칸(또는 끼니 없이 '지금 먹은 것'). 지금 보는 아이를 따른다.
 * 사료 이름·다과는 7-4. 하단 탭 없음. iOS `SuraScreen`과 같은 짜임.
 */
@Composable
fun SuraScreen(pet: Pet?, seal: @Composable () -> Unit, onBack: () -> Unit, openedFor: String? = null) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val petId = pet?.takeIf { it.memorializedAt == null }?.id
    // 1분마다 — 켜 둔 채 시간이 지나도 '아직 시간 전'·오늘(6시 기준)·급식기 자동 기록이 따라가게.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000)
            value = System.currentTimeMillis()
            runCatching { fillFeeder(dao) }
        }
    }
    val today = OkcheDay.of(now)
    val plans by remember(petId) { petId?.let { dao.mealPlansFlow(it) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    // 끼니 칸은 차례 키로 짝지어서 6시 전에 미리 남긴 6시대 끼니(전날 day)도 찾는다. '오늘 남긴 수라'는 오늘 것만.
    val meals by remember(petId, today) { petId?.let { dao.recentMealsFlow(it, today, shiftDay(today, -1)) } ?: flowOf(emptyList()) }.collectAsState(emptyList())
    val dismissed by remember { context.mealHintDismissed }.collectAsState(null)
    var editing by remember { mutableStateOf<MealMode?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { fillFeeder(dao) } }
    // 볼 아이가 없어지면(추억으로 간직·지우기·추억 아이 고름) 빈 화면 대신 닫는다.
    // 연필로 다른 아이 수라를 열었으면 지금 보는 아이가 그 아이로 바뀐 뒤부터 본다(바뀌기 전 추억 아이로 닫히지 않게).
    // 지금 보는 아이가 바뀌기만 해도 도착으로 본다(연 아이가 지워졌거나 저장이 실패해 다른 아이로 바뀐 경우에도 빈 화면에 남지 않게).
    var arrived by remember { mutableStateOf(openedFor == null) }
    val firstId = remember { pet?.id }
    LaunchedEffect(pet, petId) {
        if (!arrived && (pet?.id == openedFor || pet?.id != firstId)) arrived = true
        if (arrived && pet != null && petId == null) onBack()
    }
    var busy by remember { mutableStateOf(false) }

    // 저장 하나가 끝나기 전엔 다음 누름을 받지 않는다(빨리 두 번 눌러 한 차례에 기록이 둘 생기지 않게).
    fun run(action: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            runCatching { action() }
                .onSuccess { failed = false }
                .onFailure {
                    failed = true
                    @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                }
            busy = false
        }
    }

    val plan = openMealPlan(plans)
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("수라", "식사량", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack, trailing = seal)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 8.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            if (petId != null) {
                val mode = plan?.mode ?: MealMode.direct
                ModePicker(mode) { picked ->
                    if (picked == mode) return@ModePicker
                    if (plan != null) {
                        run { saveMealPlan(dao, petId, picked, plan.slots) }
                    } else if (picked == MealMode.feeder) {
                        editing = MealMode.feeder // 급식기는 끼니가 있어야 해서 시간 시트를 먼저 — 저장해야 바뀜
                    }
                }
                if (plan != null) {
                    PlanRow(plan) { editing = plan.mode }
                    // 그날 계획을 바꿨으면 바꾸기 전 끼니는 앞 계획의 차례로 보인다.
                    val slots = mealOccurrences(plans, today)
                    if (slots.isEmpty()) {
                        // 오늘 처음 정해 끼니가 모두 지났을 때('아직 지나지 않은 끼니부터 적용').
                        Text(if (plan.mode == MealMode.feeder) "오늘은 남은 끼니가 없어요. 내일부터 끼니 시간에 기록돼요." else "오늘은 남은 끼니가 없어요. 내일부터 끼니마다 남겨요.", color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle())
                    }
                    for (slot in slots) {
                        MealCard(slot, now, meals.firstOrNull { it.slotKey == slot.key },
                            onPick = { result -> run { pickMeal(dao, result, slot, petId) } },
                            // 남은 양은 바로 저장(다른 저장 중이라고 버리지 않게 `run`의 막기를 거치지 않음).
                            onLeft = { record, grams ->
                                try {
                                    setLeftGrams(dao, record, grams)
                                    failed = false
                                } catch (e: CancellationException) {
                                    throw e // 다음 글자로 취소된 것 — 실패가 아님
                                } catch (e: Exception) {
                                    failed = true
                                    @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                                }
                            })
                    }
                } else {
                    if (dismissed != null && petId !in dismissed.orEmpty()) {
                        Hint(onSet = { editing = MealMode.direct }, onDismiss = {
                            scope.launch { context.dismissMealHint(petId) }
                            @Suppress("DEPRECATION") view.announceForAccessibility("안내를 닫았어요")
                        })
                    }
                    NowCard { result -> run { pickMealNow(dao, result, petId) } }
                    val todays = meals.filter { it.day == today }
                    if (todays.isNotEmpty()) TodayMeals(todays) { record, result -> run { setMealResult(dao, record, result) } }
                    Text(
                        "끼니 시간 정하기 ›",
                        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { editing = MealMode.direct }.wrapContentHeight(Alignment.CenterVertically),
                        color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
                    )
                }
                if (mode == MealMode.direct) {
                    Text(
                        "그램은 넣고 싶을 때만 넣어도 돼요. 평소만큼 먹었는지만 남겨도 실록에서 흐름을 보여 드려요.",
                        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 14.dp, vertical = 12.dp),
                        color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                    )
                }
            }
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
        }
    }
    val sheetMode = editing
    if (sheetMode != null && petId != null) MealTimeSheet(petId, sheetMode, plan) { editing = null }
}

/** 급여 방식 고르기(보드 모양 — 연한 남색 판 안 두 칸). 화면 읽기는 라디오 묶음. */
@Composable
private fun ModePicker(mode: MealMode, onPick: (MealMode) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(4.dp)
            .selectableGroup().semantics { contentDescription = "급여 방식" },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for ((value, title) in listOf(MealMode.direct to "직접 급여", MealMode.feeder to "급식기 (수라간)")) {
            val on = value == mode
            val shape = RoundedCornerShape(OkcheRadius.RadiusSm)
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(shape)
                    .then(if (on) Modifier.background(OkcheColor.Namsaek, shape) else Modifier)
                    .selectable(on, role = Role.RadioButton) { onPick(value) },
                contentAlignment = Alignment.Center,
            ) {
                Text(title, color = if (on) OkcheColor.Paper else OkcheColor.NamsaekDeep, style = OkcheType.Body.toTextStyle(if (on) FontWeight.Bold else FontWeight.Medium), textAlign = TextAlign.Center)
            }
        }
    }
}

/** '끼니 시간 08:00 · 18:00 바꾸기' / '급식기 시간 … 바꾸기' + 급식기 설명. */
@Composable
private fun PlanRow(plan: MealPlan, onChange: () -> Unit) {
    val feeder = plan.mode == MealMode.feeder
    val label = if (feeder) "급식기 시간" else "끼니 시간"
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("$label ") }
                    withStyle(SpanStyle(color = OkcheColor.InkMuted)) { append(plan.slots.joinToString(" · ") { it.time }) }
                },
                Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
            )
            PillButton("바꾸기", "$label 바꾸기", onChange)
        }
        if (feeder) {
            Text("이 시간이 되면 끼니가 '다 먹음'으로 저절로 기록돼요. 남기거나 안 먹은 끼니만 바꿔 주세요.", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
    }
}

@Composable
private fun PillButton(text: String, label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OkcheRadius.RadiusPill)
    Box(Modifier.heightIn(min = 44.dp).clip(shape).clickable(role = Role.Button, onClick = onClick).semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Text(
            text,
            Modifier.heightIn(min = 40.dp).background(OkcheColor.Paper, shape).border(1.5.dp, OkcheColor.LineStrong, shape).padding(horizontal = 12.dp).wrapContentHeight(Alignment.CenterVertically),
            color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
        )
    }
}

/** 끼니 칸: 이름(급식기는 시각)·준 양 + 상태, 다 먹음·남김·안 먹음(기존 고르기 카드), 남김이면 '남은 양 (g, 선택)'. */
@Composable
private fun MealCard(slot: MealOccurrence, now: Long, record: Record?, onPick: (String) -> Unit, onLeft: suspend (Record, Int?) -> Unit) {
    val feeder = slot.mode == MealMode.feeder
    val waiting = feeder && slot.at > now && record == null
    val result = record?.let(::mealResult)
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val detail = listOfNotNull(if (feeder) slot.slot.time else null, slot.slot.grams?.let { "${it}g" }).joinToString(" · ")
            Text(
                buildAnnotatedString {
                    append(slot.slot.name)
                    if (detail.isNotEmpty()) withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OkcheColor.InkMuted)) { append(" $detail") }
                },
                Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
            )
            val status = when {
                waiting -> "아직 시간 전"
                record == null -> "아직 기록 전"
                record.auto -> "자동 기록됨"
                else -> recordTime(record)
            }
            Text(status, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        if (!waiting) {
            OkcheChoiceCards("${slot.slot.name} 식사량", mealResults, result, onPick, hideLabel = true)
            if (result == "some" && record != null) {
                // 결과가 바뀌면 기록에서 다시 읽는다(다 먹음 → 남김으로 돌아오면 지운 값이 남지 않게).
                var left by remember(record.id, result) { mutableStateOf(leftGrams(record)?.toString().orEmpty()) }
                var error by remember(record.id, result) { mutableStateOf<String?>(null) }
                val given = givenGrams(record)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("남은 양")
                            withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(" (g, 선택)") }
                        },
                        Modifier.weight(1f), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(),
                    )
                    // 글자가 바뀔 때마다 앞 저장을 취소하고 마지막 값만 저장(따로 도는 저장의 순서가 바뀌어 앞 숫자가 남지 않게).
                    LaunchedEffect(left) {
                        if (left == leftGrams(record)?.toString().orEmpty()) {
                            error = null
                            return@LaunchedEffect
                        }
                        val v = left
                        val grams = v.trim().toIntOrNull()
                        when {
                                v.isBlank() -> { error = null; onLeft(record, null) }
                                grams != null && grams in 0..(given ?: 2000) -> { error = null; onLeft(record, grams) }
                                else -> {
                                    // 잘못된 값이면 치는 중 저장된 앞 숫자(30을 치다 남은 3)가 남지 않게 비운다. 이미 비어 있으면 다시 쓰지 않음.
                                    error = given?.let { "준 양(${it}g)보다 많을 수 없어요" } ?: "0에서 2000 사이 숫자로 넣어 주세요"
                                    if (leftGrams(record) != null) onLeft(record, null)
                                }
                            }
                    }
                    OkcheTextField(
                        left,
                        { left = it },
                        "${slot.slot.name} 남은 양 (그램, 선택)",
                        Modifier.width(120.dp),
                        showLabel = false, suffix = "g", suffixStyle = OkcheType.Label, error = error, showError = false,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                error?.let { OkcheErrorText(it) }
            }
        }
    }
}

/** 끼니 안내(보드 SuraNoPlan) — 한 번만, '괜찮아요'면 그 아이는 다시 안 뜸. */
@Composable
private fun Hint(onSet: () -> Unit, onDismiss: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("끼니 시간을 정해 둘까요?", Modifier.semantics { heading() }, color = OkcheColor.NamsaekDeep, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        Text("정해 두면 아침·저녁처럼 끼니마다 남기고, 실록에서 다 먹은 끼니를 세어 드려요. 정하지 않으면 남긴 때를 먹은 때로 적어요.", color = OkcheColor.NamsaekDeep, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        if (LocalDensity.current.fontScale >= 1.15f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OkchePrimaryButton("끼니 시간 정하기", onSet)
                OkchePrimaryButton("괜찮아요", onDismiss, look = ButtonLook.Outlined)
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OkchePrimaryButton("끼니 시간 정하기", onSet, Modifier.weight(1f))
                OkchePrimaryButton("괜찮아요", onDismiss, Modifier.weight(1f), look = ButtonLook.Outlined)
            }
        }
    }
}

/** 끼니 없이 '지금 먹은 것': 누르면 지금 시각으로. */
@Composable
private fun NowCard(onPick: (String) -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("지금 먹은 것", Modifier.semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((value, title) in mealResults) {
                val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
                Box(
                    Modifier.weight(1f).heightIn(min = 52.dp).clip(shape).background(OkcheColor.Paper, shape).border(1.5.dp, OkcheColor.Line, shape)
                        .clickable(role = Role.Button) { onPick(value) }.semantics { contentDescription = "지금 $title" },
                    contentAlignment = Alignment.Center,
                ) { Text(title, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium), textAlign = TextAlign.Center) }
            }
        }
    }
}

/** '오늘 남긴 수라' 목록(시각 · 결과). 줄을 누르면 결과 바꾸기(끼니 없이 남긴 수라 고치기 — 2026-11-04 사용자). */
@Composable
private fun TodayMeals(meals: List<Record>, onChange: (Record, String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(start = 16.dp, end = 16.dp, bottom = 6.dp)) {
        Text("오늘 남긴 수라", Modifier.padding(top = 10.dp, bottom = 4.dp).semantics { heading() }, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle())
        meals.forEachIndexed { i, record ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
            var open by remember(record.id) { mutableStateOf(false) }
            Box {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(role = Role.Button, onClickLabel = "결과 바꾸기") { open = true }
                        .semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(recordTime(record), Modifier.weight(1f), color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle())
                    Text(recordValue(record), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
                }
                DropdownMenu(open, { open = false }) {
                    for ((value, title) in mealResults) {
                        DropdownMenuItem({ Text(title, style = OkcheType.Body.toTextStyle()) }, { open = false; onChange(record, value) })
                    }
                }
            }
        }
    }
}

/** 끼니 시간 시트(보드 SuraMealTime·FeederTime): 끼니 줄(자동 이름·시간·준 양 g·빼기), '+ 끼니 시간 추가', 저장. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealTimeSheet(petId: String, mode: MealMode, plan: MealPlan?, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val feeder = mode == MealMode.feeder
    // 줄: 시간·준 양 글자. 처음 값은 지금 계획, 없으면 08:00·18:00.
    val rows = remember {
        mutableStateListOf<Pair<String, String>>().apply {
            addAll((plan?.slots ?: listOf(MealSlot("", "08:00"), MealSlot("", "18:00"))).sortedBy { dayOrder(it.time) }.map { it.time to (it.grams?.toString().orEmpty()) })
        }
    }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    fun fail(message: String) {
        error = message
        @Suppress("DEPRECATION") view.announceForAccessibility(message)
    }

    fun save() {
        if (busy) return
        if (rows.map { it.first }.toSet().size != rows.size) return fail("같은 시간의 끼니가 있어요. 시간을 바꿔 주세요.")
        if (feeder && rows.isEmpty()) return fail("급식기는 끼니 시간을 하나 이상 넣어 주세요.")
        val slots = rows.map { (time, g) ->
            val text = g.trim()
            val grams = text.toIntOrNull()
            if (text.isNotEmpty() && (grams == null || grams !in 0..2000)) return fail("준 양은 0에서 2000 사이 숫자로 넣어 주세요.")
            MealSlot("", time, if (text.isEmpty()) null else grams)
        }
        busy = true
        scope.launch {
            runCatching {
                saveMealPlan(dao, petId, mode, slots)
                runCatching { fillFeeder(dao) } // 채우기가 실패해도 계획은 저장됐다(다음에 앱을 열 때 다시 채움)
            }.onSuccess {
                @Suppress("DEPRECATION") view.announceForAccessibility("끼니 시간을 저장했어요")
                onClose()
            }.onFailure {
                busy = false
                fail("저장하지 못했어요. 다시 해 주세요.")
            }
        }
    }

    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append(if (feeder) "수라간 " else "수라 ")
                        withStyle(SpanStyle(fontSize = 16.sp, fontWeight = FontWeight.Medium, color = OkcheColor.InkMuted)) { append(if (feeder) "급식기 시간" else "끼니 시간") }
                    },
                    Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
                )
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            Text(
                if (feeder) "급식기에 맞춰 둔 시간을 똑같이 넣어 주세요. 이 시간이 되면 그 끼니가 '다 먹음'으로 기록돼요. 준 양(g)은 넣고 싶을 때만 넣어 주세요."
                else "주로 먹이는 끼니와 시간을 넣어 주세요. 시간이 조금 달라도 그 앞뒤에 남긴 수라는 그 끼니로 들어가요. 준 양(g)은 넣고 싶을 때만 넣어 주세요.",
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
            if (rows.isNotEmpty()) {
                val names = mealNames(rows.size)
                val stacked = LocalDensity.current.fontScale >= 1.3f
                Column(Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(horizontal = 16.dp)) {
                    rows.forEachIndexed { i, (time, grams) ->
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
                        val name = names[i]
                        val timeField = @Composable { m: Modifier ->
                            OkchePickerField(time, "$name 시간", onClick = {
                                val t = runCatching { java.time.LocalTime.parse(time) }.getOrDefault(java.time.LocalTime.NOON) // 다른 기기에서 온 틀린 글자
                                TimePickerDialog(context, { _, h, min ->
                                    rows[i] = "%02d:%02d".format(h, min) to rows[i].second
                                    rows.sortBy { dayOrder(it.first) }
                                    error = null
                                }, t.hour, t.minute, DateFormat.is24HourFormat(context)).show()
                            }, m, showLabel = false, icon = Icons.Outlined.Schedule)
                        }
                        val gramsField = @Composable { m: Modifier ->
                            OkcheTextField(grams, { v -> rows[i] = rows[i].first to v; error = null }, "$name 준 양 (그램)", m,
                                showLabel = false, placeholder = "양", suffix = "g", suffixStyle = OkcheType.Label, showError = false,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                        }
                        val removable = !(feeder && rows.size == 1)
                        val remove = @Composable {
                            IconButton({ rows.removeAt(i) }, Modifier.size(44.dp), enabled = removable) {
                                Icon(Icons.Outlined.Close, "$name 끼니 빼기", tint = if (removable) OkcheColor.LineStrong else OkcheColor.Line)
                            }
                        }
                        if (stacked) {
                            Column(Modifier.padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(name, Modifier.weight(1f), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
                                    remove()
                                }
                                timeField(Modifier)
                                gramsField(Modifier)
                            }
                        } else {
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(name, Modifier.weight(1f), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
                                timeField(Modifier.width(120.dp))
                                gramsField(Modifier.width(76.dp))
                                remove()
                            }
                        }
                    }
                }
            }
            Text(
                "+ 끼니 시간 추가",
                Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) {
                    val used = rows.map { it.first }.toSet()
                    rows.add(((0 until 24).map { "%02d:00".format((12 + it) % 24) }.firstOrNull { it !in used } ?: "12:00") to "")
                    rows.sortBy { dayOrder(it.first) }
                }.wrapContentHeight(Alignment.CenterVertically),
                color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
            )
            Text(
                if (feeder) "바꾼 시간은 아직 지나지 않은 끼니부터 바로 적용돼요. 지난 기록은 그대로예요."
                else "바꾼 시간은 아직 지나지 않은 끼니부터 바로 적용돼요. 지난 기록은 그대로예요. 끼니를 모두 빼고 저장하면 끼니 없이 남겨요.",
                Modifier.fillMaxWidth().background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 14.dp, vertical = 12.dp),
                color = OkcheColor.NamsaekDeep, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
            error?.let { OkcheErrorText(it) }
            OkchePrimaryButton("저장하기", ::save)
        }
    }
}
