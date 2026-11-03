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
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkchePrimaryButton
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
    var errors by rememberSaveable { mutableStateOf(emptyMap<String, String>()) }
    val anchors = remember { Anchors() }
    // 고친 칸의 오류는 바로 지운다. 인슐린 줄 수가 바뀌면 줄 번호가 밀리므로 줄 오류를 모두 지운다.
    fun edit(key: String? = null, change: PetForm.() -> PetForm) {
        val rows = form.insulinRows.size
        form = form.change()
        if (key != null) errors = errors - key
        if (form.insulinRows.size != rows) errors = errors.filterKeys { !it.startsWith("time") && !it.startsWith("units") }
    }
    // 저장 결과 받기(저장 중 화면이 다시 만들어졌어도 여기서 받는다).
    val result = saver.result
    LaunchedEffect(result) {
        if (result == null) return@LaunchedEffect
        errors = result
        val first = result.keys.firstOrNull()
        if (first == null) {
            saver.consume()
            onSaved()
            return@LaunchedEffect
        }
        // consume은 맨 뒤에: result가 바뀌면 이 효과가 취소돼 스크롤(bringIntoView)이 중간에 끊긴다.
        anchors.goTo(first, focusable = true)
        saver.consume()
    }

    // 저장 중엔 닫지 않는다(닫으면 끝난 결과가 남아 다음 등록 화면에 옛 결과가 뜸). 닫을 때 남은 결과도 버린다.
    val close = {
        if (!saver.saving) {
            saver.consume()
            onClose()
        }
    }
    BackHandler(onBack = close)
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding()) {
        FormHeader("새 아기씨 모시기", "아이 등록", Icons.Outlined.Close, "닫기", close, enabled = !saver.saving)

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space2, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(20.dp), // 보드 칸 사이 20(토큰에 없음)
        ) {
            OkcheTextField(
                form.name, { edit("name") { copy(name = it) } }, "이름", anchors.of("name"),
                error = errors["name"],
            )
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
                onBirthUnknown = {
                    edit { copy(birthUnknown = it) }
                    errors = errors - "age" - "birthDate"
                },
                anchor = { anchors.of(it) },
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
                if (form.hasDiabetes) InsulinBox(form, errors, ::edit, { errors = errors.filterKeys { !it.startsWith("time") } }) { key ->
                    anchors.of(key)
                }
            }
            errors["save"]?.let { OkcheErrorText(it) }
            OkchePrimaryButton("모시기", enabled = !saver.saving, onClick = {
                    // 새로 모신 아이는 바로 지금 보는 아이(6-3).
                    val id = newId()
                    saver.save { registerPet(OkcheDatabase.get(context).dao(), form, petId = id).also { if (it.isEmpty()) context.setLastPetId(id) } }
                })
        }
    }
}

/** 당뇨를 켜면 나오는 인슐린 칸: 이름, 시간·양 줄(최소 1줄), 주사기. */
@Composable
private fun InsulinBox(
    form: PetForm,
    errors: Map<String, String>,
    edit: (String?, PetForm.() -> PetForm) -> Unit,
    clearTimeErrors: () -> Unit,
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
            InsulinRowsEditor(form.insulinRows, errors, { key, rows -> edit(key) { copy(insulinRows = rows) } }, clearTimeErrors, anchor)
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
