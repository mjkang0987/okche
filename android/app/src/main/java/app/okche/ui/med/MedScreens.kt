package app.okche.ui.med

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.okche.store.Medication
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheConfirmDialog
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFooter
import app.okche.ui.OkcheFooterButton
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheTextField
import app.okche.ui.food.SnackRow
import app.okche.ui.pet.FormHeader
import app.okche.ui.pet.withObjectParticle
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch

/** 약방(보드 Yakbang·YakbangEmpty): 더보기 '약방 · 약 목록'. '약'·'보조제' 묶음(없는 묶음은 숨김), 줄을 누르면 고치기, '+ 약 들이기'. */
@Composable
fun YakbangScreen(pets: List<Pet>, seal: @Composable () -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val meds by remember { dao.medicationsFlow() }.collectAsState(emptyList())
    // 처음 읽기 전(null)엔 고치기를 열지 않는다(남은 양 칸이 기록을 빼기 전 값으로 채워지지 않게).
    val loaded by remember { dao.medRecordsFlow() }.collectAsState<List<app.okche.store.Record>, List<app.okche.store.Record>?>(null)
    val records = loaded.orEmpty()
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(emptyList())
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Medication?>(null) }
    if (adding) MedFormSheet(null, pets, records) { adding = false }
    editing?.let { MedFormSheet(it, pets, records) { editing = null } }
    val groups = groupedMeds(meds)
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).statusBarsPadding()) {
        FormHeader("약방", "약 목록", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", onBack, trailing = seal)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 4.dp, bottom = OkcheSpacing.Space6 + app.okche.ui.LocalUndoInset.current),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "병원에서 받은 약과 보조제를 미리 들여 두면, 탕약 알림을 만들거나 기록할 때 골라 쓰기만 하면 돼요.",
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
            if (groups.isEmpty()) {
                Text(
                    "아직 들인 약이 없어요.",
                    Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
                    color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle(),
                )
            }
            for ((title, list) in groups) {
                Column(
                    Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
                        .semantics { isTraversalGroup = true },
                ) {
                    Text(title, Modifier.padding(top = 12.dp).semantics { heading() }, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle())
                    list.forEachIndexed { index, med ->
                        if (index > 0) HorizontalDivider(color = OkcheColor.Line)
                        SnackRow(med.name, if (loaded == null) "" else medDetail(med, records, schedules), badge = medFormTitle(med.form)) { if (loaded != null) editing = med }
                    }
                }
            }
            OkchePrimaryButton("+ 약 들이기", { adding = true })
        }
    }
}

/**
 * 약 들이기·고치기 시트(보드 MedReg·MedEdit): 약 이름, 짧은 이름, 종류, 모양, 한 번에·지금 남은 양(단위는 모양을 따름), 메모.
 * 고치기엔 '약방에서 빼기' → 먹고 있는 약이면 안내 창(MedRemoveBlocked), 아니면 확인 창(MedRemove). 약 봉투 사진은 11단계.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedFormSheet(
    editing: Medication?, pets: List<Pet>, records: List<app.okche.store.Record>,
    /** 약 추가 중 들이기(보드 MedRegSheet) — 들이면 돌아가 그 약이 골라짐. */
    picking: Boolean = false, onAdded: (Medication) -> Unit = {}, onClose: () -> Unit,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val schedules by remember { dao.medSchedulesFlow() }.collectAsState(emptyList())
    val original = remember { editing?.let { medFormOf(it, records) } ?: MedFields() }
    var f by remember { mutableStateOf(original) }
    /** 한 번 만진 칸 — 칸 아래 오류는 만진 뒤부터(열자마자 빨간 글자가 뜨지 않게). */
    var touched by remember { mutableStateOf(setOf<String>()) }
    val allErrors = medErrors(f)
    val errors = allErrors.filterKeys { it in touched }
    // 첫 번째로 남은 것(칸 순서) — 있으면 저장 버튼은 흐리게.
    val missing = listOf("name", "shortName", "dose", "stock").firstNotNullOfOrNull { allErrors[it] }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) } // "remove" / "blocked"
    val unit = unitTitle(unitOf(f.form))

    fun edit(key: String, change: MedFields.() -> MedFields) {
        f = f.change()
        touched = touched + key
    }

    val toasts = app.okche.ui.LocalUndoToasts.current
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
        if (missing != null) return
        run { if (editing != null) updateMed(dao, editing, original, f) else onAdded(addMed(dao, f)) }
    }

    if (editing != null) {
        when (dialog) {
            "blocked" -> OkcheConfirmDialog(
                "지금은 뺄 수 없어요", "알겠어요", onCancel = { dialog = null }, onConfirm = { dialog = null },
                message = medBlockedMessage(editing, medTakers(editing, schedules, pets)), showsCancel = false,
            )
            "remove" -> OkcheConfirmDialog(
                "${withObjectParticle(editing.name)} 약방에서 뺄까요?", "빼기", destructive = true,
                onCancel = { dialog = null }, onConfirm = {
                    dialog = null
                    run {
                        removeMed(dao, editing)
                        toasts?.removed("${app.okche.ui.pet.withObjectParticle(editing.name)} 약방에서 뺐어요") { restoreMed(dao, editing) }
                    }
                },
                message = "탕약에서 더는 고를 수 없어요. 지난 기록의 약 이름은 그대로 남아요.", confirmLook = ButtonLook.Danger,
            )
        }
    }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
        ) {
            Row {
                Text(
                    if (editing != null) "약 고치기" else if (picking) "약방에 새 약 들이기" else "약방에 약 들이기",
                    Modifier.weight(1f).padding(top = 12.dp).semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
                )
                IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            OkcheTextField(f.name, { edit("name") { copy(name = it) } }, "약 이름", placeholder = "예: 병원에서 받은 약 이름", error = errors["name"])
            OkcheTextField(
                f.shortName, { v -> edit("shortName") { copy(shortName = v) } }, "버튼에 보일 짧은 이름", hint = "(선택)",
                placeholder = "예: 프레드니솔론 → 프레드 · 비우면 원래 이름", error = errors["shortName"],
            )
            OkcheChoiceChips("종류", medCategories, f.category, { v -> edit("category") { copy(category = v) } })
            OkcheChoiceChips("모양", medForms, f.form, { v -> edit("form") { copy(form = v) } })
            val dose = @Composable { m: Modifier ->
                OkcheTextField(
                    f.dose, { v -> edit("dose") { copy(dose = v) } }, "한 번에", m, suffix = unit, description = unit, error = errors["dose"],
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            val stock = @Composable { m: Modifier ->
                OkcheTextField(
                    f.stock, { v -> edit("stock") { copy(stock = v) } }, "지금 남은 양", m, hint = "(선택)", placeholder = "예: 120", suffix = unit, description = unit,
                    error = errors["stock"], keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
            }
            // 큰 글자에선 두 칸을 위아래로.
            if (LocalDensity.current.fontScale >= 1.3f) {
                dose(Modifier)
                stock(Modifier)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    dose(Modifier.weight(1f))
                    stock(Modifier.weight(1f))
                }
            }
            OkcheTextField(f.memo, { v -> edit("memo") { copy(memo = v) } }, "메모", hint = "(선택)", placeholder = "예: 공복에 주면 토해요")
            Text(
                when {
                    editing != null -> "남은 양을 고치면 지금부터 다시 세요. 약방에서 빼도 지난 기록의 약 이름은 그대로 남아요."
                    picking -> "들이면 약 추가 화면으로 돌아가 이 약이 바로 골라져 있어요. 약방에도 그대로 남아요."
                    else -> "들인 약은 탕약 알림을 만들 때 골라 쓸 수 있어요."
                },
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
        }
        OkcheFooter {
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkcheFooterButton(if (editing != null) "저장하기" else if (picking) "약방에 들이고 고르기" else "약방에 들이기", ::save, missing)
            if (editing != null) {
                Text(
                    "약방에서 빼기",
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                        dialog = if (medTakers(editing, schedules, pets).isEmpty()) "remove" else "blocked"
                    }.padding(vertical = 12.dp),
                    color = OkcheColor.Yeonji, style = OkcheType.Body.toTextStyle(FontWeight.Medium), textAlign = TextAlign.Center,
                )
            }
        }
    }
}
