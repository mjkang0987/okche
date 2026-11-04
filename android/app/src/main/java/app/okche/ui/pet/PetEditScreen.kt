package app.okche.ui.pet

import androidx.compose.foundation.clickable
import androidx.compose.runtime.collectAsState
import app.okche.store.OkcheDay
import app.okche.ui.food.FoodChangeSheet
import app.okche.ui.food.foodChangeAt
import app.okche.ui.food.foodLine
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheConfirmDialog
import androidx.compose.runtime.rememberCoroutineScope
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkchePickerField
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheSwitchRow
import app.okche.ui.OkcheTextField
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * 저장을 화면 밖(ViewModel)에서 한다 — 저장 중 화면이 다시 만들어져도 끊기지 않고 결과는 새 화면이 받는다. 저장 중 다시 누르면 무시한다(아이 등록·고치기·인슐린 설정).
 */
class FormSaveModel : ViewModel() {
    var saving by mutableStateOf(false)
        private set

    /** 처리 안 한 저장 결과(빈 맵 = 저장됨, 아니면 오류). 화면이 받으면 [consume]. */
    var result by mutableStateOf<Map<String, String>?>(null)
        private set

    fun save(block: suspend () -> Map<String, String>) {
        if (saving) return
        saving = true
        viewModelScope.launch {
            try {
                result = block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("Okche", "저장 실패", e)
                result = mapOf("save" to SAVE_ERROR)
            } finally {
                saving = false
            }
        }
    }

    fun consume() {
        result = null
    }
}

/**
 * 아이 정보 고치기 → 인슐린 설정 두 화면. 인슐린 설정을 다녀와도 고치던 아이 정보 입력이 남게 저장 상태를 둘로 나눠 둔다.
 * 들어가는 길은 더보기 아이 관리·아이 바꾸기 시트(6-3).
 */
@Composable
fun PetEditFlow(petId: String, onClose: () -> Unit, onKept: () -> Unit = onClose, onDeleted: () -> Unit = onClose) {
    var insulin by rememberSaveable { mutableStateOf(false) }
    val states = rememberSaveableStateHolder()
    if (insulin) {
        // 닫으면(저장·뒤로) 그 화면 입력은 버린다 — 다시 열면 저장된 계획부터(iOS와 같음).
        states.SaveableStateProvider("insulin") {
            InsulinSetupScreen(petId, onClose = {
                states.removeState("insulin")
                insulin = false
            })
        }
    } else {
        states.SaveableStateProvider("edit") { PetEditScreen(petId, onClose, onInsulin = { insulin = true }, onKept, onDeleted) }
    }
}

/** 오류가 난 첫 칸으로 옮겨 가려고 칸마다 다는 표시(아이 등록·고치기·인슐린 설정). */
internal class Anchors {
    val bring = mutableMapOf<String, BringIntoViewRequester>()
    val focus = mutableMapOf<String, FocusRequester>()
    fun of(key: String) =
        Modifier.bringIntoViewRequester(bring.getOrPut(key) { BringIntoViewRequester() }).focusRequester(focus.getOrPut(key) { FocusRequester() })

    suspend fun goTo(key: String, focusable: Boolean) {
        bring[key]?.bringIntoView()
        if (focusable) focus[key]?.requestFocus()
    }
}

/**
 * 아이 정보 고치기(디자인 보드 PetEdit, 추억 아이는 PetEditMemorial). 사진 칸은 11단계.
 * 사료·체중 줄은 그 화면을 만드는 단계 전이라 보이기만 한다. 간직·되돌리기·지우기는 6-4.
 */
@Composable
private fun PetEditScreen(petId: String, onClose: () -> Unit, onInsulin: () -> Unit, onKept: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val saver: FormSaveModel = viewModel(key = "petEdit")
    val scope = rememberCoroutineScope()
    // 간직·되돌리기·지우기를 저장하는 동안엔 '저장하기'를 막는다(간직 전 입력이 추억 아이에 덮이지 않게).
    var lifecycleBusy by remember { mutableStateOf(false) }
    var form by rememberSaveable { mutableStateOf<PetEditForm?>(null) }
    var errors by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    // 위 요약(저장된 값)과 체중 줄. 인슐린 설정에서 돌아오면 다시 읽는다.
    var saved by remember { mutableStateOf<Pet?>(null) }
    var weight by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(petId) {
        saved = dao.pet(petId)
        weight = latestWeightText(dao, petId)
        // 인슐린 설정을 다녀와 계획이 생겼으면 '먼저 설정해 주세요' 오류를 지운다.
        if ("insulinPlan" in errors && dao.openInsulinPlans(petId).isNotEmpty()) errors = errors - "insulinPlan"
        // 처음 열 때만 저장된 값으로 채운다(화면을 다시 만들면 고치던 값이 남음). 그새 지운 아이면 닫는다.
        if (form == null) {
            val loaded = PetEditForm.load(dao, petId)
            if (loaded == null) onClose() else form = loaded
        }
    }
    val anchors = remember { Anchors() }
    val result = saver.result
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        errors = result
        val first = result.keys.firstOrNull()
        if (first == null) {
            saver.consume()
            onClose()
            return@LaunchedEffect
        }
        anchors.goTo(first, focusable = first == "name" || first == "age")
        saver.consume()
    }
    val close = {
        if (!saver.saving) {
            saver.consume()
            onClose()
        }
    }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding()) {
        FormHeader(saved?.let { petTitle(it) }.orEmpty(), "아이 정보", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", close, enabled = !saver.saving)
        val f = form ?: return@Column
        fun edit(key: String? = null, change: PetEditForm.() -> PetEditForm) {
            form = f.change()
            if (key != null) errors = errors - key
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space1, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(20.dp), // 아이 등록과 같은 칸 사이
        ) {
            saved?.let { pet ->
                Column(Modifier.semantics(mergeDescendants = true) {}) {
                    val title = OkcheType.Title.toTextStyle()
                    Text(petTitle(pet), color = OkcheColor.Ink, style = title)
                    val days = if (pet.memorializedAt != null) "추억으로 간직 중" else companionDays(pet.familyDate)?.let { "동행 %,d일".format(it) }
                    Text(
                        listOfNotNull(if (pet.species == Species.cat) "고양이" else "강아지", pet.breed, days).joinToString(" · "),
                        color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                    )
                }
            }
            OkcheTextField(f.name, { edit("name") { copy(name = it) } }, "이름", anchors.of("name"), error = errors["name"])
            OkcheChoiceCards("종", listOf(Species.cat to "고양이", Species.dog to "강아지"), f.species, { edit { withSpecies(it) } })
            BreedField(f.species, f.breed) { edit { copy(breed = it) } }
            FurColorPicker(f.species, f.furColor) { edit { copy(furColor = it) } }
            OkcheChoiceChips(
                "성별", listOf(Sex.male to "남아", Sex.female to "여아"), f.sex, { edit { copy(sex = it) } }, hint = "(선택)",
                onDeselect = { edit { copy(sex = null) } },
            )
            OkcheChoiceChips(
                "중성화", listOf(true to "했어요", false to "안 했어요"), f.neutered, { edit { copy(neutered = it) } },
                hint = "(선택)", onDeselect = { edit { copy(neutered = null) } },
            )
            BirthFields(
                f.birthUnknown, f.age, f.birthDate, errors,
                onAge = { edit("age") { copy(age = it) } },
                onBirthDate = { edit("birthDate") { copy(birthDate = it) } },
                onBirthUnknown = {
                    edit { copy(birthUnknown = it) }
                    errors = errors - "age" - "birthDate"
                },
                anchor = anchors::of,
            )
            DateField(f.familyDate, "가족 된 날", { edit("familyDate") { copy(familyDate = it) } }, anchors.of("familyDate"),
                errors["familyDate"], "가족 된 날 지우기")
            OkcheTextField(f.diagnosis, { edit { copy(diagnosis = it) } }, "진단명", hint = "(있을 때만)")
            Column(anchors.of("insulinPlan"), verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1)) {
                OkcheSwitchRow("당뇨가 있어요", "켜면 일기와 위젯에 혈당·인슐린 칸이 생겨요.", f.hasDiabetes, { edit("insulinPlan") { copy(hasDiabetes = it) } })
                // 추억 아이는 인슐린을 새로 설정하지 않는다(간직하면 계획을 닫아 둠 — 되돌릴 때 6-4 규칙대로).
                if (f.hasDiabetes && saved?.memorializedAt == null) {
                    TextButton(onInsulin, Modifier.heightIn(min = 44.dp)) {
                        // 화면 읽기는 '›' 없이 읽게
                        Text("인슐린 이름·양·시간 설정 ›", Modifier.semantics { contentDescription = "인슐린 이름·양·시간 설정" }, color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
                    }
                    errors["insulinPlan"]?.let { OkcheErrorText(it, Modifier.padding(horizontal = OkcheSpacing.Space4)) }
                }
            }
            saved?.let { LaterRows(it, weight) }
            // 추억으로 간직한 아이는 휴양 스위치 없음(6-4).
            if (saved?.memorializedAt == null) OkcheSwitchRow(
                "휴양 중 · 관리 쉬기", "알림을 멈추고, 이 기간은 약·끼니 챙긴 횟수에서 빼요. 휴양 중에도 기록할 수 있어요.",
                f.resting, { edit { copy(resting = it) } },
            )
            errors["save"]?.let { OkcheErrorText(it) }
            OkchePrimaryButton("저장하기", enabled = !saver.saving && !lifecycleBusy, onClick = { saver.save { savePetEdit(dao, f) } })
            saved?.let { pet ->
                LifecycleLinks(
                    pet,
                    enabled = !saver.saving && !lifecycleBusy, // 저장과 겹치면 저장이 간직 표시를 덮을 수 있어서
                    onBusy = { lifecycleBusy = it },
                    onDone = { done ->
                        when (done) {
                            Lifecycle.Kept -> onKept()
                            Lifecycle.Deleted -> onDeleted()
                            Lifecycle.Restored -> scope.launch { saved = dao.pet(petId) }
                        }
                    },
                )
            }
        }
    }
}

/** 지금 먹는 사료(누르면 사료 바꾸기 — 7-4a, 추억 아이는 보기만)·옥체(체중, 그 단계에서 눌리게). */
@Composable
private fun LaterRows(pet: Pet, weight: String?) {
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val changes by remember { dao.foodChangesFlow() }.collectAsState(emptyList())
    val foods by remember { dao.foodsFlow() }.collectAsState(emptyList())
    var changingFood by remember { mutableStateOf(false) }
    val food = foodChangeAt(OkcheDay.of(System.currentTimeMillis()), pet.id, changes)?.let { c -> foodLine(c, foods.firstOrNull { it.id == c.foodId }) } ?: "아직 없어요"
    if (changingFood) FoodChangeSheet(pet) { changingFood = false }
    Column(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
            .padding(horizontal = 14.dp),
    ) {
        if (pet.memorializedAt == null) {
            Row(Modifier.clickable(role = Role.Button, onClickLabel = "사료 바꾸기") { changingFood = true }, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { LaterRow("지금 먹는 사료", food, merge = false) }
                Text("›", Modifier.clearAndSetSemantics {}, color = OkcheColor.LineStrong, style = OkcheType.Body.toTextStyle())
            }
        } else {
            LaterRow("지금 먹는 사료", food)
        }
        HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
        LaterRow("옥체 · 체중", weight ?: "아직 없어요")
    }
}

@Composable
/** [merge]가 거짓이면 감싼 눌림 줄이 글자를 함께 읽는다(TalkBack에서 두 번 멈추지 않게). */
private fun LaterRow(title: String, value: String, merge: Boolean = true) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(vertical = 12.dp).then(if (merge) Modifier.semantics(mergeDescendants = true) {} else Modifier),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(title, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
        Text(value, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
    }
}

private enum class Lifecycle { Kept, Restored, Deleted }

/**
 * '추억으로 간직하기 | 이 아이 기록 지우기', 추억 아이는 '추억에서 되돌리기 | 이 아이 기록 지우기'(6-4). 누르면 확인 창.
 * 저장은 한 트랜잭션(OkcheDao), 실패하면 아래에 문구.
 */
@Composable
private fun LifecycleLinks(pet: Pet, enabled: Boolean, onBusy: (Boolean) -> Unit, onDone: (Lifecycle) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var dialog by rememberSaveable { mutableStateOf<Lifecycle?>(null) }
    var failed by remember { mutableStateOf(false) }
    val memorial = pet.memorializedAt != null
    fun run(what: Lifecycle) {
        dialog = null
        onBusy(true)
        scope.launch {
            val dao = OkcheDatabase.get(context).dao()
            val now = System.currentTimeMillis()
            failed = runCatching {
                when (what) {
                    Lifecycle.Kept -> dao.memorialize(pet.id, now)
                    Lifecycle.Restored -> dao.restoreFromMemorial(pet.id, now)
                    Lifecycle.Deleted -> dao.deletePet(pet.id, now)
                }
            }.isFailure
            // 간직·지우기는 화면을 닫으니 막은 채로 둔다.
            if (failed || what == Lifecycle.Restored) onBusy(false)
            if (!failed) onDone(what)
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        // 큰 글자에서 한 줄에 안 들어가면 다음 줄로(버튼이 찌그러지지 않게).
        FlowRow(Modifier.heightIn(min = 44.dp), horizontalArrangement = Arrangement.Center, itemVerticalAlignment = Alignment.CenterVertically) {
            val style = OkcheType.Label.toTextStyle(FontWeight.Normal)
            val alpha = if (enabled) 1f else 0.4f // 막혀 있을 땐 흐리게
            if (memorial) {
                TextButton({ dialog = Lifecycle.Restored }, enabled = enabled) { Text("추억에서 되돌리기", color = OkcheColor.Namsaek.copy(alpha = alpha), style = style) }
            } else {
                TextButton({ dialog = Lifecycle.Kept }, enabled = enabled) { Text("추억으로 간직하기", color = OkcheColor.InkMuted.copy(alpha = alpha), style = style) }
            }
            Text("|", Modifier.clearAndSetSemantics {}, color = OkcheColor.Line, style = style)
            TextButton({ dialog = Lifecycle.Deleted }, enabled = enabled) { Text("이 아이 기록 지우기", color = OkcheColor.Yeonji.copy(alpha = alpha), style = style) }
        }
        if (failed) OkcheErrorText(SAVE_ERROR)
    }
    when (dialog) {
        Lifecycle.Kept -> OkcheConfirmDialog(
            "${pet.name}의 기록을 추억으로 간직할게요", "간직하기", { dialog = null }, { run(Lifecycle.Kept) },
            bullets = listOf("약·기록·사진 알림은 모두 멈춰요", "기록과 사진은 그대로 남아요", "생일 같은 축하 화면은 이제 뜨지 않아요", "아이 정보에서 언제든 되돌릴 수 있어요"),
        )
        Lifecycle.Deleted -> OkcheConfirmDialog(
            "${pet.name}의 기록을 지울까요?", "지우기", { dialog = null }, { run(Lifecycle.Deleted) },
            destructive = true, message = "기록·사진·실록이 모두 사라지고, 되돌릴 수 없어요.", confirmLook = ButtonLook.Danger,
        ) {
            // 추억 아이에게는 간직 권유를 보이지 않는다(이미 간직 중).
            if (!memorial) Column(
                Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    buildAnnotatedString {
                        append("지우는 대신 ")
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("추억으로 간직") }
                        append("할 수도 있어요. 기록과 사진은 그대로 두고, 알림만 모두 멈춰요.")
                    },
                    color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                )
                OkchePrimaryButton("추억으로 간직하기", { dialog = Lifecycle.Kept }, look = ButtonLook.Outlined)
            }
        }
        Lifecycle.Restored -> OkcheConfirmDialog(
            "${withObjectParticle(pet.name)} 다시 모실까요?", "다시 모시기", { dialog = null }, { run(Lifecycle.Restored) },
            bullets = listOf("간직할 때 멈춘 약·인슐린·끼니 알림이 다시 시작돼요", "생일 같은 축하 화면이 다시 떠요", "휴양 중이었다면 다시 켜 주세요"),
        )
        null -> Unit
    }
}

/** 인슐린 설정(디자인 보드 InsulinSetup). '저장하기'는 바로 저장하고 아이 정보로 돌아간다. */
@Composable
private fun InsulinSetupScreen(petId: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val saver: FormSaveModel = viewModel(key = "insulinSetup")
    var form by rememberSaveable { mutableStateOf<InsulinSetupForm?>(null) }
    var errors by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    LaunchedEffect(petId) { if (form == null) form = InsulinSetupForm.load(dao, petId) }
    val anchors = remember { Anchors() }
    val result = saver.result
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        errors = result
        val first = result.keys.firstOrNull()
        if (first == null) {
            saver.consume()
            onClose()
            return@LaunchedEffect
        }
        anchors.goTo(first, focusable = first.startsWith("units"))
        saver.consume()
    }
    val close = {
        if (!saver.saving) {
            saver.consume()
            onClose()
        }
    }
    BackHandler(onBack = close)

    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding()) {
        FormHeader("인슐린", "설정", Icons.AutoMirrored.Outlined.ArrowBack, "뒤로", close, enabled = !saver.saving)
        val f = form ?: return@Column
        // 고친 칸의 오류는 지우고, 줄 수가 바뀌면 줄 번호가 밀리므로 줄 오류를 모두 지운다(아이 등록과 같음).
        fun edit(key: String? = null, change: InsulinSetupForm.() -> InsulinSetupForm) {
            val next = f.change()
            form = next
            if (key != null) errors = errors - key
            if (next.rows.size != f.rows.size) errors = errors.filterKeys { !it.startsWith("time") && !it.startsWith("units") }
        }
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space1, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(14.dp), // 보드 칸 사이 14
        ) {
            OkcheTextField(f.name, { edit { copy(name = it) } }, "인슐린 이름", placeholder = "예: 병원에서 받은 인슐린 이름")
            Column {
                OkcheChoiceChips(
                    "주사기 종류", listOf(Syringe.u40 to "U-40", Syringe.u100 to "U-100", Syringe.unknown to "잘 모르겠어요"),
                    f.syringe, { edit { copy(syringe = it) } },
                )
                HelpText("인슐린과 맞는 주사기를 쓰길 권해요. 모르면 병원에 물어보길 권해요.", Modifier.padding(top = OkcheSpacing.Space2))
            }
            Column {
                OkcheFieldLabel("맞는 시간과 양", Modifier.padding(bottom = OkcheSpacing.Space2))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
                        .padding(horizontal = 14.dp),
                ) {
                    InsulinRowsEditor(f.rows, errors, { key, rows -> edit(key) { copy(rows = rows) } },
                        { errors = errors.filterKeys { !it.startsWith("time") } }, anchors::of)
                }
                HelpText(
                    "병원에서 정해 준 양을 넣어 주세요. 일기의 '맞음' 버튼이 그 시간의 양으로 바로 기록해요. 양을 바꿀 땐 수의사와 먼저 상의하길 권해요.",
                    Modifier.padding(top = OkcheSpacing.Space1),
                )
            }
            OkcheSwitchRow("인슐린 시간 알림", "끄면 모든 아이의 인슐린 알림이 멈춰요", f.notify, { edit { copy(notify = it) } })
            OkcheSwitchRow("밤에도 울리기", "밤에 알림 쉬기 중에도 이 인슐린 알림은 울려요", f.ringDuringQuiet, { edit { copy(ringDuringQuiet = it) } })
            LeadPicker(f.leadMinutes) { edit { copy(leadMinutes = it) } }
            SourceNote(DIABETES_SOURCE)
            errors["save"]?.let { OkcheErrorText(it) }
            OkchePrimaryButton("저장하기", enabled = !saver.saving, onClick = { saver.save { saveInsulinSetup(dao, f) } })
        }
    }
}

/** 알림 보낼 때: 이름표 왼쪽, 고르기 칸(셀렉트) 오른쪽. 큰 글자에서 넘치면 아래로. */
@Composable
private fun LeadPicker(minutes: Int, onSelect: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val title = insulinLeadChoices.firstOrNull { it.first == minutes }?.second.orEmpty()
    FlowRow(
        Modifier.fillMaxWidth().padding(start = OkcheSpacing.Space4),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        // 고르기 칸이 같은 이름을 읽으므로 보이는 글은 화면 읽기에서 뺀다(iOS와 같음).
        Text("알림 보낼 때", Modifier.clearAndSetSemantics {}, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
        // 내용 너비(최소 150) — 보드처럼 이름 오른쪽에, 큰 글자에선 다음 줄로.
        Box(Modifier.widthIn(min = 150.dp).width(IntrinsicSize.Max)) {
            OkchePickerField(title, "알림 보낼 때", { open = true }, showLabel = false, icon = Icons.Outlined.KeyboardArrowDown, role = Role.DropdownList)
            DropdownMenu(open, { open = false }, containerColor = OkcheColor.Paper) {
                for ((value, name) in insulinLeadChoices) {
                    DropdownMenuItem(
                        text = {
                            Text(name, color = OkcheColor.Ink,
                                style = OkcheType.Body.toTextStyle(if (value == minutes) FontWeight.Bold else FontWeight.Normal))
                        },
                        onClick = { onSelect(value); open = false },
                        modifier = Modifier.semantics { selected = value == minutes },
                    )
                }
            }
        }
    }
}
