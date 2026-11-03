package app.okche.ui.pet

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.okche.store.OkcheDatabase
import app.okche.ui.TabScreen
import app.okche.ui.theme.OkcheColor
import app.okche.ui.theme.OkcheRadius
import app.okche.ui.theme.OkcheType
import app.okche.ui.toTextStyle
import app.okche.store.OkcheDao
import app.okche.store.Pet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 추억 화면 숫자(plan.md 6-4). iOS `PetLifecycle.stats`와 같은 규칙. 간직·되돌리기·지우기 저장은 OkcheDao 트랜잭션.

/** 함께한 날(가족 된 날부터 오늘까지, 없으면 첫 기록 날부터, 둘 다 없으면 null)·처음/마지막 기록 날짜(없으면 null). */
data class MemorialStats(val days: Long?, val first: String?, val last: String?)

private val memorialDate = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

suspend fun memorialStats(dao: OkcheDao, pet: Pet, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): MemorialStats {
    val day = { at: Long -> Instant.ofEpochMilli(at).atZone(zone).toLocalDate() }
    val first = dao.firstRecordAt(pet.id)?.let(day)
    val last = dao.lastRecordAt(pet.id)?.let(day)
    return MemorialStats(
        days = companionDays(pet.familyDate ?: first?.toString(), today),
        first = first?.format(memorialDate),
        last = last?.format(memorialDate),
    )
}

/**
 * 추억 화면(보드 Memorial, 문안 탭 자리): 머리 '콩이 · 추억으로 간직 중' + 낙관, 잠든 모습 자리, 함께한 날·처음/마지막 기록.
 * 컨디션 좋았던 날들·앨범·내보내기(11·13단계)와 편지·도움받을 곳(보류)은 아직 없음.
 */
@Composable
fun MemorialScreen(pet: Pet, modifier: Modifier = Modifier, seal: @Composable () -> Unit = {}) {
    val context = LocalContext.current
    var stats by remember(pet.id) { mutableStateOf<MemorialStats?>(null) }
    LaunchedEffect(pet.id) { stats = memorialStats(OkcheDatabase.get(context).dao(), pet) }
    TabScreen(pet.name, modifier, "추억으로 간직 중", trailing = seal) {
        SleepingBand(pet.name)
        stats?.let { s ->
            val cards = buildList {
                s.days?.let { add(Triple("함께한 날", "%,d일".format(it), true)) }
                add(Triple("처음 기록", s.first ?: "없어요", false))
                add(Triple("마지막 기록", s.last ?: "없어요", false))
            }
            // 큰 글자에서는 세로로 쌓는다.
            if (LocalDensity.current.fontScale >= 1.3f) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { (l, v, big) -> StatCard(l, v, big, Modifier.fillMaxWidth()) } }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { cards.forEach { (l, v, big) -> StatCard(l, v, big, Modifier.weight(1f)) } }
            }
        }
    }
}

/** 잠든 모습 그림 자리(그림은 캐릭터 단계). 남색 띠에 별 몇 개. */
@Composable
private fun SleepingBand(name: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(120.dp)
            .background(OkcheColor.NamsaekDeep)
            .drawBehind {
                for ((x, y, r) in listOf(Triple(.11f, .23f, 1.6f), Triple(.26f, .15f, 1.2f), Triple(.84f, .25f, 1.6f), Triple(.70f, .13f, 1.1f),
                    Triple(.92f, .58f, 1.2f), Triple(.07f, .67f, 1.1f), Triple(.50f, .12f, 1.4f))) {
                    drawCircle(OkcheColor.GeumLight, r.dp.toPx(), Offset(x * size.width, y * size.height))
                }
            }
            .semantics { contentDescription = "별 아래 잠든 $name 그림 자리"; role = Role.Image },
        contentAlignment = Alignment.BottomCenter,
    ) {
        val shape = RoundedCornerShape(topStart = 60.dp, topEnd = 60.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
        Box(
            Modifier
                .padding(bottom = 18.dp)
                .size(120.dp, 56.dp)
                .drawBehind {
                    val outline = shape.createOutline(size, layoutDirection, this)
                    drawOutline(outline, OkcheColor.OnDeepMuted, style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
                },
            contentAlignment = Alignment.Center,
        ) {
            Text("잠든 모습", Modifier.clearAndSetSemantics {}, color = OkcheColor.OnDeepMuted, style = OkcheType.Caption.toTextStyle())
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, big: Boolean, modifier: Modifier) {
    Column(
        modifier
            .background(OkcheColor.Paper, RoundedCornerShape(OkcheRadius.RadiusLg))
            .padding(12.dp)
            .clearAndSetSemantics { contentDescription = "$label $value" },
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(value, color = OkcheColor.Ink, style = (if (big) OkcheType.Heading else OkcheType.Body).toTextStyle(FontWeight.Bold))
        Text(label, color = OkcheColor.InkMuted, style = OkcheType.Caption.toTextStyle())
    }
}
