package app.okche.ui.sillok

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.ui.ButtonLook
import app.okche.ui.LocalUndoInset
import app.okche.ui.OkcheConfirmDialog
import app.okche.ui.goldFrame
import app.okche.ui.record.shiftDay
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.ZoneId

// 실록 탭(보드 Sillok·SillokLocked·SillokEmpty, plan.md 10-1). 계산은 SillokRules.kt. iOS `SillokView`와 같은 화면.
// 보기 설정(10-2)·바꾼 뒤 어때요(10-3)·당뇨 칸(10-4)·PDF(10-5)는 그 단계에서.

/** 지금 보는 아이의 실록. 추억 아이도 그대로(실록 탭은 기록 보기). */
@Composable
fun SillokScreen(pet: Pet, modifier: Modifier = Modifier, seal: @Composable () -> Unit = {}) {
    // 1분마다·앱으로 돌아올 때 — 켜 둔 채 6시를 넘기면 '오늘'이 새 날로(문안과 같음).
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
    val today = OkcheDay.of(now)
    var range by rememberSaveable { mutableStateOf(SillokRange.month) }
    var back by rememberSaveable { mutableIntStateOf(0) }
    var locked by rememberSaveable { mutableStateOf(false) }
    val period = sillokPeriod(today, range.days ?: 30, back)
    val before = period.previous()

    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val records by remember(pet.id, before.start, period.end) { dao.petRecordsFlow(pet.id, before.start, period.end) }.collectAsState(null)
    val plans by remember(pet.id) { dao.mealPlansFlow(pet.id) }.collectAsState(emptyList())
    val rests by remember(pet.id) { dao.petRestPeriodsFlow(pet.id) }.collectAsState(emptyList())
    val foodChanges by remember { dao.foodChangesFlow() }.collectAsState(emptyList())
    val foods by remember { dao.allFoodsFlow() }.collectAsState(emptyList())
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(emptyList())
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())

    Column(
        modifier
            .fillMaxSize()
            .background(OkcheColor.Hanji)
            .verticalScroll(rememberScrollState())
            .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space6, bottom = OkcheSpacing.Space4 + LocalUndoInset.current),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
    ) {
        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), horizontalArrangement = Arrangement.End) { seal() }
        Header(pet, period, range)
        RangeChips(range, onPick = { range = it; back = 0 }, onLocked = { locked = true })
        PeriodNav(period, range, onPrev = { back += 1 }, onNext = { if (back > 0) back -= 1 })
        val all = records ?: return@Column // 읽기 전엔 아래를 비워 둔다('기록이 없어요'가 스치지 않게).
        val now30 = all.filter { period.contains(it.day) }
        val prev30 = all.filter { before.contains(it.day) }
        if (now30.isEmpty()) {
            Text(
                "이 기간에 남긴 기록이 없어요. 일기에서 기록을 남기면 여기에 모여요.",
                Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(OkcheSpacing.Space4),
                color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(),
            )
        } else {
            val changes = sillokChanges(pet, foodChanges, foods, schedules, meds, period)
            Summary(now30, prev30, plans, rests, pet.id, period, range, now)
            VomitChart(now30, period, changes)
            stoolShares(now30)?.let { StoolCard(it) }
            urineSummary(now30, period.days)?.let { u ->
                UrineCard(u, urineSummary(prev30, before.days)?.perDay?.takeIf { prev30.isNotEmpty() }, range)
            }
            weightPoints(now30).takeIf { it.isNotEmpty() }?.let { WeightCard(it, period) }
            if (changes.isNotEmpty()) ChangesCard(changes)
        }
    }

    // 자물쇠 안내 창(보드 SillokLocked) — '알아보기'는 14단계(결제) 전까지 숨김.
    if (locked) {
        OkcheConfirmDialog(
            "여러 달은 전체 기간 열기로 볼 수 있어요", "그냥 둘게요",
            onCancel = { locked = false }, onConfirm = { locked = false },
            text = "무료로는 한 번에 30일씩 봐요. 지난 기록도 화살표로 30일씩 넘기면 다 볼 수 있어요.",
            message = "3달·1년·전체를 한눈에 보거나 바꾼 뒤 4주를 비교하려면 전체 기간 열기(한 번 결제)가 필요해요.",
            showsCancel = false, confirmLook = ButtonLook.Outlined,
        )
    }
}

/** 남색 금테 머리: '콩이실록 최근 30일' + '9/26 – 10/25'. 화면 읽기는 한 머리말. */
@Composable
private fun Header(pet: Pet, period: SillokPeriod, range: SillokRange) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 104.dp)
            .background(OkcheColor.NamsaekDeep)
            .goldFrame(inset = 12f, stroke = 1.1f) { size ->
                Brush.linearGradient(
                    0f to OkcheColor.GeumLight, 0.5f to OkcheColor.Geum, 1f to OkcheColor.GeumShade,
                    start = Offset.Zero, end = Offset(size.width, size.height),
                )
            }
            .padding(horizontal = 28.dp, vertical = 21.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), itemVerticalAlignment = Alignment.CenterVertically) {
            Text("${pet.name}실록", Modifier.alignByBaseline(), color = OkcheColor.GeumLight, style = OkcheType.Display.toTextStyle())
            Text(
                periodHeadline(period, range), Modifier.alignByBaseline(), color = OkcheColor.Hanji,
                style = OkcheType.Display.toTextStyle().copy(fontSize = 18.sp, lineHeight = 26.sp),
            )
        }
        Text(periodText(period), color = OkcheColor.OnDeepMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
    }
}

/** 빠른 선택: 1주·30일은 고르기, 3달·1년·전체는 자물쇠(누르면 안내 창). */
@Composable
private fun RangeChips(range: SillokRange, onPick: (SillokRange) -> Unit, onLocked: () -> Unit) {
    FlowRow(
        Modifier.selectableGroup().semantics { contentDescription = "기간" },
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
    ) {
        for (r in SillokRange.entries) {
            val isLocked = r.days == null
            val isSelected = r == range
            val shape = RoundedCornerShape(OkcheRadius.RadiusPill)
            val action = if (isLocked) {
                Modifier.clickable(role = Role.Button, onClickLabel = "안내 보기", onClick = onLocked)
                    .semantics { contentDescription = "${r.title} · 잠김" }
            } else {
                Modifier.selectable(isSelected, role = Role.RadioButton, onClick = { onPick(r) })
            }
            // 누르는 자리 44 — 보이는 알약 40 위아래 2씩.
            Box(Modifier.clip(shape).then(action).padding(vertical = 2.dp)) {
                Row(
                    Modifier
                        .height(40.dp)
                        .then(
                            when {
                                isSelected -> Modifier.background(OkcheColor.Namsaek, shape)
                                isLocked -> Modifier.background(OkcheColor.Paper, shape).border(1.5.dp, OkcheColor.Line, shape)
                                else -> Modifier.background(OkcheColor.Paper, shape).border(1.5.dp, OkcheColor.LineStrong, shape)
                            },
                        )
                        .padding(horizontal = if (isLocked) 12.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (isLocked) Icon(Icons.Outlined.Lock, null, Modifier.size(14.dp), tint = OkcheColor.InkMuted)
                    Text(
                        r.title,
                        color = when {
                            isSelected -> OkcheColor.Paper
                            isLocked -> OkcheColor.InkMuted
                            else -> OkcheColor.Ink
                        },
                        style = OkcheType.Body.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

/** ‹ 이전 · '9/26 – 10/25' · 다음 › (오늘이 든 기간이면 다음 안 눌림). '보기 설정'은 10-2. */
@Composable
private fun PeriodNav(period: SillokPeriod, range: SillokRange, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
        RoundArrow(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "이전 ${range.title} 보기", true, onPrev)
        Text(periodText(period), Modifier.weight(1f), textAlign = TextAlign.Center, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        RoundArrow(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "다음 ${range.title} 보기", period.back > 0, onNext)
    }
}

@Composable
private fun RoundArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    // 보이는 동그라미 40, 누르는 자리 44.
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(40.dp).alpha(if (enabled) 1f else 0.35f).background(OkcheColor.Paper, CircleShape).border(1.5.dp, OkcheColor.LineStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(20.dp), tint = OkcheColor.Ink) }
    }
}

/** 요약 세 칸: 구토·묽은 변·다 먹은 끼니(끼니 차례가 없던 기간이면 두 칸). */
@Composable
private fun Summary(
    now: List<app.okche.store.Record>, prev: List<app.okche.store.Record>, plans: List<app.okche.store.MealPlan>,
    rests: List<app.okche.store.RestPeriod>, petId: String, period: SillokPeriod, range: SillokRange, nowMs: Long,
) {
    val hasPrev = prev.isNotEmpty()
    val vomit = vomitCount(now)
    val loose = looseStoolCount(now)
    val meal = mealFraction(now, plans, rests, petId, period, nowMs)
    // 칸 높이를 가장 큰 칸에 맞춤.
    Row(Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
        SummaryCell("구토", "$vomit", "회", compareText(vomit, vomitCount(prev), hasPrev, range))
        SummaryCell("묽은 변", "$loose", "회", compareText(loose, looseStoolCount(prev), hasPrev, range))
        meal?.let { (eaten, total) -> SummaryCell("다 먹은 끼니", "$eaten", "/$total", null, easy = true) }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryCell(label: String, value: String, unit: String, compare: String?, easy: Boolean = false) {
    Column(
        Modifier.weight(1f).fillMaxHeight().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(OkcheSpacing.Space3)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        Text(
            buildAnnotatedString {
                append(value)
                withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)) { append(unit) }
            },
            color = OkcheColor.Ink, style = OkcheType.Number.toTextStyle().copy(fontSize = 28.sp, lineHeight = 34.sp),
        )
        when {
            easy -> Text(easyWord("수라", "식사"), color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
            // 좋다·나쁘다 색 없이 남색 한 가지(늘었·줄었), 같으면 회색(보드).
            compare != null -> Text(
                compare,
                color = if (compare.endsWith("같아요")) OkcheColor.InkMuted else OkcheColor.NamsaekDeep,
                style = OkcheType.Caption.toTextStyle(if (compare.endsWith("같아요")) FontWeight.Normal else FontWeight.Bold),
            )
        }
    }
}

/** '매화 · 변 상태'처럼 쉬운 말을 0.85배 회색으로. */
private fun easyWord(word: String, easy: String) = buildAnnotatedString {
    append(word)
    withStyle(SpanStyle(fontSize = 0.85.em, fontWeight = FontWeight.Normal, color = OkcheColor.InkMuted)) { append(" · $easy") }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(OkcheSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
        content = content,
    )
}

@Composable
private fun CardTitle(text: androidx.compose.ui.text.AnnotatedString) {
    Text(text, Modifier.semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
}

private val BAR_AREA = 84.dp
private val LABEL_TOP = 18.dp

/** 구토 횟수 막대: 하루 단위, 최근 날짜부터 보이고 옆으로 넘기면 지난 날. 바꾼 날 앞에 금색 선과 이름표(선 왼쪽 — 첫 이틀은 오른쪽, 12장). */
@Composable
private fun VomitChart(records: List<app.okche.store.Record>, period: SillokPeriod, changes: List<SillokChange>) {
    val counts = dailyVomits(records, period)
    val top = barTop(counts)
    val marks = changeMarks(changes)
    Card {
        CardTitle(buildAnnotatedString {
            append("구토 횟수 ")
            withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal, color = OkcheColor.InkMuted)) { append("하루 단위") }
        })
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(
                Modifier.padding(top = LABEL_TOP).height(BAR_AREA).clearAndSetSemantics {},
                verticalArrangement = Arrangement.SpaceBetween, horizontalAlignment = Alignment.End,
            ) {
                for (v in listOf(top, top / 2, 0)) Text("$v", color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle().copy(lineHeight = 11.sp))
            }
            val scroll = rememberScrollState()
            // 처음엔 최근 날짜(오른쪽 끝)부터.
            LaunchedEffect(scroll.maxValue, period) { scroll.scrollTo(scroll.maxValue) }
            Row(
                Modifier
                    .weight(1f)
                    .semantics { contentDescription = vomitChartLabel(period, counts.sum(), changes) }
                    .horizontalScroll(scroll)
                    .padding(bottom = 6.dp)
                    .drawBehind {
                        // 가운데·0 눈금 선.
                        for (y in listOf(LABEL_TOP + BAR_AREA / 2, LABEL_TOP + BAR_AREA)) {
                            drawLine(OkcheColor.Line, Offset(0f, y.toPx()), Offset(size.width, y.toPx()), 1.dp.toPx())
                        }
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                period.dayList.forEachIndexed { i, day ->
                    marks[day]?.let { ChangeLine(it, labelRight = i < 2) }
                    DayBar(counts[i], top, dayLabel(day, i))
                }
            }
        }
        Text(
            "최근 날짜부터 보여요. 옆으로 넘기면 지난 날짜가 나와요. 금색 선은 사료·탕약을 바꾼 날이에요.",
            color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle(),
        )
    }
}

/** 막대 아래 날짜: 첫날과 매달 1일은 '10/1', 나머지는 날만. */
private fun dayLabel(day: String, index: Int): String {
    val d = LocalDate.parse(day)
    return if (index == 0 || d.dayOfMonth == 1) "${d.monthValue}/${d.dayOfMonth}" else "${d.dayOfMonth}"
}

@Composable
private fun DayBar(count: Int, top: Int, label: String) {
    Column(
        Modifier.width(22.dp).padding(top = LABEL_TOP).clearAndSetSemantics {},
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.height(BAR_AREA), contentAlignment = Alignment.BottomCenter) {
            if (count == 0) {
                Box(Modifier.width(14.dp).height(2.dp).background(OkcheColor.Line))
            } else {
                Box(Modifier.width(14.dp).height(BAR_AREA * (count.toFloat() / top)).background(OkcheColor.Namsaek, RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)))
            }
        }
        Text(label, color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle().copy(lineHeight = 14.sp), maxLines = 1, softWrap = false)
    }
}

/** 금색 세로선(2 × 84)과 이름표 — 이름표는 자리를 차지하지 않고 선 옆에 그린다. */
@Composable
private fun ChangeLine(label: String, labelRight: Boolean) {
    Box(Modifier.padding(top = LABEL_TOP).width(2.dp).height(BAR_AREA).background(OkcheColor.Geum).clearAndSetSemantics {}) {
        Text(
            label,
            Modifier.wrapLabel(labelRight),
            color = OkcheColor.GeumDeep, style = OkcheType.Caption.toTextStyle(FontWeight.Bold), maxLines = 1, softWrap = false,
        )
    }
}

/** 이름표를 선 위(18 위)·왼쪽(또는 오른쪽)으로 4 떨어져 놓는다. 부모 폭(2)에 묶이지 않게 재고 그 자리에 그린다. */
private fun Modifier.wrapLabel(right: Boolean) = layout { measurable, _ ->
    val placeable = measurable.measure(androidx.compose.ui.unit.Constraints())
    val gap = 4.dp.roundToPx()
    layout(0, 0) {
        val x = if (right) 2.dp.roundToPx() + gap else -(placeable.width + gap)
        placeable.place(x, -LABEL_TOP.roundToPx())
    }
}

/** 매화 변 상태 비율 막대 + 범례. */
@Composable
private fun StoolCard(shares: List<Pair<StoolBand, Int>>) {
    val colors = mapOf(
        StoolBand.hard to OkcheColor.LineStrong, StoolBand.normal to OkcheColor.NamsaekDeep,
        StoolBand.soft to OkcheColor.Namsaek, StoolBand.watery to OkcheColor.NamsaekSoft,
    )
    Card {
        CardTitle(easyWord("매화", "변 상태"))
        Row(
            Modifier.fillMaxWidth().height(20.dp).clip(RoundedCornerShape(OkcheRadius.RadiusPill))
                .border(1.dp, OkcheColor.LineStrong, RoundedCornerShape(OkcheRadius.RadiusPill)).clearAndSetSemantics {},
        ) {
            for ((band, pct) in shares) if (pct > 0) Box(Modifier.weight(pct.toFloat()).fillMaxHeight().background(colors.getValue(band)))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for ((band, pct) in shares) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val swatch = Modifier.size(12.dp).background(colors.getValue(band), RoundedCornerShape(3.dp))
                    Box(if (band == StoolBand.watery) swatch.border(1.dp, OkcheColor.LineStrong, RoundedCornerShape(3.dp)) else swatch)
                    Text("${band.title} $pct%", color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                }
            }
        }
    }
}

/** 소피: 하루 평균 n번 + 직전 기간 평균 + 찔끔·분홍·붉은색·힘만 줌 횟수(안내 문구 없음 — 정한 것 ②). */
@Composable
private fun UrineCard(u: UrineSummary, before: Double?, range: SillokRange) {
    Card {
        CardTitle(easyWord("소피", "소변"))
        FlowRow(itemVerticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
            Text(perDayText(u.perDay), Modifier.alignByBaseline(), color = OkcheColor.Ink, style = OkcheType.Number.toTextStyle())
            Text("번 / 하루 평균", Modifier.alignByBaseline(), color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            if (before != null) {
                Spacer(Modifier.weight(1f))
                Text("직전 ${range.title} ${perDayText(before)}번", Modifier.alignByBaseline(), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            }
        }
        Text(
            "찔끔 ${u.little}번 · 분홍·붉은색 ${u.pinkRed}번 · 힘만 줌 ${u.straining}번",
            color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
        )
    }
}

/** 옥체 체중선: 기간 안 체중 기록, 마지막 값 크게. 기록 하나면 점 하나. */
@Composable
private fun WeightCard(points: List<Pair<Long, Long>>, period: SillokPeriod) {
    val zone = ZoneId.systemDefault()
    val from = LocalDate.parse(period.start).atTime(OkcheDay.START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val to = LocalDate.parse(shiftDay(period.end, 1)).atTime(OkcheDay.START_HOUR, 0).atZone(zone).toInstant().toEpochMilli()
    val last = points.last().second
    Card {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            CardTitle(easyWord("옥체", "체중"))
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontSize = 32.sp, fontWeight = FontWeight.Bold)) { append(kgNumber(last)) }
                    append(" kg")
                },
                color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
        }
        val label = if (points.size == 1) "체중 ${kgNumber(last)}kg" else "체중 변화: ${kgNumber(points.first().second)}kg에서 ${kgNumber(last)}kg"
        val lo = points.minOf { it.second }
        val hi = points.maxOf { it.second }
        Canvas(Modifier.fillMaxWidth().height(90.dp).semantics { contentDescription = label; role = Role.Image }) {
            drawLine(OkcheColor.Line, Offset(0f, 80.dp.toPx()), Offset(size.width, 80.dp.toPx()), 1.dp.toPx())
            val pad = 10.dp.toPx()
            fun x(t: Long) = pad + (size.width - 2 * pad) * ((t - from).toFloat() / (to - from).coerceAtLeast(1)).coerceIn(0f, 1f)
            fun y(g: Long) = if (hi == lo) 40.dp.toPx() else (60.dp - 40.dp * ((g - lo).toFloat() / (hi - lo))).toPx()
            if (points.size > 1) {
                val path = Path().apply {
                    points.forEachIndexed { i, (t, g) -> if (i == 0) moveTo(x(t), y(g)) else lineTo(x(t), y(g)) }
                }
                drawPath(path, OkcheColor.Namsaek, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
            drawCircle(OkcheColor.Namsaek, 5.dp.toPx(), Offset(x(points.last().first), y(last)))
        }
        Row(Modifier.fillMaxWidth().clearAndSetSemantics {}, horizontalArrangement = Arrangement.SpaceBetween) {
            for (t in weightAxis(period)) Text(t, color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
        }
    }
}

/** 이 기간에 바뀐 것(6-4) — '바꾼 뒤 어때요 ›'는 10-3에서. */
@Composable
private fun ChangesCard(changes: List<SillokChange>) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(OkcheSpacing.Space4),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
    ) {
        CardTitle(buildAnnotatedString { append("이 기간에 바뀐 것") })
        changes.forEachIndexed { i, c ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
            Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) { contentDescription = c.text },
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            ) {
                Box(Modifier.size(10.dp).background(OkcheColor.Geum, CircleShape))
                Text(c.text, color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            }
        }
    }
}
