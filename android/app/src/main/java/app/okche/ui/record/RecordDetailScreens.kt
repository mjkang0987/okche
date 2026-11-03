package app.okche.ui.record

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Rectangle
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheMultiChips
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 자세히 시트를 여는 값: 새 기록(종류·아이)이거나 고칠 기록. */
data class DetailTarget(val kind: RecordKind, val editing: Record? = null, val petId: String? = null)

/**
 * 구토·매화·소피 자세히 시트(보드 Vomit·Stool·Urine): 시간, 칸들, 의서 안내, '기록하기'. 고치기도 같은 시트(값이 채워진 채).
 * [onSaved]는 저장한 기록과 새 기록인지.
 */
// shortcut: 시트 상태는 화면 회전·테마 전환(액티비티 다시 만듦) 때 시트와 함께 사라짐(아직 안 저장한 고르기만), 회전을 지원할 때 DetailTarget을 저장한다.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordDetailSheet(target: DetailTarget, pets: List<Pet>, onSaved: (Record, Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val kind = target.kind
    val editing = target.editing
    val petId = editing?.petId ?: target.petId
    var values by remember { mutableStateOf(editing?.let(::detailValues) ?: emptyMap()) }
    var occurredAt by remember { mutableLongStateOf(editing?.occurredAt ?: System.currentTimeMillis()) }
    var timeChanged by remember { mutableStateOf(false) }
    var alerting by remember { mutableStateOf(false) }
    // 이 시트에서 레이어가 저절로 떴는지(시트 한 번에 한 번 — 2026-11-03 사용자).
    var alerted by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun pick(field: DetailField, value: String) {
        val before = values
        values = toggleDetail(values, field, value)
        if (!alerted && picksAlert(kind, before, values)) {
            alerted = true
            alerting = true
        }
    }

    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val name = petId?.let { id -> pets.firstOrNull { it.id == id }?.name } ?: "누군지 모름"
                Text("$name · ${kindName(kind)}", Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            TimeRow(timeText(occurredAt, editing == null && !timeChanged)) {
                pickDateTime(context, occurredAt) {
                    occurredAt = it
                    timeChanged = true
                }
            }
            for (field in detailFields(kind)) {
                val options = field.options.map { it.value to it.title }
                val picked = values[field.key].orEmpty()
                val help: (@Composable () -> Unit)? = if (field.key == "color") {
                    {
                        IconButton({ alerting = true }, Modifier.size(44.dp)) {
                            Icon(
                                Icons.AutoMirrored.Outlined.HelpOutline,
                                if (kind == RecordKind.urine) "어떨 때 병원에 가야 하나요" else "어떤 빛깔이면 병원에 가야 하나요",
                                Modifier.size(22.dp), tint = OkcheColor.Namsaek,
                            )
                        }
                    }
                } else {
                    null
                }
                when {
                    field.multi -> OkcheMultiChips(field.title, options, picked, { pick(field, it) }, hint = field.hint)
                    kind == RecordKind.stool && field.key == "shape" -> OkcheChoiceCards(
                        field.title, options, picked.firstOrNull(), { pick(field, it) }, hint = field.hint,
                        icons = shapeIcons, onDeselect = { picked.firstOrNull()?.let { pick(field, it) } },
                    )
                    else -> OkcheChoiceChips(
                        field.title, options, picked.firstOrNull(), { pick(field, it) }, hint = field.hint,
                        onDeselect = { picked.firstOrNull()?.let { pick(field, it) } }, labelTrailing = help,
                    )
                }
            }
            Warning(kind) { alerting = true }
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton("기록하기", {
                if (busy) return@OkchePrimaryButton
                busy = true
                // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
                val at = if (editing == null && !timeChanged) System.currentTimeMillis() else occurredAt
                scope.launch {
                    runCatching { saveDetail(dao, kind, values, at, editing, petId) }
                        .onSuccess {
                            onSaved(it, editing == null)
                            onClose()
                        }
                        .onFailure {
                            failed = true
                            @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                        }
                    busy = false
                }
            })
        }
    }
    if (alerting) GuideLayer(kind) { alerting = false }
}

/** 디자인 아이콘(알갱이·알약·무른 덩어리·웅덩이·물방울)에 가까운 머티리얼 아이콘. */
private val shapeIcons = mapOf(
    "hard" to Icons.Outlined.MoreHoriz, "normal" to Icons.Outlined.Rectangle, "soft" to Icons.Outlined.Landscape,
    "loose" to Icons.Outlined.Air, "watery" to Icons.Outlined.WaterDrop,
)

private val clock = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

/** '지금 · 오후 2:14', 바꾼 뒤·고치기는 '오늘 · 오후 2:14' / '10월 24일 (토) · 오후 2:14'(6시 기준 날). */
private fun timeText(at: Long, now: Boolean, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(at).atZone(zone).format(clock)
    if (now) return "지금 · $time"
    val day = OkcheDay.of(at, zone)
    val today = OkcheDay.of(System.currentTimeMillis(), zone)
    return "${if (day == today) "오늘" else dayTitle(day, today)} · $time"
}

/** 시간 바꾸기: 기기 날짜 고르기(오늘까지) → 시각 고르기. 지금보다 뒤면 지금으로(저장 때도 막음). */
private fun pickDateTime(context: Context, at: Long, onPicked: (Long) -> Unit) {
    val zone = ZoneId.systemDefault()
    val current = Instant.ofEpochMilli(at).atZone(zone).toLocalDateTime()
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, min ->
            val pickedTime = LocalDateTime.of(y, m + 1, d, h, min)
            // 고르기는 분까지라, 같은 분을 다시 고르면 원래 시각(초) 그대로 — 안 바꾼 것으로 본다.
            if (pickedTime == current.withSecond(0).withNano(0)) return@TimePickerDialog onPicked(at)
            val picked = pickedTime.atZone(zone).toInstant().toEpochMilli()
            val now = System.currentTimeMillis()
            if (picked > now) Toast.makeText(context, "지금보다 뒤로는 바꿀 수 없어서 지금으로 했어요.", Toast.LENGTH_SHORT).show()
            onPicked(minOf(picked, now))
        }, current.hour, current.minute, false).show()
    }, current.year, current.monthValue - 1, current.dayOfMonth).apply {
        datePicker.maxDate = System.currentTimeMillis()
        show()
    }
}

/** '지금 · 오후 2:14' + '시간 바꾸기' 줄. */
@Composable
private fun TimeRow(text: String, onChange: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd))
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, Modifier.weight(1f), color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
        val shape = RoundedCornerShape(OkcheRadius.RadiusPill)
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(shape)
                .clickable(role = Role.Button, onClick = onChange)
                .padding(vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "시간 바꾸기",
                Modifier
                    .heightIn(min = 40.dp)
                    .background(OkcheColor.Paper, shape)
                    .border(1.5.dp, OkcheColor.LineStrong, shape)
                    .padding(horizontal = 12.dp)
                    .wrapContentHeight(Alignment.CenterVertically),
                color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
        }
    }
}

/** 구토·소피 시트 아래 연지색 안내('의서'·'의서 보기'를 누르면 레이어). */
@Composable
private fun Warning(kind: RecordKind, onOpen: () -> Unit) {
    val (text, link, tail) = when (kind) {
        RecordKind.vomit -> Triple("피·커피색이 보이거나 이물질을 삼킨 것 같으면 ", "의서", "를 먼저 보길 권해요.")
        RecordKind.urine -> Triple("힘만 주고 안 나오면 변비처럼 보여도 응급일 수 있어요. ", "의서 보기", "")
        else -> return
    }
    Text(
        buildAnnotatedString {
            append(text)
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(link) }
            append(tail)
        },
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "의서 안내 열기", onClick = onOpen),
        color = OkcheColor.Yeonji, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
    )
}

/** 의서 레이어(보드 GuideAlert·GuideAlertStool·GuideAlertUrine): 고른 것을 집어 말하지 않고 의서의 '지금 바로 병원에' 공통 목록만. */
@Composable
fun GuideLayer(kind: RecordKind, onClose: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(OkcheColor.Hanji, RoundedCornerShape(OkcheRadius.RadiusLg))
                .verticalScroll(rememberScrollState())
                .padding(start = 18.dp, end = 18.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                    Text("의서", color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle())
                    Text("병원에 바로 연락하길 권해요", color = OkcheColor.Yeonji, style = OkcheType.Heading.toTextStyle())
                }
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            Text("의서의 '지금 바로 병원에' 목록이에요.", color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (line in guideLines(kind)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.padding(top = 9.dp).size(6.dp).background(OkcheColor.Yeonji, CircleShape))
                        Text(line, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
                    }
                }
            }
            Text(guideHelp(kind), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            OkchePrimaryButton("가까운 24시 동물병원 찾기", { openHospitalSearch(context) })
            OkchePrimaryButton("기록 이어 하기", onClose, look = ButtonLook.Outlined)
            Text(GUIDE_SOURCE, color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
        }
    }
}

private fun guideLines(kind: RecordKind): List<String> {
    val urinary = "화장실에서 힘만 주고 소변이 안 나와요 (특히 수컷 고양이 · 변비처럼 보여도 응급일 수 있어요)"
    val limp = "축 늘어져 불러도 반응이 약해요"
    return when (kind) {
        RecordKind.vomit -> listOf(
            "피를 토하거나, 커피 찌꺼기 같은 걸 토해요",
            "연달아 여러 번 토하거나, 물만 마셔도 토해요",
            "토하려는데 아무것도 안 나와요 (강아지)",
            "끈·실·약·사람 음식 같은 걸 삼킨 것 같아요 — 실이 보여도 잡아당기거나 억지로 토하게 하지 않길 권해요",
            limp,
        )
        RecordKind.stool -> listOf("변이 짜장처럼 새까매요", "변에 피가 많이 섞였고, 처지거나 토해요", urinary, limp)
        else -> listOf(urinary, limp)
    }
}

private fun guideHelp(kind: RecordKind) = when (kind) {
    RecordKind.vomit -> "토한 것 사진과 실록을 보여 주면 진료에 도움이 돼요."
    RecordKind.stool -> "변 사진과 실록을 보여 주면 진료에 도움이 돼요."
    else -> "언제부터 못 봤는지와 실록을 보여 주면 진료에 도움이 돼요."
}

private const val GUIDE_SOURCE = "출처: 의서와 같음(코넬대 수의대 고양이·개 건강센터 외). 위 내용은 이 단체들의 권고사항이고, 아이마다 다를 수 있어요. 진단이 아니니 수의사와 상의하길 권해요."

/** '24시 동물병원'으로 네이버 지도 앱 → 없으면 카카오맵 앱 → 둘 다 없으면 네이버 지도 웹(2026-11-01 사용자). 위치 권한은 지도 앱이. */
fun openHospitalSearch(context: Context) {
    val q = Uri.encode("24시 동물병원")
    val urls = listOf("nmap://search?query=$q&appname=app.okche", "kakaomap://search?q=$q", "https://map.naver.com/p/search/$q")
    for (url in urls) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            return
        } catch (_: ActivityNotFoundException) {
            // 다음 지도로
        }
    }
}
