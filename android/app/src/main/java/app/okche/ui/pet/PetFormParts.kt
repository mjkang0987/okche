package app.okche.ui.pet

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.okche.store.Species
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkcheFieldLabel
import app.okche.ui.OkchePickerField
import app.okche.ui.OkcheTextField
import app.okche.ui.singleLineHeight
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 아이 등록(6-1)과 아이 정보 고치기·인슐린 설정(6-2)이 함께 쓰는 입력 조각.

internal val dateFormat = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)
internal val timeFormat = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN)

// 숫자 자판에도 '완료' 키를 두어 키보드를 닫을 수 있게(완료 기본 동작이 키보드 닫기).
internal val numberKeys = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
internal val decimalKeys = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done)

/** '첫 번째', '두 번째' … — 인슐린 줄을 읽어 줄 때. */
internal fun ordinal(n: Int) =
    listOf("첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열").getOrNull(n - 1)?.let { "$it 번째" } ?: "$n 번째"

/** 화면 머리: 왼쪽 버튼(닫기·뒤로) + 제목 22 + 쉬운 말 14. 큰 글자에서 한 줄에 안 들어가면 쉬운 말이 아래로. */
@Composable
internal fun FormHeader(title: String, note: String, icon: ImageVector, iconLabel: String, onClick: () -> Unit, enabled: Boolean = true) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = OkcheSpacing.Space2),
        horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick, Modifier.size(44.dp), enabled = enabled) {
            Icon(icon, iconLabel, Modifier.size(22.dp), tint = OkcheColor.Ink)
        }
        // TalkBack은 한 머리말로 읽는다.
        FlowRow(
            Modifier.weight(1f).semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            itemVerticalAlignment = Alignment.CenterVertically,
        ) {
            val style = OkcheType.Title.toTextStyle()
            Text(title, Modifier.singleLineHeight(style.lineHeight), color = OkcheColor.Ink, style = style, maxLines = 1)
            Text(note, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
    }
}

/** 생일 또는 '생일을 몰라요' + 나이 (대략). 칸 열쇠 birthDate·age. */
@Composable
internal fun BirthFields(
    birthUnknown: Boolean,
    age: String,
    birthDate: LocalDate?,
    errors: Map<String, String>,
    onAge: (String) -> Unit,
    onBirthDate: (LocalDate?) -> Unit,
    onBirthUnknown: (Boolean) -> Unit,
    anchor: (String) -> Modifier,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (birthUnknown) {
            OkcheTextField(
                age, onAge, "나이", anchor("age"), hint = "(대략)", suffix = "살쯤",
                error = errors["age"], keyboardOptions = numberKeys, description = AGE_HELP,
            )
        } else {
            DateField(birthDate, "생일", onBirthDate, anchor("birthDate"), errors["birthDate"], "생일 지우기")
        }
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .toggleable(birthUnknown, role = Role.Checkbox, onValueChange = onBirthUnknown),
            horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(
                birthUnknown, null, Modifier.size(20.dp),
                colors = CheckboxDefaults.colors(checkedColor = OkcheColor.Namsaek, uncheckedColor = OkcheColor.LineStrong),
            )
            Text("생일을 몰라요 (나이를 어림잡아 넣을게요)", color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
        }
        if (birthUnknown) {
            // 나이 칸 설명으로 이미 읽으므로 여기선 보이기만.
            HelpText(AGE_HELP, Modifier.clearAndSetSemantics {})
        }
    }
}

/** 날짜 칸: 누르면 달력, 오늘 뒤는 고를 수 없음. 비울 수 있다. */
@Composable
internal fun DateField(date: LocalDate?, label: String, onDate: (LocalDate?) -> Unit, modifier: Modifier, error: String?, clearLabel: String) {
    val context = LocalContext.current
    OkchePickerField(
        date?.format(dateFormat), label,
        onClick = {
            val d = date ?: LocalDate.now()
            DatePickerDialog(context, { _, y, m, day -> onDate(LocalDate.of(y, m + 1, day)) }, d.year, d.monthValue - 1, d.dayOfMonth).apply {
                // 미래 날짜는 고르지 못하게(저장 때도 한 번 더 검사).
                datePicker.maxDate = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() - 1
            }.show()
        },
        modifier,
        placeholder = "날짜를 골라 주세요",
        icon = Icons.Outlined.CalendarMonth,
        error = error,
        onClear = { onDate(null) },
        clearLabel = clearLabel,
    )
}

/** 털색 고른 고리가 동그라미 밖으로 나가는 폭(한지 틈 3 + 남색 고리 2). */
private val Ring = 5.dp

internal const val AGE_HELP = "오늘에서 그만큼 거슬러 간 날을 생일로 잡아요. 1살이 안 됐으면 0을 넣어 주세요."

@Composable
internal fun HelpText(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
}

/** 품종: 입력칸 모양을 누르면 그 종의 목록 메뉴가 열린다(셀렉트). */
@Composable
internal fun BreedField(species: Species, breed: String, onSelect: (String) -> Unit) {
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
                    modifier = Modifier.semantics { selected = name == breed },
                )
            }
        }
    }
}

/** 털색 동그라미 48(고르면 한지 3 틈 + 남색 2 고리). 큰 글자에서 넘치면 다음 줄로. */
@Composable
internal fun FurColorPicker(species: Species, selected: String, onSelect: (String) -> Unit) {
    BoxWithConstraints {
        val width = maxWidth
        Column {
            // 고른 고리(동그라미 밖 5)까지 칸 안에 들도록 칸을 사방 5 넓히고, 그만큼 간격을 줄여 보드와 같은 자리에 둔다.
            OkcheFieldLabel("털색", Modifier.padding(bottom = OkcheSpacing.Space2 - Ring))
            // 이름표가 세 글자까지라 칸 너비 = max(동그라미+고리 58, 글자 세 개). 넷이 한 줄에 안 들어가면 3+1 대신 2+2.
            val itemWidth = maxOf(48.dp + Ring * 2, with(LocalDensity.current) { OkcheType.Label.fontSize.toDp() } * 3)
            val gap = OkcheSpacing.Space3 - Ring * 2
            val fitsOneRow = itemWidth * 4 + gap * 3 <= width
            // 첫 동그라미가 보드처럼 왼쪽 여백 16에 붙게 고리 여백만큼 왼쪽으로 당긴다.
            FlowRow(
                Modifier.offset(x = -Ring).selectableGroup(),
                maxItemsInEachRow = if (fitsOneRow) 4 else 2,
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            ) {
                for (fur in furColors.getValue(species)) {
                    val isSelected = fur.name == selected
                    // 누름·포커스 덧칠이 네모로 삐져나오지 않게 칸 모양으로 자른다.
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(OkcheRadius.RadiusSm))
                            .selectable(isSelected, role = Role.RadioButton) { onSelect(fur.name) },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .padding(Ring)
                                .size(48.dp)
                                .drawBehind {
                                    if (isSelected) drawCircle(OkcheColor.Namsaek, radius = 28.dp.toPx(), style = Stroke(2.dp.toPx()))
                                }
                                .background(fur.color, CircleShape)
                                .then(if (fur.outlined) Modifier.border(1.5.dp, OkcheColor.LineStrong, CircleShape) else Modifier),
                        )
                        Text(
                            fur.name,
                            Modifier.padding(horizontal = Ring),
                            color = OkcheColor.Ink,
                            style = OkcheType.Label.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        )
                    }
                }
            }
        }
    }
}

/**
 * 인슐린 맞는 시간과 양 줄들 + '+ 시간 추가'(아이 등록·인슐린 설정 공통). 바탕 상자는 쓰는 쪽이 둔다.
 * `onRows(오류 열쇠, 새 줄들)` — 열쇠가 있으면 그 칸 오류를 지운다.
 */
@Composable
internal fun InsulinRowsEditor(
    rows: List<InsulinRow>,
    errors: Map<String, String>,
    onRows: (String?, List<InsulinRow>) -> Unit,
    clearTimeErrors: () -> Unit,
    anchor: (String) -> Modifier,
) {
    val context = LocalContext.current
    Column {
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
                                    // 시간 하나를 바꾸면 다른 줄의 '같은 시간' 오류도 풀릴 수 있어 시간 오류를 모두 지운다.
                                    onRows(null, rows.toMutableList().also { it[i] = it[i].copy(time = time) })
                                    clearTimeErrors()
                                }, t.hour, t.minute, DateFormat.is24HourFormat(context)).show()
                            },
                            anchor("time$i").then(m),
                            showLabel = false,
                            error = errors["time$i"],
                            showError = false,
                            icon = Icons.Outlined.Schedule,
                        )
                    }
                    val unitsAndRemove: @Composable RowScope.() -> Unit = {
                        OkcheTextField(
                            row.units,
                            { v -> onRows("units$i", rows.toMutableList().also { it[i] = it[i].copy(units = v) }) },
                            "$n 양 (단위)",
                            anchor("units$i").then(if (stacked) Modifier.weight(1f) else Modifier.width(104.dp)),
                            showLabel = false,
                            error = errors["units$i"],
                            showError = false,
                            suffix = "단위",
                            suffixStyle = OkcheType.Label,
                            keyboardOptions = decimalKeys,
                        )
                        if (rows.size > 1) {
                            IconButton({ onRows(null, rows.filterIndexed { j, _ -> j != i }) }, Modifier.size(44.dp)) {
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
            { onRows(null, rows + InsulinRow(nextFreeTime(rows))) },
            Modifier.heightIn(min = 44.dp),
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("+ 시간 추가", color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium))
        }
    }
}

/** 새 줄 시각: 12:00부터 한 시간씩 넘겨 아직 없는 시각(두 번 눌러도 같은 시간이 생기지 않게 — iOS와 같음). */
private fun nextFreeTime(rows: List<InsulinRow>): String {
    val used = rows.map { it.time }.toSet()
    return (0 until 24).map { "%02d:00".format((12 + it) % 24) }.firstOrNull { it !in used } ?: "12:00"
}
