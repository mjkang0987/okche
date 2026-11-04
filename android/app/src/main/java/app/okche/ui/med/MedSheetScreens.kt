package app.okche.ui.med

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheTextField
import app.okche.ui.food.SnackPickCard
import app.okche.ui.pet.toDecimal
import app.okche.ui.record.TimeRow
import app.okche.ui.record.pickDateTime
import app.okche.ui.record.timeText
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch

// 오늘 먹일 약 시트·예정에 없던 약 시트(plan.md 8-3b, 보드 MedSheet·MedExtra). iOS `MedSheetViews.swift`와 같은 짜임.

val medStatuses = listOf("given" to "줬어요", "skipped" to "건너뜀", "vomited" to "먹고 토함")

/** 시트 머리: 제목(+ 흐린 덧말) + 닫기 44 — 다과 기록 시트 머리와 같은 모양. */
@Composable
private fun SheetHeader(title: String, subtitle: String? = null, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            buildAnnotatedString {
                append(title)
                if (subtitle != null) withStyle(SpanStyle(color = OkcheColor.InkMuted, fontWeight = FontWeight.Medium, fontSize = OkcheType.Body.fontSize)) { append(" $subtitle") }
            },
            Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
        )
        IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) = Text(
    text,
    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 12.dp),
    color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
)

/**
 * 오늘 먹일 약 시트(보드 MedSheet): 오늘의 모든 차례를 먹일 시간으로 묶어 시간 순, 약마다 줬어요·건너뜀·먹고 토함을 골라 두고
 * '기록하기'로 한 번에. 이미 기록한 차례는 고른 채 흐리게(여기서 못 바꿈 — 고치기는 기록 줄 연필).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedTodaySheet(pet: Pet, onSaved: (List<Record>) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(null)
    val records by remember { dao.medRecordsFlow() }.collectAsState(null)
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())
    /** 고른 차례: `slotKey` → 상태. */
    val picks = remember { mutableStateMapOf<String, String>() }
    var extra by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // shortcut: 켜 둔 채 6시를 넘겨도 다시 그리기 전까지 어제 차례가 보임, 일기 탭과 같이 날 바뀜을 다룰 때 함께.
    val entries = if (schedules != null && records != null) todaySlots(schedules!!, records!!, pet.id, OkcheDay.of(System.currentTimeMillis())) else null
    val groups = entries.orEmpty().groupBy { it.slot.due }.toSortedMap()
    val next = groups.entries.firstOrNull { (_, list) -> list.any { it.record == null } }?.key

    fun save() {
        val chosen = entries.orEmpty().filter { it.record == null }.mapNotNull { e -> picks[e.slot.key]?.let { e.slot to it } }
        if (chosen.isEmpty() || busy) return
        busy = true
        scope.launch {
            val saved = runCatching { recordSlots(dao, chosen) }
            busy = false
            failed = saved.isFailure
            if (failed) {
                @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
            } else {
                onSaved(saved.getOrThrow())
                onClose()
            }
        }
    }

    if (extra) MedExtraSheet(pet, onSaved = { onSaved(listOf(it)); onClose() }) { extra = false }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SheetHeader("${pet.name} · 탕약", "오늘 먹일 약", onClose)
            if (entries != null && groups.isEmpty()) {
                Text("오늘 먹일 약이 없어요.", color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(FontWeight.Normal))
            }
            for ((due, list) in groups) {
                val done = list.all { it.record != null }
                val right = if (done) list.mapNotNull { it.record?.occurredAt }.maxOrNull()?.let { "${hhmm(it)}에 기록함" } else if (due == next) "다음 차례" else null
                Column(
                    Modifier.fillMaxWidth().alpha(if (done) 0.7f else 1f)
                        .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(start = 14.dp, end = 14.dp, bottom = 6.dp),
                ) {
                    Row(Modifier.padding(top = 10.dp).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
                        Text(clockText(due), Modifier.weight(1f), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
                        if (right != null) Text(right, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                    }
                    list.forEachIndexed { i, entry ->
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
                        val med = meds.firstOrNull { it.id == entry.slot.schedule.medicationId }
                        val recorded = entry.record?.let { medJson(it).optString("status") }
                        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.semantics(mergeDescendants = true) {}) {
                                Text(med?.name ?: "약", color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
                                Text(slotDetail(entry.slot.schedule, med), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                            }
                            // 묶음 이름(legend)은 화면 읽기만 — '오후 9:00 프레드니솔론'.
                            OkcheChoiceChips(
                                "${clockText(due)} ${med?.name ?: "약"}", medStatuses, recorded ?: picks[entry.slot.key],
                                { picks[entry.slot.key] = it }, onDeselect = { picks.remove(entry.slot.key) }, hideLabel = true,
                                hint = if (recorded != null) "이미 기록했어요. 고치려면 기록 목록에서" else null, enabled = recorded == null,
                            )
                        }
                    }
                }
            }
            LinkText("+ 예정에 없던 약 남기기") { extra = true }
            if (groups.isNotEmpty()) {
                Text("'먹고 토함'은 실록에 따로 세요. 다시 먹여도 되는지는 병원에 물어보길 권해요.", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            }
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            if (groups.isNotEmpty()) OkchePrimaryButton("기록하기", ::save, enabled = picks.isNotEmpty())
        }
    }
}

/** 예정에 없던 약 시트(보드 MedExtra) — 탕약 기록 고치기도 같은 칸(약은 고정, 차례 기록이면 '건너뜀'도). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedExtraSheet(pet: Pet, editing: Record? = null, onSaved: (Record) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(emptyList())
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())
    val records by remember { dao.medRecordsFlow() }.collectAsState(emptyList())
    val start = remember { editing?.let(::medJson) }
    var picked by remember { mutableStateOf(start?.optString("medicationId")?.takeIf { it.isNotEmpty() }) }
    var amount by remember { mutableStateOf(start?.takeIf { it.has("amount") }?.let { medNumber(it.getDouble("amount")) }.orEmpty()) }
    var status by remember { mutableStateOf(start?.optString("status")?.takeIf { it.isNotEmpty() } ?: "given") }
    var occurredAt by remember { mutableLongStateOf(editing?.occurredAt ?: System.currentTimeMillis()) }
    var timeChanged by remember { mutableStateOf(false) }
    var pickError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val statuses = if (editing?.slotKey != null) medStatuses else medStatuses.filter { it.first != "skipped" }
    val options = if (editing == null) extraMedOptions(meds, schedules, pet)
    else meds.filter { it.id == picked }.map { it to medFormTitle(it.form) } // 고치기는 약 고정(약방에서 뺀 약도)
    val med = meds.firstOrNull { it.id == picked }

    fun pick(m: app.okche.store.Medication) {
        picked = m.id
        amount = medNumber(m.doseAmount) // 고르면 '얼마나'는 그 약의 '한 번에'
        pickError = null
        amountError = null
    }

    fun save() {
        val value = amount.toDecimal()
        pickError = if (med == null) "어떤 약인지 골라 주세요" else null
        amountError = if ((value ?: 0.0) > 0) null else "0보다 큰 숫자로 넣어 주세요"
        if (med == null || value == null || value <= 0) {
            @Suppress("DEPRECATION") view.announceForAccessibility(listOfNotNull(pickError, amountError).joinToString(". "))
            return
        }
        if (busy) return
        busy = true
        // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
        val at = if (editing == null && !timeChanged) System.currentTimeMillis() else occurredAt
        scope.launch {
            val saved = runCatching { saveExtraMed(dao, pet.id, med, value, status, at, editing) }
            busy = false
            failed = saved.isFailure
            if (failed) {
                @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
            } else {
                @Suppress("DEPRECATION") view.announceForAccessibility("아뢰었사옵니다")
                onSaved(saved.getOrThrow())
                onClose()
            }
        }
    }

    // 새로 남길 땐 맨 위 약(이 아이의 필요할 때만 약)을 골라 둔다(보드 MedExtra).
    androidx.compose.runtime.LaunchedEffect(options.firstOrNull()?.first?.id) {
        if (editing == null && picked == null) options.firstOrNull()?.let { pick(it.first) }
    }
    if (adding) MedFormSheet(null, listOf(pet), records, picking = true, onAdded = { pick(it) }) { adding = false }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SheetHeader("${pet.name} · ${if (editing == null) "예정에 없던 약" else "탕약 기록"}", onClose = onClose)
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                OkcheFieldLabel("어떤 약", Modifier.semantics { heading() })
                for ((m, detail) in options) {
                    if (editing == null) SnackPickCard(m.name, detail, picked == m.id) { pick(m) }
                    else SnackPickCard(m.name, detail, true) {} // 고정
                }
                if (editing == null) LinkText("+ 약방에 새 약 들이기") { adding = true }
                pickError?.let { OkcheErrorText(it) }
            }
            OkcheTextField(amount, { amount = it }, "얼마나", suffix = med?.let { unitTitle(it.unit) }, error = amountError,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OkcheFieldLabel("언제")
                TimeRow(timeText(occurredAt, editing == null && !timeChanged)) {
                    pickDateTime(context, occurredAt) {
                        occurredAt = it
                        timeChanged = true
                    }
                }
            }
            OkcheChoiceChips("어떻게 됐나요", statuses, status, { status = it })
            if (editing == null) {
                Text("정해 둔 시간 밖에 준 약이라 탕약 횟수(1/3)에는 들어가지 않아요. 약방 남은 양에서는 빠져요.", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            }
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton("기록하기", ::save)
        }
    }
}
