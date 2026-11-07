package app.okche.ui.pet

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.okche.store.OkcheDatabase
import app.okche.store.newId
import app.okche.store.setLastPetId
import app.okche.store.setNotifyAsked
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkcheFooter
import app.okche.ui.OkcheFooterButton
import app.okche.ui.OkcheSwitchRow
import app.okche.ui.OkcheTextField
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle

/**
 * 아이 등록(디자인 보드 Profile·ProfileDog). 프로필 사진 칸은 11단계(사진 저장)에서.
 * 닫기·시스템 뒤로는 `onClose`, '모시기'가 저장되면 `onSaved`.
 */
@Composable
fun PetRegisterScreen(onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val saver: FormSaveModel = viewModel(key = "petRegister")
    var form by rememberSaveable { mutableStateOf(PetForm()) }
    /** 한 번 만진 칸 — 칸 아래 오류는 만진 뒤부터(열자마자 빨간 글자가 뜨지 않게). */
    var touched by rememberSaveable { mutableStateOf(emptySet<String>()) }
    var failed by rememberSaveable { mutableStateOf(false) }
    val allErrors = registerErrors(form)
    val errors = allErrors.touchedOnly(touched)
    // 첫 번째로 남은 것(화면 위에서부터) — 있으면 '모시기'는 흐리게.
    val missingKey = (listOf("name", "birthDate", "age", "weight") + insulinOrder(form.insulinRows.size)).firstOrNull { it in allErrors }
    val missing = allErrors.missingLine(missingKey)
    val anchors = remember { Anchors() }
    val scope = rememberCoroutineScope()
    fun edit(key: String? = null, change: PetForm.() -> PetForm) {
        val rows = form.insulinRows.size
        form = form.change()
        touched = touched.afterRows(key, rows, form.insulinRows.size)
    }
    /** 당뇨 아이를 모시면 허락 안내 창(NotifyAsk, 9-3) — 아이는 이미 저장됨, 답이 오면 탭으로. */
    val asking = rememberSaveable { mutableStateOf(false) }
    // 저장 결과 받기(저장 중 화면이 다시 만들어졌어도 여기서 받는다).
    val result = saver.result
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        failed = "save" in result
        touched = touched + result.keys // 화면 검사와 저장 검사가 어긋나도 그 칸이 보이게
        saver.consume()
        if (result.isEmpty()) {
            // 인슐린 시간 알림(설정 notify.insulinTime, 모든 아이 공통)을 꺼 두었으면 묻지 않는다.
            val ask = form.hasDiabetes && runCatching {
                OkcheDatabase.get(context).dao().loadOrCreateSetting().notify.insulinTime && app.okche.ui.med.shouldAskNotify(context)
            }.getOrDefault(false)
            if (ask) {
                runCatching { context.setNotifyAsked(true) }
                asking.value = true
            } else {
                onSaved()
            }
        }
    }

    // 저장 중엔 닫지 않는다(닫으면 끝난 결과가 남아 다음 등록 화면에 옛 결과가 뜸). 닫을 때 남은 결과도 버린다.
    val close = {
        if (!saver.saving) {
            saver.consume()
            onClose()
        }
    }
    BackHandler(onBack = close)
    // 되돌리기 알림이 떠 있으면 아래 고정 버튼이 가리지 않게 그만큼 위로.
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding().padding(bottom = app.okche.ui.LocalUndoInset.current)) {
        FormHeader("새 아기씨 모시기", "아이 등록", Icons.Outlined.Close, "닫기", close, enabled = !saver.saving)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space2, bottom = OkcheSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(20.dp), // 보드 칸 사이 20(토큰에 없음)
        ) {
            OkcheTextField(form.name, { edit("name") { copy(name = it) } }, "이름", anchors.of("name"), error = errors["name"])
            OkcheChoiceCards(
                "종", listOf(Species.cat to "고양이", Species.dog to "강아지"), form.species,
                { edit { withSpecies(it) } },
            )
            BreedField(form.species, form.breed) { edit { copy(breed = it) } }
            FurColorPicker(form.species, form.furColor) { edit { copy(furColor = it) } }
            OkcheChoiceChips(
                "성별", listOf(Sex.male to "남아", Sex.female to "여아"), form.sex, { edit { copy(sex = it) } }, hint = "(선택)",
                onDeselect = { edit { copy(sex = null) } },
            )
            OkcheChoiceChips(
                "중성화", listOf(true to "했어요", false to "안 했어요"), form.neutered, { edit { copy(neutered = it) } },
                hint = "(선택)", onDeselect = { edit { copy(neutered = null) } },
            )
            BirthFields(
                form.birthUnknown, form.age, form.birthDate, errors,
                onAge = { edit("age") { copy(age = it) } },
                onBirthDate = { edit("birthDate") { copy(birthDate = it) } },
                onBirthUnknown = { edit { copy(birthUnknown = it) } },
                anchor = anchors::of,
            )
            OkcheTextField(
                form.weight, { edit("weight") { copy(weight = it) } }, "체중", anchors.of("weight"), hint = "(kg)",
                error = errors["weight"], keyboardOptions = decimalKeys,
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OkcheTextField(
                    form.diagnosis, { edit { copy(diagnosis = it) } }, "진단명", hint = "(있을 때만)", placeholder = "예: 염증성 장질환(IBD)",
                )
                HelpText("적어 두면 실록과 탕약 알림을 투병 관리에 맞춰 쓸 수 있어요.")
            }
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OkcheSwitchRow("당뇨가 있어요", "켜면 일기와 위젯에 혈당·인슐린 칸이 생겨요.", form.hasDiabetes, { edit { copy(hasDiabetes = it) } })
                if (form.hasDiabetes) InsulinBox(form, errors, ::edit, anchors::of)
            }
        }
        OkcheFooter {
            if (failed) OkcheErrorText(SAVE_ERROR)
            OkcheFooterButton("모시기", {
                if (missing == null) {
                    // 새로 모신 아이는 바로 지금 보는 아이(6-3).
                    val id = newId()
                    saver.save { registerPet(OkcheDatabase.get(context).dao(), form, petId = id).also { if (it.isEmpty()) runCatching { context.setLastPetId(id) } } }
                }
            }, missing) {
                // 남은 것 줄을 누르면 그 칸으로 가서 칸 아래 오류를 보여 줌.
                missingKey?.let { k ->
                    touched = touched + touchKey(k)
                    scope.launch { anchors.goTo(k, focusable = k != "birthDate" && touchKey(k) != "times") }
                }
            }
        }
    }
    app.okche.ui.med.NotifyAskHost(asking, "인슐린 맞을 시간을 알려 드릴까요?", onSaved)
}

/** 당뇨를 켜면 나오는 인슐린 칸: 이름, 시간·양 줄(최소 1줄), 주사기. */
@Composable
private fun InsulinBox(
    form: PetForm,
    errors: Map<String, String>,
    edit: (String?, PetForm.() -> PetForm) -> Unit,
    anchor: (String) -> Modifier,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd))
            .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 14.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("인슐린", Modifier.semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        OkcheTextField(
            form.insulinName, { edit(null) { copy(insulinName = it) } }, "인슐린 이름", hint = "(몰라도 돼요)",
            placeholder = "예: 병원에서 받은 인슐린 이름",
        )
        Column {
            OkcheFieldLabel("맞는 시간과 양", Modifier.padding(bottom = OkcheSpacing.Space2))
            InsulinRowsEditor(form.insulinRows, errors, { key, rows -> edit(key) { copy(insulinRows = rows) } }, anchor)
        }
        Column {
            OkcheChoiceChips(
                "주사기 종류", listOf(Syringe.u40 to "U-40", Syringe.u100 to "U-100", Syringe.unknown to "잘 모르겠어요"),
                form.syringe, { edit(null) { copy(syringe = it) } },
            )
            Text(
                "인슐린과 맞는 주사기를 쓰길 권해요. 모르면 병원에 물어보길 권해요.",
                Modifier.padding(top = OkcheSpacing.Space2),
                color = OkcheColor.InkMuted,
                style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
        }
        Text(
            "병원에서 정해 준 양을 넣어 주세요. 나중에 아이 정보에서 바꿀 수 있어요.",
            color = OkcheColor.InkMuted,
            style = OkcheType.Label.toTextStyle(FontWeight.Normal),
        )
        SourceNote(DIABETES_SOURCE, Modifier.padding(bottom = OkcheSpacing.Space2))
    }
}
