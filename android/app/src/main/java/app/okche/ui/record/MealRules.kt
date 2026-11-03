package app.okche.ui.record

import app.okche.store.FixedIds
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 수라·끼니·급식기 규칙(plan.md 7-3, 데이터 7·8장, 고정 ID 1-5). iOS `MealRules`와 같은 규칙.

/** 끼니 차례 하나: 그 끼니가 그날 오는 때와 `slotKey`. */
data class MealOccurrence(val slot: MealSlot, val at: Long, val day: String, val key: String)

/** 끼니 이름: 시간 순으로 1개 아침, 2개 아침·저녁, 3개 아침·점심·저녁, 넷째부터 '끼니 4'(2026-11-04 사용자 — 보드대로 자동). */
fun mealNames(count: Int): List<String> {
    val base = when (count) {
        0 -> emptyList()
        1 -> listOf("아침")
        2 -> listOf("아침", "저녁")
        else -> listOf("아침", "점심", "저녁")
    }
    return base + (base.size until maxOf(count, base.size)).map { "끼니 ${it + 1}" }
}

/** 시간 순으로 정렬하고 이름을 붙인 끼니들. */
fun namedSlots(slots: List<MealSlot>): List<MealSlot> {
    val sorted = slots.sortedBy { it.time }
    return sorted.zip(mealNames(sorted.size)) { s, n -> s.copy(name = n) }
}

/** 쓰는 계획: 열린 줄 중 `activeFrom`이 늦은 것, 같으면 `updatedAt`이 늦은 것(7장). */
fun openMealPlan(plans: List<MealPlan>): MealPlan? =
    plans.filter { it.activeUntil == null && it.deletedAt == null }.maxWithOrNull(compareBy({ it.activeFrom }, { it.updatedAt }))

/** 달력 날짜 [date]의 끼니 시각에 오는 차례. ID 글자는 소문자(5-1) — iOS와 같은 `slotKey`·고정 ID. */
private fun occurrence(plan: MealPlan, slot: MealSlot, date: LocalDate, zone: ZoneId): MealOccurrence {
    val at = date.atTime(LocalTime.parse(slot.time)).atZone(zone).toInstant().toEpochMilli()
    val day = OkcheDay.of(at, zone)
    return MealOccurrence(slot, at, day, "${plan.id.lowercase()}|$day|${slot.time}")
}

/** 그날(6시 기준) 끼니 차례들, 시각 순. 6시 전 끼니는 달력 다음 날에 온다. */
fun mealOccurrences(plan: MealPlan, day: String, zone: ZoneId = ZoneId.systemDefault()): List<MealOccurrence> {
    val date = LocalDate.parse(day)
    return plan.slots.map { occurrence(plan, it, if (it.time < "%02d:00".format(OkcheDay.START_HOUR)) date.plusDays(1) else date, zone) }
        .sortedBy { it.at }
}

/** 지금 차례: 각 차례 구간은 자기 시간 1시간 전부터 다음 차례 1시간 전까지(7장 인슐린과 같은 규칙). */
fun currentMeal(plan: MealPlan, now: Long, zone: ZoneId = ZoneId.systemDefault()): MealOccurrence? {
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return (-1L..1L).flatMap { offset -> plan.slots.map { occurrence(plan, it, today.plusDays(offset), zone) } }
        .filter { it.at - 3_600_000 <= now }
        .maxByOrNull { it.at }
}

/** 수라 기록 `data`: 결과 + 끼니 이름·준 양·남은 양(있을 때만). 칸 이름 순(iOS sortedKeys와 같은 글자). */
fun mealData(result: String, slotName: String?, givenGrams: Int?, leftGrams: Int?): String {
    val json = JSONObject()
    givenGrams?.let { json.put("givenGrams", it) }
    if (result == "some") leftGrams?.let { json.put("leftGrams", it) }
    json.put("result", result)
    slotName?.let { json.put("slotName", it) }
    return json.toString()
}

private fun JSONObject.intOrNull(key: String) = if (has(key)) optInt(key) else null

private fun mealJson(record: Record) = runCatching { JSONObject(record.data) }.getOrDefault(JSONObject())

/** 기록의 남은 양(g) — 남김일 때만. */
fun leftGrams(record: Record): Int? = mealJson(record).intOrNull("leftGrams")

/** 기록의 결과(`all`·`some`·`none`). */
fun mealResult(record: Record): String? = mealJson(record).optString("result").takeIf { it.isNotEmpty() }

/** 일기 수라 바로 기록의 차례: 끼니가 있으면 지금 차례, 그 차례에 이미 기록이 있으면 없음(추가 식사). 끼니가 없으면 없음. */
suspend fun quickMealSlot(dao: OkcheDao, petId: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): MealOccurrence? {
    val plan = petId?.let { openMealPlan(dao.mealPlans(it)) } ?: return null
    val slot = currentMeal(plan, now, zone) ?: return null
    return slot.takeIf { dao.recordBySlot(it.key) == null }
}

/** 수라 화면에서 끼니 결과를 고름: 그 차례 기록이 있으면 결과만 고치고(남김이 아니면 남은 양 비움), 없으면 지금 시각으로 만든다. */
suspend fun pickMeal(dao: OkcheDao, result: String, slot: MealOccurrence, petId: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record {
    val existing = dao.recordBySlot(slot.key)
    return if (existing != null) {
        val json = mealJson(existing)
        existing.copy(data = mealData(result, json.optString("slotName").takeIf { it.isNotEmpty() }, json.intOrNull("givenGrams"), json.intOrNull("leftGrams")), updatedAt = now)
            .also { dao.update(it) }
    } else {
        Record(createdAt = now, petId = petId, kind = RecordKind.meal, occurredAt = now, day = OkcheDay.of(now, zone), slotKey = slot.key,
            data = mealData(result, slot.slot.name, slot.slot.grams, null)).also { dao.insert(it) }
    }
}

/** 끼니 없이 '지금 먹은 것': 지금 시각, `slotKey` 없음. */
suspend fun pickMealNow(dao: OkcheDao, result: String, petId: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record =
    Record(createdAt = now, petId = petId, kind = RecordKind.meal, occurredAt = now, day = OkcheDay.of(now, zone), data = mealData(result, null, null, null))
        .also { dao.insert(it) }

/** '남은 양 (g, 선택)': 0~2000 정수, 비우면 없음. */
suspend fun setLeftGrams(dao: OkcheDao, record: Record, grams: Int?, now: Long = System.currentTimeMillis()): Record {
    val json = mealJson(record)
    return record.copy(
        data = mealData(json.optString("result").ifEmpty { "some" }, json.optString("slotName").takeIf { it.isNotEmpty() }, json.intOrNull("givenGrams"), grams),
        updatedAt = now,
    ).also { dao.update(it) }
}

/** 끼니 시간 저장·급여 방식 바꾸기: 열린 계획을 지금 닫고 같은 시각 새 줄(지난 기록은 그대로). 직접 급여에서 끼니가 없으면 닫기만. */
suspend fun saveMealPlan(dao: OkcheDao, petId: String, mode: MealMode, slots: List<MealSlot>, now: Long = System.currentTimeMillis()) {
    dao.replaceMealPlan(petId, if (slots.isEmpty()) null else MealPlan(createdAt = now, petId = petId, mode = mode, slots = namedSlots(slots), activeFrom = now), now)
}

/**
 * 급식기 자동 기록: 급식기 계획마다 `activeFrom`부터 지금(닫혔으면 닫힌 때)까지 지난 차례를 '다 먹음'으로 고정 ID로 채운다.
 * 같은 ID 줄이 있으면(지운 줄 포함) 만들지 않고, 그 차례에 보호자가 남긴 기록이 있어도 만들지 않는다. 몇 개 만들었는지 돌려준다.
 */
suspend fun fillFeeder(dao: OkcheDao, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
    val plans = dao.mealPlans().filter { it.mode == MealMode.feeder }
    if (plans.isEmpty()) return 0
    val ids = dao.mealRecordIds().toMutableSet()
    val keys = dao.mealSlotKeys().toMutableSet()
    val made = mutableListOf<Record>()
    for (plan in plans) {
        val until = minOf(plan.activeUntil ?: now, now)
        if (plan.activeFrom >= until) continue
        var date = java.time.Instant.ofEpochMilli(plan.activeFrom).atZone(zone).toLocalDate().minusDays(1)
        val last = java.time.Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
        while (!date.isAfter(last)) {
            for (slot in plan.slots) {
                val o = occurrence(plan, slot, date, zone)
                if (o.at < plan.activeFrom || o.at > until || (plan.activeUntil != null && o.at >= plan.activeUntil)) continue
                val id = FixedIds.feederRecord(o.key)
                if (id in ids || o.key in keys) continue
                made += Record(id = id, createdAt = o.at, petId = plan.petId, kind = RecordKind.meal, occurredAt = o.at, day = o.day, slotKey = o.key,
                    data = mealData("all", slot.name, slot.grams, null), auto = true)
                ids += id
                keys += o.key
            }
            date = date.plusDays(1)
        }
    }
    if (made.isNotEmpty()) dao.insertAll(made)
    return made.size
}
