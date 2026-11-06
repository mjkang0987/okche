package app.okche.ui.diabetes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFooter
import app.okche.ui.OkcheFooterButton
import app.okche.ui.OkcheTextField
import app.okche.ui.med.clockText
import app.okche.ui.med.medNumber
import app.okche.ui.pet.DIABETES_SOURCE
import app.okche.ui.record.DetailTarget
import app.okche.ui.record.TimeRow
import app.okche.ui.record.pickDateTime
import app.okche.ui.record.recordTime
import app.okche.ui.record.timeText
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch

/**
 * 인슐린 시트(보드 Insulin·InsulinTwice, 9-2): 시간(+ 인슐린 이름 · 정해 둔 양, 이미 맞은 시간대면 연지색 + 경고), 맞은 양, 어떻게 됐나요,
 * 맞기 전에 밥은(선택), 안내, 인슐린 설정 줄, 출처, '기록하기'. 고치기도 같은 시트([DetailTarget.editing]).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsulinSheet(target: DetailTarget, pets: List<Pet>, onSaved: (Record, Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val openSetup = LocalOpenInsulinSetup.current
    val editing = target.editing
    val petId = editing?.petId ?: target.petId
    val plans by remember { dao.insulinPlansFlow() }.collectAsState(null)
    val records by remember { dao.insulinRecordsFlow() }.collectAsState(null)
    // 칸을 만지기 전엔 null — 시트 시각 차례의 정해 둔 양을 보인다(시간을 바꾸면 따라감). 건너뛴 기록은 양이 0이라 처음 값으로.
    var unitsText by remember { mutableStateOf(editing?.takeIf { insulinStatus(it) != "skipped" }?.let(::insulinUnits)?.let(::medNumber)) }
    var status by remember { mutableStateOf(editing?.let(::insulinStatus) ?: "full") }
    var meal by remember { mutableStateOf(editing?.let(::insulinMeal)) }
    var occurredAt by remember { mutableLongStateOf(editing?.occurredAt ?: System.currentTimeMillis()) }
    var timeChanged by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val isNow = editing == null && !timeChanged
    val at = if (isNow) System.currentTimeMillis() else occurredAt
    val plan = petId?.let { plans?.let { p -> openInsulinPlan(p, it) } }
    val slot = plan?.let { insulinSlotAt(it, at) }
    val text = unitsText ?: slot?.let { medNumber(it.units) }.orEmpty()
    val units = insulinUnitsNumber(text)
    val skipped = status == "skipped"
    val twice = petId?.let { insulinTwice(it, plan, records.orEmpty(), at, editing?.id) }
    val missing = if (skipped || units != null) null else insulinUnitsError(text) ?: "맞은 양을 넣어 주세요"

    fun save() {
        if (missing != null || busy) return
        busy = true
        val savedAt = if (isNow) System.currentTimeMillis() else occurredAt
        scope.launch {
            runCatching { saveInsulin(dao, petId, plan, units ?: 0.0, status, meal, savedAt, editing) }
                .onSuccess {
                    if (editing != null) @Suppress("DEPRECATION") view.announceForAccessibility("고쳤어요")
                    onSaved(it, editing == null)
                    onClose()
                }
                .onFailure {
                    busy = false // 성공이면 닫힐 때까지 두 번 저장되지 않게 그대로
                    failed = true
                    @Suppress("DEPRECATION") view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
                }
        }
    }

    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column {
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space4),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val name = petId?.let { id -> pets.firstOrNull { it.id == id }?.name } ?: "누군지 모름"
                    Text("$name · 인슐린", Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                    IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                    TimeRow(
                        timeText(at, isNow),
                        detail = slot?.let { "${plan?.name ?: "인슐린"} · 정해 둔 양 ${medNumber(it.units)}단위" },
                        warning = twice != null,
                    ) {
                        pickDateTime(context, at) {
                            occurredAt = it
                            timeChanged = true
                        }
                    }
                    if (twice != null) {
                        val clock = slot?.occurrence?.let { clockText(it) }.orEmpty() // 경고가 있으면 같은 구간의 차례
                        Text(
                            "이 시간대($clock 차례)에 이미 맞은 기록이 있어요 · ${recordTime(twice)} · ${insulinUnits(twice)?.let(::medNumber).orEmpty()}단위. " +
                                "두 번 맞히지 않았는지 확인해 주세요. 맞다면 그대로 남길 수 있어요.",
                            Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }, // 나타날 때 읽히게
                            color = OkcheColor.Yeonji, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                        )
                    }
                }
                if (!skipped) {
                    OkcheTextField(
                        text, { unitsText = it }, "맞은 양", Modifier.widthIn(max = 200.dp), suffix = "단위", error = insulinUnitsError(text),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
                OkcheChoiceChips("어떻게 됐나요?", insulinStatusTitles, status, { status = it })
                OkcheChoiceChips("맞기 전에 밥은?", insulinMealTitles, meal, { meal = it }, hint = "(선택)", onDeselect = { meal = null })
                Text(
                    "밥을 안 먹은 날 인슐린을 어떻게 할지는 미리 수의사와 정해 두길 권해요. 양은 상의 없이 바꾸지 않길 권해요.",
                    color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                )
                if (petId != null) {
                    Text(
                        "인슐린 이름·양·시간 바꾸기 ›",
                        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) {
                            onClose()
                            openSetup(petId)
                        }.wrapContentHeight(Alignment.CenterVertically),
                        color = OkcheColor.Namsaek, style = OkcheType.Label.toTextStyle(),
                    )
                }
                Text(DIABETES_SOURCE, color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
            }
            OkcheFooter {
                if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
                OkcheFooterButton("기록하기", ::save, missing)
            }
        }
    }
}
