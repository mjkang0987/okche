package app.okche.ui.pet

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.okche.store.OkcheDatabase
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
import app.okche.ui.singleLineHeight
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
private val timeFormat = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

// 숫자 자판에도 '완료' 키를 두어 키보드를 닫을 수 있게(완료 기본 동작이 키보드 닫기).
private val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
private val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)

/** '첫 번째', '두 번째' … — 인슐린 줄을 읽어 줄 때. */
private fun ordinal(n: Int) =
    listOf("첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열").getOrNull(n - 1)?.let { "$it 번째" } ?: "$n 번째"

/**
 * 아이 등록(디자인 보드 Profile·ProfileDog). 프로필 사진 칸은 11단계(사진 저장)에서.
 * 닫기·시스템 뒤로는 `onClose`, '모시기'가 저장되면 `onSaved`.
 */
@Composable
fun PetRegisterScreen(onClose: () -> Unit, onSaved: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var form by rememberSaveable { mutableStateOf(PetForm()) }
    var errors by remember { mutableStateOf(emptyMap<String, String>()) }
    var saving by remember { mutableStateOf(false) }
    // 오류가 난 첫 칸으로 옮겨 가려고 칸마다 단다.
    val bring = remember { mutableMapOf<String, BringIntoViewRequester>() }
    val focus = remember { mutableMapOf<String, FocusRequester>() }
    fun Modifier.anchor(key: String) =
        bringIntoViewRequester(bring.getOrPut(key) { BringIntoViewRequester() }).focusRequester(focus.getOrPut(key) { FocusRequester() })
    // 고친 칸의 오류는 바로 지운다. 인슐린 줄 수가 바뀌면 줄 번호가 밀리므로 줄 오류를 모두 지운다.
    fun edit(key: String? = null, change: PetForm.() -> PetForm) {
        val rows = form.insulinRows.size
        form = form.change()
        if (key != null) errors = errors - key
        if (form.insulinRows.size != rows) errors = errors.filterKeys { !it.startsWith("time") && !it.startsWith("units") }
    }

    BackHandler(onBack = onClose)
    Column(Modifier.fillMaxSize().background(OkcheColor.Hanji).safeDrawingPadding()) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = OkcheSpacing.Space2),
            horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClose, Modifier.size(44.dp)) {
                Icon(Icons.Outlined.Close, "닫기", Modifier.size(22.dp), tint = OkcheColor.Ink)
            }
            // 큰 글자에서 한 줄에 안 들어가면 '아이 등록'이 아래로 내려간다. TalkBack은 한 머리말로 읽는다.
            FlowRow(
                Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                val title = OkcheType.Title.toTextStyle()
                Text("새 아기씨 모시기", Modifier.singleLineHeight(title.lineHeight), color = OkcheColor.Ink, style = title, maxLines = 1)
                Text("아이 등록", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            }
        }

        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = OkcheSpacing.Space2, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(20.dp), // 보드 칸 사이 20(토큰에 없음)
        ) {
            OkcheTextField(
                form.name, { edit("name") { copy(name = it) } }, "이름", Modifier.anchor("name"),
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
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (form.birthUnknown) {
                    OkcheTextField(
                        form.age, { edit("age") { copy(age = it) } }, "나이", Modifier.anchor("age"), hint = "(대략)", suffix = "살쯤",
                        error = errors["age"], keyboardOptions = numberKeys,
                    )
                } else {
                    OkchePickerField(
                        form.birthDate?.format(dateFormat), "생일",
                        onClick = {
                            val d = form.birthDate ?: LocalDate.now()
                            DatePickerDialog(context, { _, y, m, day -> edit("birthDate") { copy(birthDate = LocalDate.of(y, m + 1, day)) } },
                                d.year, d.monthValue - 1, d.dayOfMonth).apply {
                                // 미래 날짜는 고르지 못하게(저장 때도 한 번 더 검사).
                                datePicker.maxDate = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
                            }.show()
                        },
                        Modifier.anchor("birthDate"),
                        placeholder = "날짜를 골라 주세요",
                        icon = Icons.Outlined.CalendarMonth,
                        error = errors["birthDate"],
                    )
                }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 44.dp)
                        .toggleable(form.birthUnknown, role = Role.Checkbox) { checked ->
                            edit { copy(birthUnknown = checked) }
                            errors = errors - "age" - "birthDate"
                        },
                    horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        form.birthUnknown, null, Modifier.size(20.dp),
                        colors = CheckboxDefaults.colors(checkedColor = OkcheColor.Namsaek, uncheckedColor = OkcheColor.LineStrong),
                    )
                    Text("생일을 몰라요 (나이를 어림잡아 넣을게요)", color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
                }
                if (form.birthUnknown) {
                    HelpText("오늘에서 그만큼 거슬러 간 날을 생일로 잡아요. 1살이 안 됐으면 0을 넣어 주세요.")
                }
            }
            OkcheTextField(
                form.weight, { edit("weight") { copy(weight = it) } }, "체중", Modifier.anchor("weight"), hint = "(kg)",
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
                if (form.hasDiabetes) InsulinBox(form, errors, ::edit) { key -> Modifier.anchor(key) }
            }
            errors["save"]?.let { OkcheErrorText(it) }
            OkchePrimaryButton("모시기", enabled = !saving, onClick = {
                saving = true
                scope.launch {
                    errors = registerPet(OkcheDatabase.get(context).dao(), form)
                    saving = false
                    val first = errors.keys.firstOrNull() ?: return@launch onSaved()
                    bring[first]?.bringIntoView()
                    focus[first]?.requestFocus()
                }
            })
        }
    }
}

@Composable
private fun HelpText(text: String) {
    Text(text, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
}

/** 품종: 입력칸 모양을 누르면 그 종의 목록 메뉴가 열린다(셀렉트). */
@Composable
private fun BreedField(species: Species, breed: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OkchePickerField(breed, "품종", { open = true }, icon = Icons.Outlined.KeyboardArrowDown, role = Role.DropdownList)
        DropdownMenu(open, { open = false }, containerColor = OkcheColor.Paper) {
            for (name in breeds.getValue(species)) {
                DropdownMenuItem(
                    text = {
                        Text(name, color = OkcheColor.Ink,
                            style = OkcheType.Body.toTextStyle(if (name == breed) FontWeight.Bold else FontWeight.Normal))
                    },
                    onClick = { onSelect(name); open = false },
                )
            }
        }
    }
}

/** 털색 동그라미 48(고르면 한지 3 틈 + 남색 2 고리). 큰 글자에서 넘치면 다음 줄로. */
@Composable
private fun FurColorPicker(species: Species, selected: String, onSelect: (String) -> Unit) {
    BoxWithConstraints {
        val width = maxWidth
        Column {
            OkcheFieldLabel("털색", Modifier.padding(bottom = OkcheSpacing.Space2))
            // 이름표가 세 글자까지라 칸 너비 = max(동그라미 48, 글자 세 개). 넷이 한 줄에 안 들어가면 3+1 대신 2+2.
            val itemWidth = maxOf(48.dp, with(LocalDensity.current) { OkcheType.Label.fontSize.toDp() } * 3)
            val fitsOneRow = itemWidth * 4 + OkcheSpacing.Space3 * 3 <= width
            FlowRow(
                Modifier.selectableGroup(),
                maxItemsInEachRow = if (fitsOneRow) 4 else 2,
                horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
                verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            ) {
                for (fur in furColors.getValue(species)) {
                    val isSelected = fur.name == selected
                    Column(
                        Modifier.selectable(isSelected, role = Role.RadioButton) { onSelect(fur.name) },
                        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .drawBehind {
                                    if (isSelected) drawCircle(OkcheColor.Namsaek, radius = 28.dp.toPx(), style = Stroke(2.dp.toPx()))
                                }
                                .background(fur.color, CircleShape)
                                .then(if (fur.outlined) Modifier.border(1.5.dp, OkcheColor.LineStrong, CircleShape) else Modifier),
                        )
                        Text(
                            fur.name,
                            color = OkcheColor.Ink,
                            style = OkcheType.Label.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        )
                    }
                }
            }
        }
    }
}

/** 당뇨를 켜면 나오는 인슐린 칸: 이름, 시간·양 줄(최소 1줄), 주사기. */
@Composable
private fun InsulinBox(
    form: PetForm,
    errors: Map<String, String>,
    edit: (String?, PetForm.() -> PetForm) -> Unit,
    anchor: (String) -> Modifier,
) {
    val context = LocalContext.current
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
            val rows = form.insulinRows
            BoxWithConstraints {
                // 시간 칸에 '오전 12:00'(글자 약 5개)·시계, 양 칸 104, 빼기 44가 한 줄에 안 들어가거나
                // 글자가 커서 양 칸 104에 '단위'가 안 들어가면 시간 칸을 위에, 양·빼기를 아래 줄로 쌓는다.
                val body = with(LocalDensity.current) { OkcheType.Body.fontSize.toDp() }
                val stacked = body > 20.dp || body * 5 + 56.dp + 8.dp + 104.dp + 8.dp + 44.dp > maxWidth
                Column {
                    rows.forEachIndexed { i, row ->
                        if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
                        val n = ordinal(i + 1)
                        val timeField: @Composable (Modifier) -> Unit = { m ->
                            OkchePickerField(
                                LocalTime.parse(row.time).format(timeFormat), "$n 시간",
                                onClick = {
                                    val t = LocalTime.parse(row.time)
                                    TimePickerDialog(context, { _, h, min ->
                                        val time = "%02d:%02d".format(h, min)
                                        edit("time$i") { copy(insulinRows = insulinRows.toMutableList().also { it[i] = it[i].copy(time = time) }) }
                                    }, t.hour, t.minute, DateFormat.is24HourFormat(context)).show()
                                },
                                anchor("time$i").then(m),
                                showLabel = false,
                                icon = Icons.Outlined.Schedule,
                            )
                        }
                        val unitsAndRemove: @Composable RowScope.() -> Unit = {
                            OkcheTextField(
                                row.units,
                                { v -> edit("units$i") { copy(insulinRows = insulinRows.toMutableList().also { it[i] = it[i].copy(units = v) }) } },
                                "$n 양 (단위)",
                                anchor("units$i").then(if (stacked) Modifier.weight(1f) else Modifier.width(104.dp)),
                                showLabel = false,
                                suffix = "단위",
                                suffixStyle = OkcheType.Label,
                                keyboardOptions = decimalKeys,
                            )
                            if (rows.size > 1) {
                                IconButton({ edit(null) { copy(insulinRows = insulinRows.filterIndexed { j, _ -> j != i }) } }, Modifier.size(44.dp)) {
                                    Icon(Icons.Outlined.Close, "$n 시간 빼기", Modifier.size(20.dp), tint = OkcheColor.LineStrong)
                                }
                            }
                        }
                        Column(Modifier.padding(vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val rowArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)
                            if (stacked) {
                                timeField(Modifier.fillMaxWidth())
                                Row(horizontalArrangement = rowArrangement, verticalAlignment = Alignment.CenterVertically, content = unitsAndRemove)
                            } else {
                                Row(horizontalArrangement = rowArrangement, verticalAlignment = Alignment.CenterVertically) {
                                    timeField(Modifier.weight(1f))
                                    unitsAndRemove()
                                }
                            }
                            // 줄 안 두 칸의 오류는 줄 아래에 모아 보인다.
                            listOfNotNull(errors["time$i"], errors["units$i"]).forEach { OkcheErrorText(it) }
                        }
                    }
                    errors["insulinRows"]?.let { OkcheErrorText(it) }
                }
            }
            TextButton(
                { edit(null) { copy(insulinRows = insulinRows + InsulinRow("12:00")) } },
                Modifier.heightIn(min = 44.dp),
                contentPadding = PaddingValues(0.dp),
            ) {
                Text("+ 시간 추가", color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
            }
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
            Modifier.padding(bottom = OkcheSpacing.Space2),
            color = OkcheColor.InkMuted,
            style = OkcheType.Label.toTextStyle(FontWeight.Normal),
        )
    }
}
