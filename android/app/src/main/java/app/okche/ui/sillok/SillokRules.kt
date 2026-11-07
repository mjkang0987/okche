package app.okche.ui.sillok

import app.okche.store.Food
import app.okche.store.FoodChange
import app.okche.store.MealPlan
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.RestPeriod
import app.okche.ui.record.mealOccurrences
import app.okche.ui.record.mealResult
import app.okche.ui.record.shiftDay
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

// 실록 화면 규칙(plan.md 10-1, 데이터 3장·6-3·6-4·12장). iOS `SillokRules`와 같은 규칙. 화면은 SillokScreen.kt.

/** 빠른 선택 칩. [days]가 없으면 자물쇠(3달·1년·전체 — 14단계 결제 전엔 안내 창). */
enum class SillokRange(val title: String, val days: Int?, private val and: String = "과") {
    week("1주", 7, "와"), month("30일", 30), quarter("3달", null), year("1년", null), all("전체", null);

    /** '30일과'·'1주와'(받침 따라). */
    val withAnd get() = title + and
}

/** 보는 기간: 6시 기준 날 [start]..[end](둘 다 포함), [back] = 오늘이 든 기간에서 몇 칸 앞인지(0이면 다음 안 눌림). */
data class SillokPeriod(val start: String, val end: String, val days: Int, val back: Int) {
    val dayList: List<String> get() = (0 until days).map { shiftDay(start, it.toLong()) }
    /** 직전 같은 길이 기간. */
    fun previous() = SillokPeriod(shiftDay(start, -days.toLong()), shiftDay(start, -1), days, back + 1)
    fun contains(day: String) = day in start..end
}

/** 끝 = 오늘(6시 기준) − [back]×[days]일, 시작 = 끝 − ([days]−1)일. */
fun sillokPeriod(today: String, days: Int, back: Int = 0): SillokPeriod {
    val end = shiftDay(today, -(back.toLong() * days))
    return SillokPeriod(shiftDay(end, -(days - 1).toLong()), end, days, back)
}

/** '9/26 – 10/25'. */
fun periodText(p: SillokPeriod): String = "${md(p.start)} – ${md(p.end)}"

/** 머리 둘째 글: 오늘이 든 기간이면 '최근 30일', 지난 기간이면 '30일'. */
fun periodHeadline(p: SillokPeriod, range: SillokRange): String = if (p.back == 0) "최근 ${range.title}" else range.title

/** '10/14'. */
fun md(day: String): String = LocalDate.parse(day).let { "${it.monthValue}/${it.dayOfMonth}" }

/** 그 아이의 기간 안 기록(지운 것 빼고). */
fun petRecords(records: List<Record>, petId: String, p: SillokPeriod): List<Record> =
    records.filter { it.petId == petId && it.deletedAt == null && p.contains(it.day) }

private fun json(r: Record) = runCatching { JSONObject(r.data) }.getOrNull()
private fun str(r: Record, key: String) = json(r)?.optString(key)?.takeIf { it.isNotEmpty() }
private fun list(r: Record, key: String): List<String> =
    json(r)?.optJSONArray(key)?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()

private val looseShapes = setOf("soft", "loose", "watery")

/** 구토 n회 = 구토 기록 수. */
fun vomitCount(records: List<Record>) = records.count { it.kind == RecordKind.vomit }

/** 묽은 변 n회 = 매화 `shape`가 무름·묽음·물 같음. */
fun looseStoolCount(records: List<Record>) = records.count { it.kind == RecordKind.stool && str(it, "shape") in looseShapes }

/** 직전 기간과 비교 줄: 직전 기간에 그 아이 기록이 하나도 없으면 null. */
fun compareText(now: Int, before: Int, beforeHasRecords: Boolean, range: SillokRange): String? = when {
    !beforeHasRecords -> null
    now < before -> "${before - now}회 줄었어요"
    now > before -> "${now - before}회 늘었어요"
    else -> "직전 ${range.withAnd} 같아요"
}

/** 하루 구토 수(기간의 날 순). */
fun dailyVomits(records: List<Record>, p: SillokPeriod): List<Int> {
    val byDay = records.filter { it.kind == RecordKind.vomit }.groupingBy { it.day }.eachCount()
    return p.dayList.map { byDay[it] ?: 0 }
}

/** 막대 세로 눈금 위 값: 2와 가장 많은 날 중 큰 짝수(가운데 눈금이 정수가 되게). */
fun barTop(counts: List<Int>): Int = maxOf(2, counts.maxOrNull() ?: 0).let { if (it % 2 == 0) it else it + 1 }

/**
 * 다 먹은 끼니 a/b(6-3): 분모 = 기간 각 날의 끼니 차례(그때 계획) 중 예정 시각이 휴양 기간 밖인 것,
 * 분자 = 그 차례 중 같은 `slotKey`에 `all` 기록이 있는 차례 수([records]는 기간 앞뒤 하루까지 — 차례와 기록의 날이 다를 수 있어서). 아직 안 온 끼니([now] 뒤)는 빼고, 차례가 없으면 null(칸 숨김).
 */
fun mealFraction(
    records: List<Record>, plans: List<MealPlan>, rests: List<RestPeriod>, petId: String, p: SillokPeriod, now: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<Int, Int>? {
    val petPlans = plans.filter { it.petId == petId }
    val petRests = rests.filter { it.petId == petId && it.deletedAt == null }
    val slots = p.dayList.flatMap { mealOccurrences(petPlans, it, zone) }
        .filter { it.at <= now }
        .filter { s -> petRests.none { it.startedAt <= s.at && (it.endedAt == null || s.at < it.endedAt) } }
    if (slots.isEmpty()) return null
    val eaten = records.filter { it.kind == RecordKind.meal && it.petId == petId && it.deletedAt == null && mealResult(it) == "all" }
        .mapNotNullTo(mutableSetOf()) { it.slotKey }
    return slots.count { it.key in eaten } to slots.size
}

/** 변 상태 칸: 단단 / 정상 / 무름·묽음 / 물 같음. */
enum class StoolBand(val title: String) { hard("단단"), normal("정상"), soft("무름·묽음"), watery("물 같음") }

/** 변 상태 비율(%) — 반올림해도 합이 100(나머지가 큰 칸부터 1씩). 모양이 있는 매화 기록이 없으면 null(칸 숨김). */
fun stoolShares(records: List<Record>): List<Pair<StoolBand, Int>>? {
    val bands = records.filter { it.kind == RecordKind.stool }.mapNotNull {
        when (str(it, "shape")) {
            "hard" -> StoolBand.hard
            "normal" -> StoolBand.normal
            "soft", "loose" -> StoolBand.soft
            "watery" -> StoolBand.watery
            else -> null
        }
    }
    if (bands.isEmpty()) return null
    val counts = StoolBand.entries.map { b -> bands.count { it == b } }
    val raw = counts.map { it * 100.0 / bands.size }
    val floors = raw.map { it.toInt() }.toMutableList()
    var left = 100 - floors.sum()
    for (i in raw.indices.sortedByDescending { raw[it] - floors[it] }) {
        if (left == 0) break
        floors[i] += 1
        left -= 1
    }
    return StoolBand.entries.zip(floors)
}

/** 소피 칸: 하루 평균(기록 수 ÷ 기간 날 수), 찔끔·분홍·붉은색·힘만 줌 횟수. 소피 기록이 없으면 null(칸 숨김). */
data class UrineSummary(val perDay: Double, val little: Int, val pinkRed: Int, val straining: Int)

fun urineSummary(records: List<Record>, days: Int): UrineSummary? {
    val urine = records.filter { it.kind == RecordKind.urine }
    if (urine.isEmpty()) return null
    return UrineSummary(
        urine.size.toDouble() / days,
        urine.count { str(it, "amount") == "little" },
        urine.count { str(it, "color") == "pinkRed" },
        urine.count { "straining" in list(it, "signs") },
    )
}

/** 직전 기간 소피 하루 평균 — 그 기간에 소피 기록이 없어도 0(직전 기간에 다른 기록이 있을 때만 부름). */
fun urinePerDay(records: List<Record>, days: Int): Double = records.count { it.kind == RecordKind.urine }.toDouble() / days

/** 하루 평균 글자 '3.2'(소수 첫째 자리, 기기 지역과 상관없이 '.'). */
fun perDayText(v: Double): String = String.format(Locale.ROOT, "%.1f", v)

/** 체중 점: 기간 안 `weight` 기록, 시각 순 (시각, 그램). */
fun weightPoints(records: List<Record>): List<Pair<Long, Long>> =
    records.filter { it.kind == RecordKind.weight }.mapNotNull { r -> json(r)?.optLong("grams", -1)?.takeIf { it > 0 }?.let { r.occurredAt to it } }
        .sortedBy { it.first }

/** 체중선 가로 글: 30일은 '1주'~'5주'(보드), 1주는 날짜 7개. */
fun weightAxis(p: SillokPeriod): List<String> =
    if (p.days <= 7) p.dayList.map(::md) else (1..((p.days + 6) / 7)).map { "${it}주" }

/** 바꾼 날(6-4). [what] = '사료'·'탕약', [text] = 줄 글, [spoken] = 그래프 읽기('10/14 사료 바꿈'), [label] = 줄 읽기(화살표 없이). */
data class SillokChange(val day: String, val what: String, val text: String, val spoken: String, val name: String = "") {
    val label get() = "$spoken, $name"
}

/**
 * 이 기간에 바뀐 것(6-4): 사료 = 그 아이 `FoodChange.startedOn`. 약 = 같은 `seriesId` 줄을 `activeFrom` 순으로 —
 * 첫 줄 `activeFrom`(시작), 앞 줄과 주기·양이 다른 줄의 `activeFrom`(바꿈), 마지막 줄의 `activeUntil`(그만 — 추억으로 끝난 것 빼고).
 * 날 순, 같은 날이면 사료 먼저.
 */
fun sillokChanges(
    pet: Pet, foodChanges: List<FoodChange>, foods: List<Food>, schedules: List<MedSchedule>, meds: List<Medication>,
    p: SillokPeriod, zone: ZoneId = ZoneId.systemDefault(),
): List<SillokChange> {
    val foodName = foods.associate { it.id to it.name }
    val medName = meds.associate { it.id to it.name }
    val food = foodChanges.filter { it.petId == pet.id && it.deletedAt == null && p.contains(it.startedOn) }
        .sortedBy { it.createdAt }
        .map {
            val name = foodName[it.foodId].orEmpty()
            SillokChange(it.startedOn, "사료", "${md(it.startedOn)} 사료 → $name", "${md(it.startedOn)} 사료 바꿈", name)
        }
    val med = schedules.filter { it.petId == pet.id && it.deletedAt == null }.groupBy { it.seriesId }.values.flatMap { series ->
        val rows = series.sortedWith(compareBy({ it.activeFrom }, { it.createdAt }))
        val name = medName[rows.first().medicationId].orEmpty()
        val marks = mutableListOf<Pair<Long, String>>()
        rows.forEachIndexed { i, row ->
            if (i == 0) marks += row.activeFrom to "시작"
            else if (doseKey(row) != doseKey(rows[i - 1])) marks += row.activeFrom to "바꿈"
        }
        rows.last().activeUntil?.takeIf { it != pet.memorializedAt }?.let { marks += it to "그만" }
        marks.map { (at, word) ->
            val day = OkcheDay.of(at, zone)
            SillokChange(day, "탕약", "${md(day)} 탕약 → $name · $word", "${md(day)} 탕약 $word", name)
        }
    }.filter { p.contains(it.day) }
    return (food + med).sortedWith(compareBy({ it.day }, { if (it.what == "사료") 0 else 1 }))
}

private fun doseKey(s: MedSchedule) = listOf(s.frequency, s.times, s.everyDays, s.weekdays, s.everyHours, s.amount, s.unit)

/** 막대의 금색 선: 날 → 이름표('사료', '탕약', 같은 날 둘이면 '사료·탕약'). */
fun changeMarks(changes: List<SillokChange>): Map<String, String> =
    changes.groupBy { it.day }.mapValues { (_, list) -> list.map { it.what }.distinct().joinToString("·") }

/** 그래프 화면 읽기 이름: '구토 횟수 일자별 그래프 · 좌우로 넘겨 보기 · 9/26부터 10/25까지 4번 · 10/14 사료 바꿈, 10/20 탕약 바꿈'. */
fun vomitChartLabel(p: SillokPeriod, total: Int, changes: List<SillokChange>): String =
    listOfNotNull(
        "구토 횟수 일자별 그래프", "좌우로 넘겨 보기", "${md(p.start)}부터 ${md(p.end)}까지 ${total}번",
        changes.takeIf { it.isNotEmpty() }?.joinToString(", ") { it.spoken },
    ).joinToString(" · ")

/** 체중 kg 글자 '4.6'(소수 둘째 자리 반올림, 0은 뺌 — `kgText`에서 'kg'만 뺌). */
fun kgNumber(grams: Long): String =
    java.math.BigDecimal.valueOf(grams, 3).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
