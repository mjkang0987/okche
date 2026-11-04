package app.okche.ui.food

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.okche.store.FoodChange
import app.okche.store.FoodChangeMethod
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.ui.ButtonLook
import app.okche.ui.OkcheChoiceCards
import app.okche.ui.OkcheChoiceChips
import app.okche.ui.OkcheErrorText
import app.okche.ui.OkchePrimaryButton
import app.okche.ui.OkcheTextField
import app.okche.ui.pet.DateField
import app.okche.ui.pet.SourceNote
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheSpacing
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import kotlinx.coroutines.launch
import java.time.LocalDate

fun speciesName(species: Species) = if (species == Species.cat) "고양이" else "강아지"

/** 시트 머리: 제목 + 닫기. */
@Composable
private fun SheetHeader(title: @Composable (Modifier) -> Unit, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        title(Modifier.weight(1f).semantics { heading() })
        IconButton(onClose) { Icon(Icons.Outlined.Close, "닫기", tint = OkcheColor.Ink) }
    }
}

/** '고양이 사료 바꾸는 법 보기 ›' 링크. */
@Composable
private fun GuideLink(species: Species, onClick: () -> Unit) {
    Text(
        "${speciesName(species)} 사료 바꾸는 법 보기 ›",
        Modifier.heightIn(min = 44.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = 10.dp),
        color = OkcheColor.Namsaek, style = OkcheType.Body.toTextStyle(FontWeight.Medium),
    )
}

/**
 * 사료 바꾸기 시트(보드 Food): 사료 이름 + 전에 먹던 사료, 먹기 시작한 날, 어떻게 바꾸나요, 사료 바꾸는 법 보기, '바꾸기'.
 * 성분표 사진 칸은 11단계(사진)까지 숨김.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodChangeSheet(pet: Pet, onClose: () -> Unit) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val changes by remember { dao.foodChangesFlow() }.collectAsState(emptyList())
    val foods by remember { dao.foodsFlow() }.collectAsState(emptyList())
    var name by remember { mutableStateOf("") }
    // 처음 값은 6시 기준 오늘(자정~6시에 바꿔도 바로 '지금 먹는 사료'가 되게).
    var startedOn by remember { mutableStateOf(LocalDate.parse(OkcheDay.of(System.currentTimeMillis()))) }
    var method by remember { mutableStateOf(FoodChangeMethod.gradual) }
    var nameError by remember { mutableStateOf<String?>(null) }
    var guiding by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }

    fun save() {
        if (busy) return
        if (name.isBlank()) {
            nameError = "사료 이름을 넣어 주세요"
            view.announceForAccessibility("사료 이름을 넣어 주세요")
            return
        }
        busy = true
        scope.launch {
            // 오늘 뒤는 안 됨 — 오늘 = 6시 기준(달력에선 자정 뒤 날짜도 고를 수 있어 여기서 맞춤).
            val today = OkcheDay.of(System.currentTimeMillis())
            failed = runCatching { saveFoodChange(dao, pet.id, name, minOf(startedOn.toString(), today), method) }.isFailure
            busy = false
            if (failed) {
                view.announceForAccessibility("저장하지 못했어요. 다시 해 주세요.")
            } else {
                view.announceForAccessibility("사료를 바꿨어요")
                onClose()
            }
        }
    }

    if (guiding) FoodGuideSheet(pet.species) { guiding = false }
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space4),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
        ) {
            SheetHeader({ Text("${pet.name} · 사료 바꾸기", it, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle()) }, onClose)
            OkcheTextField(
                name, { name = it; if (it.isNotBlank()) nameError = null }, "사료 이름",
                placeholder = "예: 가수분해 처방식 1.5kg", error = nameError,
            )
            val previous = previousFoodNames(pet.id, OkcheDay.of(System.currentTimeMillis()), changes, foods)
            if (previous.isNotEmpty()) {
                // 누르면 이름 칸을 채운다.
                OkcheChoiceChips("전에 먹던 사료", previous.map { it to it }, name.trim().takeIf { it in previous }, { name = it; nameError = null })
            }
            DateField(startedOn, "먹기 시작한 날", { it?.let { d -> startedOn = d } }, Modifier, null, "", clearable = false)
            OkcheChoiceCards(
                "어떻게 바꾸나요?",
                listOf(FoodChangeMethod.gradual to "조금씩 섞어서 바꿔요", FoodChangeMethod.atOnce to "한 번에 바꿨어요"),
                method, { method = it },
            )
            GuideLink(pet.species) { guiding = true }
            // 실록 금색 선 안내는 그 단계(10)에서 — 지금 안 되는 안내는 넣지 않는다.
            Text(
                "바꾼 지 7·10·12·14일째에 문안에서 다 바꿨는지 물어볼게요.",
                Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 14.dp, vertical = 12.dp),
                color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
            )
        }
        HorizontalDivider(color = OkcheColor.Line)
        Column(
            Modifier.padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, top = 10.dp, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2),
        ) {
            if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
            OkchePrimaryButton("바꾸기", ::save)
        }
    }
}

private val mixes = listOf(Triple("1~2일", 0.25f, "새 사료 4분의 1"), Triple("3~4일", 0.5f, "반반"), Triple("5~6일", 0.75f, "새 사료 4분의 3"), Triple("7일", 1f, "새 사료만"))

/** 사료 바꾸는 법 레이어(보드 FoodGuide·FoodGuideDog, 출처 미국 동물병원협회). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodGuideSheet(species: Species, onClose: () -> Unit) {
    val cat = species == Species.cat
    ModalBottomSheet(onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = OkcheColor.Hanji) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(start = OkcheSpacing.Space4, end = OkcheSpacing.Space4, bottom = OkcheSpacing.Space6),
            verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space4),
        ) {
            SheetHeader({
                Text(
                    buildAnnotatedString {
                        append("사료 바꾸는 법")
                        withStyle(SpanStyle(color = OkcheColor.InkMuted, fontSize = 0.78.em, fontWeight = FontWeight.Medium)) { append(" · ${speciesName(species)}") }
                    },
                    it, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle(),
                )
            }, onClose)
            Text(
                if (cat) "갑자기 바꾸면 배탈이 나거나 그 사료를 싫어하게 될 수 있어서, 고양이는 천천히 바꾸길 권해요. 길게는 40일까지 걸릴 수 있어요."
                else "갑자기 바꾸면 배탈이 날 수 있어서, 강아지는 7일에 걸쳐 천천히 바꾸길 권해요.",
                color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
            )
            MixCard(cat)
            if (cat) {
                Bullets(
                    "권고사항",
                    listOf(
                        "습식·건식처럼 사료 모양이 바뀌면 한두 달에 걸쳐 바꾸길 권해요.",
                        "너무 차갑거나 뜨겁지 않게, 체온 정도로 맞춰 주길 권해요.",
                        "메스껍거나 토할 때는 새 사료를 잠시 미루길 권해요. 그때 먹은 사료를 길게는 40일까지 싫어할 수 있어요.",
                        "하루에 필요한 만큼은 먹어야 하니, 서두르지 말고 새 사료를 먹을 때까지 천천히 이어 가길 권해요.",
                    ),
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
                Bullets("잘 안 먹을 때 권하는 방법", listOf("캔 사료를 조금 올려 주기", if (cat) "싱겁게 끓인 육수를 조금 더하기(참치·조개·닭)" else "싱겁게 끓인 육수를 조금 더하기(닭·소고기)"))
                Text(
                    "처방식을 먹는 아이는 처방식만 먹어야 할 수 있어요. 무엇이든 더하기 전에 병원에 먼저 물어보길 권해요.",
                    Modifier.fillMaxWidth().background(OkcheColor.NamsaekSoft, RoundedCornerShape(OkcheRadius.RadiusMd)).padding(horizontal = 14.dp, vertical = 12.dp),
                    color = OkcheColor.NamsaekDeep, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
                )
            }
            SourceNote("출처: 미국 동물병원협회 「새 사료로 바꾸는 요령과 기간」. 위 내용은 이 단체의 권고사항이고, 아이마다 다를 수 있어요.")
        }
    }
}

/** 권하는 섞는 순서: 날짜 + 옛 사료·새 사료 비율 막대 + 글자. */
@Composable
private fun MixCard(cat: Boolean) {
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
    ) {
        Text("권하는 섞는 순서", Modifier.semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        FlowRow(Modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(6.dp), itemVerticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).background(OkcheColor.NamsaekSoft, CircleShape).border(1.dp, OkcheColor.LineStrong, CircleShape))
            Text("옛 사료", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
            Box(Modifier.padding(start = 6.dp).size(12.dp).background(OkcheColor.Namsaek, CircleShape))
            Text("새 사료", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        for ((days, share, text) in mixes) {
            Row(Modifier.semantics(mergeDescendants = true) {}, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(days, Modifier.widthIn(min = 56.dp), color = OkcheColor.NamsaekDeep, style = OkcheType.Label.toTextStyle(FontWeight.Bold))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth().height(12.dp).clip(CircleShape).border(1.dp, OkcheColor.LineStrong, CircleShape)) {
                        if (share < 1f) Box(Modifier.weight(1f - share).height(12.dp).background(OkcheColor.NamsaekSoft))
                        Box(Modifier.weight(share).height(12.dp).background(OkcheColor.Namsaek))
                    }
                    Text(text, color = OkcheColor.Ink, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
                }
            }
        }
        Text(
            if (cat) "처음엔 새 사료를 4분의 1만 섞고, 잘 먹는 만큼 늘리길 권해요. 잘 안 먹으면 그 단계에 며칠 더 머물러도 돼요."
            else "처음엔 새 사료를 4분의 1만 섞고, 잘 먹는 만큼 늘리길 권해요.",
            color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal),
        )
    }
}

@Composable
private fun Bullets(title: String, items: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) {
        Text(title, Modifier.semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(FontWeight.Bold))
        for (item in items) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.padding(top = 9.dp).size(6.dp).background(OkcheColor.Namsaek, CircleShape))
                Text(item, color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle())
            }
        }
    }
}

/** 문안 '사료 다 바꿨나요?' 카드(보드 MainFoodCheck): 지금 먹는 사료 바꾸기의 7·10·12·14일째. */
@Composable
fun FoodCheckCard(pet: Pet, day: String) {
    val context = LocalContext.current
    val view = LocalView.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val scope = rememberCoroutineScope()
    val changes by remember { dao.foodChangesFlow() }.collectAsState(emptyList())
    val foods by remember { dao.foodsFlow() }.collectAsState(emptyList())
    var guiding by remember { mutableStateOf(false) }
    // 아이·날이 바뀌면 새 카드(누른 뒤 막아 둔 버튼이 다른 아이·다음 단계 카드에 남지 않게).
    var failed by remember(pet.id, day) { mutableStateOf(false) }
    var busy by remember(pet.id, day) { mutableStateOf(false) }
    val change = foodChangeAt(day, pet.id, changes) ?: return
    val (step, dayNumber) = foodCheck(change, day) ?: return

    // 누르면 카드가 사라지니 TalkBack에도 결과를 알린다.
    fun run(announce: String, work: suspend () -> Unit) {
        if (busy) return
        busy = true
        scope.launch {
            failed = runCatching { work() }.isFailure
            // 성공하면 카드가 사라지니 막아 둔 채로(새 줄이 오기 전 다른 버튼이 옛 값을 덮어쓰지 않게).
            if (failed) busy = false
            view.announceForAccessibility(if (failed) "저장하지 못했어요. 다시 해 주세요." else announce)
        }
    }

    if (guiding) FoodGuideSheet(pet.species) { guiding = false }
    Column(
        Modifier.fillMaxWidth().background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg)).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space3),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("사료 다 바꿨나요?", Modifier.semantics { heading() }, color = OkcheColor.Ink, style = OkcheType.Heading.toTextStyle())
            Text("${foods.firstOrNull { it.id == change.foodId }?.name.orEmpty()} · 바꾼 지 ${dayNumber}일째", color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal))
        }
        val done = @Composable { m: Modifier -> OkchePrimaryButton("다 바꿨어요", { run("사료를 다 바꿨다고 남겼어요") { foodFinished(dao, change, day) } }, m, look = ButtonLook.Emphasis) }
        val notYet = @Composable { m: Modifier -> OkchePrimaryButton("아직이에요", { run(foodLaterNote(step) ?: "알겠어요") { foodNotYet(dao, change, step) } }, m, look = ButtonLook.Outlined) }
        // 큰 글자에선 버튼을 위아래로.
        if (LocalDensity.current.fontScale >= 1.3f) {
            Column(verticalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) { done(Modifier); notYet(Modifier) }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(OkcheSpacing.Space2)) { done(Modifier.weight(1f)); notYet(Modifier.weight(1f)) }
        }
        foodLaterNote(step)?.let { Text(it, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal)) }
        if (failed) OkcheErrorText("저장하지 못했어요. 다시 해 주세요.")
        GuideLink(pet.species) { guiding = true }
    }
}

/** 더보기 '사료 · 처방식' 줄: 아래 줄에 지금 먹는 사료 — 바꾸면 바로 바뀌어 저장한 게 보이게(2026-11-04 사용자). */
@Composable
fun FoodMenuRow(pet: Pet, onClick: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { OkcheDatabase.get(context).dao() }
    val changes by remember { dao.foodChangesFlow() }.collectAsState(emptyList())
    val foods by remember { dao.foodsFlow() }.collectAsState(emptyList())
    val food = foodChangeAt(OkcheDay.of(System.currentTimeMillis()), pet.id, changes)?.let { c -> foodLine(c, foods.firstOrNull { it.id == c.foodId }) }
    MoreMenuRow("사료 · 처방식", detail = food, onClick = onClick)
}

/** 더보기 메뉴 줄: 이름(+ 쉬운 말)(+ 아래 줄) + ›, 52 높이. */
@Composable
fun MoreMenuRow(title: String, note: String? = null, detail: String? = null, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(vertical = if (detail == null) 12.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                buildAnnotatedString {
                    append(title)
                    note?.let { withStyle(SpanStyle(color = OkcheColor.InkMuted, fontSize = 0.85.em, fontWeight = FontWeight.Normal)) { append(" · $it") } }
                },
                color = OkcheColor.Ink, style = OkcheType.Body.toTextStyle(),
            )
            detail?.let { Text(it, color = OkcheColor.InkMuted, style = OkcheType.Label.toTextStyle(FontWeight.Normal)) }
        }
        Text("›", Modifier.clearAndSetSemantics {}, color = OkcheColor.LineStrong, style = OkcheType.Body.toTextStyle())
    }
}
