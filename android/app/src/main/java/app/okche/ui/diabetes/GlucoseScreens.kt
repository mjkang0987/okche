package app.okche.ui.diabetes

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFooter
import app.okche.ui.OkcheFooterButton
import app.okche.ui.OkcheTextField
import app.okche.ui.pet.DIABETES_SOURCE
import app.okche.ui.record.DetailTarget
import app.okche.ui.record.GuideLayer
import app.okche.ui.record.TimeRow
import app.okche.ui.record.pickDateTime
import app.okche.ui.record.timeText
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch

/**
 * 혈당 시트(보드 Glucose, 9-1): 시간, 수치, 언제 쟀나요, 함께 보였나요(고르면 연지색 + 저혈당 안내), 늘 보이는 안내·출처, '기록하기'.
 * 고치기도 같은 시트([DetailTarget.editing]). 새 기록은 [DetailTarget.glucoseText]가 맞는 숫자면 채운다. [onSaved]는 저장한 기록과 새 기록인지.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlucoseSheet(target: DetailTarget, pets: List<Pet>, onSaved: (Record, Boolean) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val editing = target.editing
    val petId = editing?.petId ?: target.petId
    val plans by remember { dao.insulinPlansFlow() }.collectAsState(null)
    val insulin by remember { dao.insulinRecordsFlow() }.collectAsState(null)
    var text by remember { mutableStateOf(editing?.let(::glucoseMgdl)?.toString() ?: glucoseNumber(target.glucoseText)?.toString().orEmpty()) }
    var timing by remember { mutableStateOf(editing?.let(::glucoseTiming)) }
    var signs by remember { mutableStateOf(editing?.let(::glucoseSigns) ?: emptySet()) }
    var occurredAt by remember { mutableLongStateOf(editing?.occurredAt ?: System.currentTimeMillis()) }
    var timeChanged by remember { mutableStateOf(false) }
    var guiding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    // 처음 값 = 남기기 짐작, 물어야 하면 맞기 전(다 읽은 뒤에 정함 — 그 전엔 안 고른 채).
    val loaded = plans != null && insulin != null
    val picked = timing ?: if (!loaded) null else petId?.let { glucoseGuess(it, plans.orEmpty(), insulin.orEmpty(), System.currentTimeMillis()) } ?: GlucoseTiming.beforeInsulin
    val mgdl = glucoseNumber(text)

    fun save() {
        val n = mgdl ?: return
        if (busy) return
        busy = true
        val at = if (editing == null && !timeChanged) System.currentTimeMillis() else occurredAt
        scope.launch {
            runCatching { saveGlucose(dao, petId, n, picked ?: GlucoseTiming.beforeInsulin, signs, at, editing) }
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
                    Text("$name · 혈당", Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                    IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
                }
                TimeRow(timeText(occurredAt, editing == null && !timeChanged)) {
                    pickDateTime(context, occurredAt) {
                        occurredAt = it
                        timeChanged = true
                    }
                }
                OkcheTextField(
                    text, { text = it }, "수치", Modifier.widthIn(max = 200.dp), suffix = "mg/dL", error = glucoseError(text),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OkcheChoiceChips("언제 쟀나요?", glucoseTimingTitles, picked, { timing = it })
                OkcheChoiceCards(
                    "함께 보였나요?", glucoseSignTitles, null, { s -> signs = if (s in signs) signs - s else signs + s },
                    hint = "(여러 개 고를 수 있어요)", checked = signs,
                )
                // 고른 뒤에만 저혈당 안내(디자인 고침 — 2026-11-02).
                if (signs.isNotEmpty()) {
                    Text(
                        buildAnnotatedString {
                            append("인슐린 맞는 아이가 이런 모습을 보이면 저혈당일 수 있어요. 바로 병원에 연락하길 권해요. ")
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("의서 보기") }
                        },
                        Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "의서 안내 열기") { guiding = true },
                        color = OkcheColor.Yeonji, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                    )
                }
                Text(
                    "인슐린 양은 수의사와 상의 없이 바꾸지 않길 권해요. 맞힌 인슐린은 일기의 인슐린 줄에서 남겨 주세요.",
                    color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                )
                Text(DIABETES_SOURCE, color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
            }
            OkcheFooter {
                if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
                OkcheFooterButton("기록하기", ::save, if (mgdl == null) "수치를 넣어 주세요" else null)
            }
        }
    }
    if (guiding) GuideLayer(RecordKind.glucose) { guiding = false }
}
