package app.okche.ui.food

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.Snack
import app.okche.store.SnackKind
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheConfirmDialog
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheSwitchRow
import app.okche.ui.OkcheTextField
import app.okche.ui.pet.FormHeader
import app.okche.ui.pet.withObjectParticle
import app.okche.ui.record.TimeRow
import app.okche.ui.record.pickDateTime
import app.okche.ui.record.timeText
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch

/** 다과 목록(보드 SnackList): 더보기 '다과 · 간식 목록'. 들인 다과(모든 아이 공통, 들인 순), 줄을 누르면 고치기, '+ 다과 들이기'. */
@Composable
fun SnackListScreen(current: Pet?, seal: @Composable () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val snacks by remember { dao.snacksFlow() }.collectAsState(emptyList())
    val records by remember { dao.snackRecordsFlow() }.collectAsState(emptyList())
    var form by remember { mutableStateOf<SnackFormMode?>(null) }
    form?.let { SnackFormSheet(it) { form = null } }
    val listed = listedSnacks(snacks)
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("다과", "간식 목록", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack, trailing = seal)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 4.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "자주 주는 간식을 들여 두면 다과를 남길 때 한 번에 골라요. 수라 바꾸기 기간에 무엇을 먹었는지 실록에서 함께 볼 수 있어요.",
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
            if (listed.isNotEmpty()) {
                Column(
                    Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(horizontal = 16.dp, vertical = 4.dp)
                        .semantics { isTraversalGroup = true },
                ) {
                    listed.forEachIndexed { index, snack ->
                        if (index > 0) HorizontalDivider(color = OkcheColor.Line)
                        SnackRow(snack.name, snackListDetail(snack, current?.id, records)) { form = SnackFormMode.Edit(snack) }
                    }
                }
            }
            OkchePrimaryButton("+ 다과 들이기", { form = SnackFormMode.Add })
        }
    }
}

/** 다과 줄(목록): 48 '간식' 칸 + 이름(굵게) + 아래 줄 + ›, 72 높이. 약방 약 줄도 같이 씀(칸 글자 '알약'·'물약' — 8-1). */
@Composable
fun SnackRow(name: String, detail: String, badge: String = "간식", onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 72.dp).clickable(role = Role.Button, onClickLabel = "고치기", onClick = onClick).padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(48.dp).background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusSm)).clearAndSetSemantics {},
            contentAlignment = Alignment.Center,
        ) {
            // 칸은 48 그대로라 큰 글자에선 글자를 줄여 넣는다.
            val caption = OkcheType.Caption.toTextStyle()
            Text(badge, color = OkcheColor.NamsaekDeep, style = caption, maxLines = 1, softWrap = false,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = caption.fontSize * 0.5f, maxFontSize = caption.fontSize))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
            Text(detail, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = OkcheColor.LineStrong)
    }
}

/** 다과 들이기·고치기 시트 종류: 목록에서 들이기, 다과 기록 중 들이기(들이면 돌아가 골라짐), 고치기. */
sealed interface SnackFormMode {
    data object Add : SnackFormMode
    data object AddAndPick : SnackFormMode
    data class Edit(val snack: Snack) : SnackFormMode
}

/**
 * 다과 들이기·고치기 시트(보드 SnackReg·SnackRegSheet·SnackEdit): 간식 이름, 종류, 수라 바꾸기 중에도 괜찮은 간식.
 * 고치기엔 '목록에서 빼기' → 확인 창(SnackRemove). 성분표 사진은 11단계.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnackFormSheet(mode: SnackFormMode, onAdded: (Snack) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val editing = (mode as? SnackFormMode.Edit)?.snack
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var kind by remember { mutableStateOf(editing?.kind ?: SnackKind.lickable) }
    var ok by remember { mutableStateOf(editing?.okDuringFoodChange ?: false) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var removing by remember { mutableStateOf(false) }
    val (title, note, button) = when (mode) {
        SnackFormMode.Add -> Triple("다과 들이기", "들인 간식은 다과 기록에서 바로 골라 쓸 수 있어요.", "다과에 들이기")
        SnackFormMode.AddAndPick -> Triple("새 다과 들이기", "들이면 다과 기록으로 돌아가 이 간식이 바로 골라져 있어요. 다과 목록에도 남아요.", "다과에 들이고 고르기")
        is SnackFormMode.Edit -> Triple("다과 고치기", "목록에서 빼도 지난 기록의 간식 이름은 그대로 남아요.", "저장하기")
    }

    fun run(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            failed = runCatching { work() }.isFailure
            busy = false
            if (failed) view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.") else onClose()
        }
    }

    fun save() {
        if (name.isBlank()) {
            nameError = "간식 이름을 넣어 주세요"
            view.announceForAccessibility("간식 이름을 넣어 주세요")
            return
        }
        run {
            if (editing != null) updateSnack(dao, editing, name, kind, ok) else onAdded(addSnack(dao, name, kind, ok))
        }
    }

    if (removing && editing != null) {
        OkcheConfirmDialog(
            "${withObjectParticle(editing.name)} 목록에서 뺄까요?", "빼기", destructive = true,
            onCancel = { removing = false },
            onConfirm = { removing = false; run { removeSnack(dao, editing) } },
            message = "다과 기록에서 더는 고를 수 없어요. 지난 기록의 간식 이름은 그대로 남아요.",
            confirmLook = ButtonLook.Danger,
        )
    }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            OkcheTextField(name, { name = it; if (it.isNotBlank()) nameError = null }, "간식 이름", placeholder = "예: 닭가슴살 동결건조", error = nameError)
            OkcheChoiceChips("종류", snackKinds, kind, { kind = it })
            OkcheSwitchRow("수라 바꾸기 중에도 괜찮은 간식", "병원에서 괜찮다고 한 간식만 켜 두길 권해요.", ok, { ok = it })
            Text(note, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        HorizontalDivider(color = OkcheColor.Line)
        Column(
            Modifier.padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 10.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
        ) {
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton(button, ::save)
            if (editing != null) {
                Text(
                    "목록에서 빼기",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { removing = true }.padding(vertical = 12.dp),
                    color = OkcheColor.Yeonji, style = OkcheType.Body.toTextStyle(FontWeight.Bold),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 다과 기록 시트(보드 Snack): 수라 화면 '+ 다과 · 간식 남기기'·기록 줄 연필. 시간, 어떤 다과(이 아이에게 최근 준 순), '+ 새 다과 들이기',
 * 얼마나 줬나요(선택), 수라 바꾸기 기간 안내, '기록하기'.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnackRecordSheet(pet: Pet, editing: Record? = null, onSaved: (Record) -> Unit = {}, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val snacks by remember { dao.snacksFlow() }.collectAsState(emptyList())
    val records by remember { dao.snackRecordsFlow() }.collectAsState(emptyList())
    val changes by remember { dao.foodChangesFlow() }.collectAsState(emptyList())
    var occurredAt by remember { mutableLongStateOf(editing?.occurredAt ?: System.currentTimeMillis()) }
    var timeChanged by remember { mutableStateOf(false) }
    var picked by remember { mutableStateOf(editing?.let(::snackIdOf)) }
    var amount by remember { mutableStateOf(editing?.let(::snackAmountOf).orEmpty()) }
    var pickError by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val today = OkcheDay.of(System.currentTimeMillis())
    val options = snackPickOrder(snacks, pet.id, records).let { order ->
        // 고치는 기록의 다과를 목록에서 뺐어도 고른 채 보이게.
        val gone = snacks.firstOrNull { it.id == picked && order.none { o -> o.first.id == picked } }
        if (gone != null) listOf(gone to null) + order else order
    }

    fun save() {
        val id = picked
        if (id == null) {
            pickError = "어떤 다과를 줬는지 골라 주세요"
            view.announceForAccessibility("어떤 다과를 줬는지 골라 주세요")
            return
        }
        if (busy) return
        busy = true
        // 시간을 안 바꾼 새 기록은 누른 때가 '지금'.
        val at = if (editing == null && !timeChanged) System.currentTimeMillis() else occurredAt
        scope.launch {
            val saved = runCatching { recordSnack(dao, id, amount, at, editing, pet.id) }
            failed = saved.isFailure
            busy = false
            if (failed) {
                view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
            } else {
                view.announceForAccessibility("아뢰었사옵니다")
                onSaved(saved.getOrThrow())
                onClose()
            }
        }
    }

    if (adding) SnackFormSheet(SnackFormMode.AddAndPick, onAdded = { picked = it.id; pickError = null }) { adding = false }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildAnnotatedString {
                        append("${pet.name} · 다과")
                        withStyle(SpanStyle(color = OkcheColor.InkMuted, fontWeight = FontWeight.Medium, fontSize = OkcheType.Body.fontSize)) { append(" 간식") }
                    },
                    Modifier.weight(1f).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
                )
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            TimeRow(timeText(occurredAt, editing == null && !timeChanged)) {
                pickDateTime(context, occurredAt) {
                    occurredAt = it
                    timeChanged = true
                }
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                OkcheFieldLabel("어떤 다과를 줬나요?", Modifier.semantics { heading() })
                for ((snack, lastDay) in options) {
                    SnackPickCard(
                        snack.name,
                        listOfNotNull(snackKindTitle(snack.kind), lastDay?.let { snackLastGiven(it, today) }).joinToString(" · "),
                        picked == snack.id,
                    ) {
                        picked = snack.id
                        pickError = null
                    }
                }
                Text(
                    "+ 새 다과 들이기",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { adding = true }.padding(vertical = 12.dp),
                    color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
                )
                pickError?.let { OkcheErrorText(it) }
            }
            OkcheTextField(amount, { amount = it }, "얼마나 줬나요?", hint = "(선택)", placeholder = "예: 3알, 반 개")
            if (inFoodChange(pet.id, OkcheDay.of(occurredAt), changes)) {
                Text(
                    "지금은 수라 바꾸기 기간이에요. 간식은 실록에 따로 표시돼서, 처방식 효과를 볼 때 함께 확인할 수 있어요.",
                    Modifier.fillMaxWidth().background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 14.dp, vertical = 12.dp),
                    color = OkcheColor.NamsaekDeep, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                )
            }
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton("기록하기", ::save)
        }
    }
}

/** 다과 고르기 줄(다과 기록 시트): 라디오 + 이름 + 아래 줄, 고르면 남색 테두리·연한 남색 바탕. */
@Composable
internal fun SnackPickCard(name: String, detail: String, isOn: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp)
            .background(if (isOn) OkcheColor.NamsaekSoft else OkcheColor.Paper, shape)
            .border(1.5.dp, if (isOn) OkcheColor.Namsaek else OkcheColor.Line, shape)
            .selectable(isOn, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(isOn, null, colors = RadioButtonDefaults.colors(selectedColor = OkcheColor.Namsaek, unselectedColor = OkcheColor.LineStrong))
        Column(Modifier.weight(1f)) {
            Text(name, color = if (isOn) OkcheColor.NamsaekDeep else OkcheColor.Ink, style = OkcheType.Body.toTextStyle(if (isOn) FontWeight.Bold else FontWeight.Normal))
            Text(detail, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
    }
}
