package app.okche.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheTextStyle
import app.okche.ui.theme.OkcheType

// 공용 입력 컴포넌트(6-1에서 만듦, 사용자 승인). 값은 디자인 보드(아이 등록) 기준. 6dp·14dp는 보드 값(토큰에 없음).

private val FieldShape = RoundedCornerShape(OkcheRadius.RadiusSm)
private val FieldHeight = 52.dp
private val FieldGap = 6.dp
private val FieldPadding = 14.dp

/** 칸 이름표: 14 medium inkMuted, 뒤에 붙는 말('(선택)')은 보통 굵기. `visible`이 거짓이면 화면에서만 숨긴다(읽기는 됨). */
@Composable
fun OkcheFieldLabel(label: String, modifier: Modifier = Modifier, hint: String? = null, visible: Boolean = true) {
    Text(
        buildAnnotatedString {
            append(label)
            if (hint != null) withStyle(SpanStyle(fontWeight = FontWeight.Normal)) { append(" $hint") }
        },
        if (visible) modifier else modifier.layout { m, c -> m.measure(c).let { layout(0, 0) {} } },
        color = OkcheColor.InkMuted,
        style = OkcheType.Label.toTextStyle(),
    )
}

/** 칸 아래 오류 글자(연지색 14). */
@Composable
fun OkcheErrorText(message: String, modifier: Modifier = Modifier) {
    Text(message, modifier, color = OkcheColor.Yeonji, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
}

/** 52 높이·테두리 1.5·모서리 8·흰 바탕 칸 틀. 포커스면 남색 2 테두리. */
private fun Modifier.fieldBox(focused: Boolean) = this
    .heightIn(min = FieldHeight)
    .background(OkcheColor.Paper, FieldShape)
    .border(if (focused) 2.dp else 1.5.dp, if (focused) OkcheColor.Namsaek else OkcheColor.LineStrong, FieldShape)
    .padding(horizontal = FieldPadding)

/**
 * 글자 입력칸: 위 이름표 + 52 칸(+ 끝 단위 글자) + 오류 글자. 이름표·오류가 입력칸 안에 함께 묶여(TalkBack 한 덩어리),
 * 이름표를 눌러도 입력칸으로 간다(HTML label과 같음).
 */
@Composable
fun OkcheTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    showLabel: Boolean = true,
    placeholder: String? = null,
    suffix: String? = null,
    suffixStyle: OkcheTextStyle = OkcheType.Body,
    error: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    BasicTextField(
        value,
        onValueChange,
        modifier.fillMaxWidth().semantics { if (error != null) error(error) },
        textStyle = OkcheType.Body.toTextStyle().copy(color = OkcheColor.Ink),
        keyboardOptions = keyboardOptions,
        singleLine = true,
        interactionSource = interaction,
        cursorBrush = SolidColor(OkcheColor.Namsaek),
        decorationBox = { inner ->
            Column(verticalArrangement = Arrangement.spacedBy(FieldGap)) {
                OkcheFieldLabel(label, hint = hint, visible = showLabel)
                Row(Modifier.fieldBox(focused), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        if (value.isEmpty() && placeholder != null) {
                            Text(placeholder, color = OkcheColor.InkMuted, style = OkcheType.Body.toTextStyle())
                        }
                        inner()
                    }
                    if (suffix != null) {
                        Text(suffix, Modifier.padding(start = OkcheSpacing.Space2), color = OkcheColor.InkMuted,
                            style = suffixStyle.toTextStyle(FontWeight.Normal))
                    }
                }
                if (error != null) OkcheErrorText(error)
            }
        },
    )
}

/**
 * 입력칸 모양의 고르기 칸(날짜·시간·품종): 누르면 기본 고르기 창이나 메뉴를 연다.
 * 이름표·값·오류를 한 덩어리로 읽고, 역할은 `role`(날짜·시간은 버튼, 품종은 펼침 목록).
 */
@Composable
fun OkchePickerField(
    value: String?,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    showLabel: Boolean = true,
    placeholder: String? = null,
    icon: ImageVector? = null,
    role: Role = Role.Button,
    error: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    Column(
        modifier
            .semantics(mergeDescendants = true) { if (error != null) error(error) }
            .clickable(interaction, indication = null, role = role, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(FieldGap),
    ) {
        OkcheFieldLabel(label, hint = hint, visible = showLabel)
        Row(Modifier.fillMaxWidth().fieldBox(focused), verticalAlignment = Alignment.CenterVertically) {
            Text(
                value ?: placeholder.orEmpty(),
                Modifier.weight(1f),
                color = if (value == null) OkcheColor.InkMuted else OkcheColor.Ink,
                style = OkcheType.Body.toTextStyle(),
            )
            if (icon != null) Icon(icon, null, Modifier.padding(start = OkcheSpacing.Space2).size(20.dp), tint = OkcheColor.InkMuted)
        }
        if (error != null) OkcheErrorText(error)
    }
}

/**
 * 고르기 칩: 40 알약, 하나만 고름(고른 것 남색 바탕·흰 굵은 글자). 처음엔 아무것도 안 고른 상태도 된다.
 * 다시 눌러 안 고름으로 되돌리는 건 `onDeselect`를 줄 때만.
 * 누르는 자리는 44 — 보이는 알약 위아래 2씩 바깥이라 이름표 간격·줄 간격을 그만큼 줄여 보드와 같은 자리에 둔다.
 */
@Composable
fun <T> OkcheChoiceChips(
    label: String,
    options: List<Pair<T, String>>,
    selected: T?,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    hint: String? = null,
    /** 있으면 고른 칩을 다시 눌러 안 고름으로 되돌린다(선택 칸). 필수 칸은 비워 둔다. */
    onDeselect: (() -> Unit)? = null,
) {
    Column(modifier) {
        OkcheFieldLabel(label, Modifier.padding(bottom = OkcheSpacing.Space2 - 2.dp), hint = hint)
        FlowRow(
            Modifier.selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1),
        ) {
            for ((value, text) in options) {
                val isSelected = value == selected
                Box(
                    Modifier
                        .selectable(isSelected, role = Role.RadioButton, onClick = {
                            if (isSelected && onDeselect != null) onDeselect() else onSelect(value)
                        })
                        .padding(vertical = 2.dp),
                ) {
                    val shape = RoundedCornerShape(OkcheRadius.RadiusPill)
                    Box(
                        Modifier
                            .heightIn(min = 40.dp)
                            .then(
                                if (isSelected) Modifier.background(OkcheColor.Namsaek, shape)
                                else Modifier.background(OkcheColor.Paper, shape).border(1.5.dp, OkcheColor.LineStrong, shape),
                            )
                            .padding(horizontal = FieldPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text,
                            color = if (isSelected) OkcheColor.Paper else OkcheColor.Ink,
                            style = OkcheType.Body.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal),
                        )
                    }
                }
            }
        }
    }
}

/** 고르기 칸: 52 높이 카드 여러 칸을 나란히(종 고양이·강아지). 고른 것 namsaekSoft 바탕·남색 테두리·namsaekDeep 굵은 글자. */
@Composable
fun <T> OkcheChoiceCards(
    label: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        OkcheFieldLabel(label, Modifier.padding(bottom = OkcheSpacing.Space2))
        Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
            for ((value, text) in options) {
                val isSelected = value == selected
                val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
                Box(
                    Modifier
                        .weight(1f)
                        .heightIn(min = FieldHeight)
                        .background(if (isSelected) OkcheColor.NamsaekSoft else OkcheColor.Paper, shape)
                        .border(1.5.dp, if (isSelected) OkcheColor.Namsaek else OkcheColor.Line, shape)
                        .selectable(isSelected, role = Role.RadioButton, onClick = { onSelect(value) })
                        .padding(horizontal = OkcheSpacing.Space2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text,
                        color = if (isSelected) OkcheColor.NamsaekDeep else OkcheColor.Ink,
                        style = OkcheType.Body.toTextStyle(if (isSelected) FontWeight.Bold else FontWeight.Normal),
                    )
                }
            }
        }
    }
}

/** 스위치 줄: 제목 16 + 설명 14 + 스위치. 줄 전체가 하나의 스위치(누르는 자리·읽기 모두). */
@Composable
fun OkcheSwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = FieldHeight)
            .background(OkcheColor.Paper, shape)
            .border(1.5.dp, OkcheColor.Line, shape)
            .toggleable(checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = OkcheSpacing.Space4, vertical = FieldPadding),
        horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space1)) {
            Text(title, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
            Text(description, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        Switch(
            checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedTrackColor = OkcheColor.Namsaek,
                checkedThumbColor = OkcheColor.Paper,
                uncheckedTrackColor = OkcheColor.Paper,
                uncheckedBorderColor = OkcheColor.LineStrong,
                uncheckedThumbColor = OkcheColor.LineStrong,
            ),
        )
    }
}

/** 큰 버튼: 52 높이 남색, 모서리 14, 흰 18 굵은 글자. */
@Composable
fun OkchePrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick,
        modifier.fillMaxWidth().heightIn(min = FieldHeight),
        enabled = enabled,
        shape = RoundedCornerShape(OkcheRadius.RadiusMd),
        colors = ButtonDefaults.buttonColors(
            containerColor = OkcheColor.Namsaek,
            contentColor = OkcheColor.Paper,
            disabledContainerColor = OkcheColor.Namsaek,
            disabledContentColor = OkcheColor.Paper,
        ),
    ) {
        Text(text, style = OkcheType.Heading.toTextStyle())
    }
}
