package app.okche.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.goldFrame
import app.okche.ui.okcheSwitchColors
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import java.time.LocalDate
import java.time.Period
import kotlinx.coroutines.launch

// 호칭·나이 표기·지금 보는 아이(plan.md 6-3). iOS `PetTitle`과 같은 규칙.

/** 생일부터 오늘까지 꽉 찬 해. 2/29생은 다음 해 2/28에 1살(기기 달력). */
private fun fullYears(birth: LocalDate, today: LocalDate): Int {
    var years = Period.between(birth, today).years
    if (!birth.plusYears(years + 1L).isAfter(today)) years++
    return years
}

/** 생일(어림 포함)부터 1살 생일 전날까지 '아기씨', 1살 생일부터 '마마'. 생일이 없으면 '아기씨'. */
fun honorific(birthDate: String?, today: LocalDate = LocalDate.now()): String {
    val birth = birthDate?.let(LocalDate::parse) ?: return "아기씨"
    return if (fullYears(birth, today) >= 1) "마마" else "아기씨"
}

/** '콩이 마마' */
fun petTitle(pet: Pet, today: LocalDate = LocalDate.now()) = "${pet.name} ${honorific(pet.birthDate, today)}"

/** 만 나이 '7살', 1살 미만은 만 개월 '3개월'(1개월 미만 '0개월'). 생일이 없으면 null. */
fun petAge(birthDate: String?, today: LocalDate = LocalDate.now()): String? {
    val birth = birthDate?.let(LocalDate::parse) ?: return null
    val years = fullYears(birth, today)
    if (years >= 1) return "${years}살"
    var months = Period.between(birth, today).toTotalMonths()
    if (!birth.plusMonths(months + 1).isAfter(today)) months++
    return "${maxOf(months, 0)}개월"
}

/** 지금 보는 아이: 이 기기만의 설정 `lastPetId`, 없거나 지운 아이면 가장 먼저 모신 아이. `pets`는 지운 줄 뺀 `createdAt` 순. */
fun currentPet(pets: List<Pet>, lastPetId: String?): Pet? = pets.firstOrNull { it.id == lastPetId } ?: pets.firstOrNull()

/** 금테 네모 44: 남색 바탕에 작은 금테(디자인 낙관·아이 바꾸기 시트). 안은 사진(11단계)·캐릭터 자리라 지금은 비움. */
@Composable
private fun SealSquare() {
    Box(Modifier.size(44.dp).background(OkcheColor.NamsaekDeep).goldFrame(inset = 4f, stroke = 1f, knot = 0.45f) { SolidColor(OkcheColor.Geum) })
}

/**
 * 낙관형 표시(탭 머리 오른쪽, 디자인 Record 등): 지금 보는 아이 호칭 + '바꾸기' + 금테 네모. 누르면 아이 바꾸기 시트.
 * 아이가 없으면 같은 자리에 '+ 새 아기씨 모시기'(보드 TabHeaderNoPet).
 */
@Composable
fun PetSeal(pet: Pet?, onSwitch: () -> Unit, onRegister: () -> Unit) {
    if (pet == null) {
        OkchePrimaryButton("+ 새 아기씨 모시기", onRegister, look = ButtonLook.Pill)
        return
    }
    val title = petTitle(pet)
    Row(
        Modifier
            .heightIn(min = 44.dp)
            .clickable(role = Role.Button, onClick = onSwitch)
            .semantics { contentDescription = "지금 보는 아이 $title · 아이 바꾸기" },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.clearAndSetSemantics {}, horizontalAlignment = Alignment.End) {
            // 호칭 16 고운바탕 굵게(디자인 값, 토큰에 16 고운바탕이 없음).
            Text(title, color = OkcheColor.Ink, style = OkcheType.Display.toTextStyle().copy(fontSize = 16.sp, lineHeight = 18.sp))
            Text("바꾸기", color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
        }
        SealSquare()
    }
}

/** '고양이 · 7살 · 휴양 중' */
private fun petDetail(pet: Pet, resting: Boolean) =
    listOfNotNull(if (pet.species == Species.cat) "고양이" else "강아지", petAge(pet.birthDate), "휴양 중".takeIf { resting }).joinToString(" · ")

/** 아이 바꾸기 시트(보드 PetSwitch). 고르면 바로 바뀌고 닫힌다. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PetSwitchSheet(
    pets: List<Pet>,
    current: Pet?,
    restingIds: Set<String>,
    onPick: (String) -> Unit,
    onEdit: () -> Unit,
    onRegister: () -> Unit,
    onDismiss: () -> Unit,
) {
    // 반만 열리면 아래 버튼이 가려져서 처음부터 다 연다.
    ModalBottomSheet(onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "어느 아이를 볼까요?", Modifier.weight(1f).semantics { heading() },
                    color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
                )
                IconButton(onDismiss) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
            }
            Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                for (pet in pets) {
                    val selected = pet.id == current?.id
                    val shape = RoundedCornerShape(OkcheRadius.RadiusMd)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 52.dp)
                            .background(if (selected) OkcheColor.NamsaekSoft else OkcheColor.Paper, shape)
                            .border(1.5.dp, if (selected) OkcheColor.Namsaek else OkcheColor.Line, shape)
                            .clip(shape)
                            .selectable(selected, role = Role.RadioButton) { onPick(pet.id) }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected, null, colors = RadioButtonDefaults.colors(OkcheColor.Namsaek, OkcheColor.LineStrong))
                        SealSquare()
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                petTitle(pet), color = if (selected) OkcheColor.NamsaekDeep else OkcheColor.Ink,
                                style = OkcheType.Body.toTextStyle(if (selected) FontWeight.Bold else FontWeight.Normal),
                            )
                            Text(petDetail(pet, pet.id in restingIds), color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                        }
                    }
                }
            }
            Text("고른 아이가 모든 화면 오른쪽 위에 계속 보여요.", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            Row(horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                OkchePrimaryButton("아이 정보 고치기", onEdit, Modifier.weight(1f), look = ButtonLook.Outlined)
                OkchePrimaryButton("+ 새 아기씨 모시기", onRegister, Modifier.weight(1f), look = ButtonLook.Dashed)
            }
        }
    }
}

/** 더보기 '아이 관리'(보드 More·MoreNoPet): 아이마다 호칭(→ 아이 정보) + '휴양 중 · 관리 쉬기' 스위치(바로 저장). */
@Composable
fun PetManageSection(pets: List<Pet>, restingIds: Set<String>, onEditPet: (String) -> Unit, onRegister: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var failed by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
            .padding(horizontal = OkcheSpacing.Space4, vertical = 6.dp),
    ) {
        Text(
            "아이 관리", Modifier.padding(top = 10.dp, bottom = if (pets.isEmpty()) 12.dp else 4.dp).semantics { heading() },
            color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(),
        )
        if (pets.isEmpty()) {
            Text("아직 모신 아이가 없어요.", color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
            OkchePrimaryButton("+ 새 아기씨 모시기", onRegister, Modifier.padding(top = 12.dp, bottom = 10.dp), look = ButtonLook.Dashed)
            return@Column
        }
        pets.forEachIndexed { i, pet ->
            if (i > 0) HorizontalDivider(thickness = 1.dp, color = OkcheColor.Line)
            val resting = pet.id in restingIds
            val title = petTitle(pet)
            FlowRow(
                Modifier.fillMaxWidth().heightIn(min = 56.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildAnnotatedString {
                        append(title)
                        if (resting) withStyle(SpanStyle(color = OkcheColor.InkMuted, fontSize = 14.sp)) { append(" · 휴양 중") }
                    },
                    Modifier.heightIn(min = 44.dp).clickable(role = Role.Button) { onEditPet(pet.id) }.wrapContentHeight(Alignment.CenterVertically),
                    color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
                )
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .toggleable(resting, role = Role.Switch) { on ->
                            scope.launch {
                                failed = runCatching { setResting(OkcheDatabase.get(context).dao(), pet.id, on) }.isFailure
                            }
                        }
                        .semantics { contentDescription = "$title 휴양 중 · 관리 쉬기" },
                    horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("휴양 중 · 관리 쉬기", Modifier.clearAndSetSemantics {}, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                    Switch(resting, null, colors = okcheSwitchColors())
                }
            }
        }
        if (failed) OkcheErrorText(SAVE_ERROR, Modifier.padding(bottom = 10.dp))
    }
}
