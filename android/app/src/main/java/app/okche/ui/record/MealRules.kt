package app.okche.ui.record

import app.okche.store.FixedIds
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.food.foodChangeAt
import app.okche.ui.food.foodIdAt
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 수라·끼니·급식기 규칙(plan.md 7-3, 데이터 7·8장, 고정 ID 1-5). iOS `MealRules`와 같은 규칙.

/** 끼니 차례 하나: 그 끼니가 그날 오는 때와 `slotKey`. */
data class MealOccurrence(val slot: MealSlot, val at: Long, val day: String, val key: String, val mode: MealMode = MealMode.direct)

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

/** 하루(6시 기준) 안 순서: 6시 전 끼니는 그날의 마지막(1-2). */
fun dayOrder(time: String): String = (if (time < "%02d:00".format(OkcheDay.START_HOUR)) "1" else "0") + time

/** 하루 순서(6시 기준)로 정렬하고 이름을 붙인 끼니들. */
fun namedSlots(slots: List<MealSlot>): List<MealSlot> {
    val sorted = slots.sortedBy { dayOrder(it.time) }
    return sorted.zip(mealNames(sorted.size)) { s, n -> s.copy(name = n) }
}

/** 쓰는 계획: 열린 줄 중 `activeFrom`이 늦은 것, 같으면 `updatedAt`이 늦은 것(7장). */
fun openMealPlan(plans: List<MealPlan>): MealPlan? =
    plans.filter { it.activeUntil == null && it.deletedAt == null }.maxWithOrNull(compareBy({ it.activeFrom }, { it.updatedAt }))

/** 달력 날짜 [date]의 끼니 시각에 오는 차례. ID 글자는 소문자(5-1) — iOS와 같은 `slotKey`·고정 ID. 시각 글자가 틀리면 없음(다른 기기에서 온 줄). */
private fun occurrence(plan: MealPlan, slot: MealSlot, date: LocalDate, zone: ZoneId): MealOccurrence? {
    val time = runCatching { LocalTime.parse(slot.time) }.getOrNull() ?: return null
    val at = date.atTime(time).atZone(zone).toInstant().toEpochMilli()
    val day = OkcheDay.of(at, zone)
    return MealOccurrence(slot, at, day, "${plan.id.lowercase()}|$day|${slot.time}", plan.mode)
}

/** 그 계획이 쓰이던 동안의 차례인지: `activeFrom` ≤ 시각 < `activeUntil`. 끼니를 바꾸면 '아직 지나지 않은 끼니부터' 새 계획(8장). */
private fun MealPlan.covers(at: Long) = at >= activeFrom && (activeUntil == null || at < activeUntil)

/** 쓸 계획들: 닫힌 줄 전부 + 열린 줄은 하나(7장 — 두 기기 백업을 합쳐 둘이어도 분모가 두 배가 안 되게). */
private fun usable(plans: List<MealPlan>): List<MealPlan> {
    val live = plans.filter { it.deletedAt == null }
    return live.filter { it.activeUntil != null } + listOfNotNull(openMealPlan(live))
}

/** 그날(6시 기준) 끼니 차례들 — 그날 계획을 바꿨으면 바꾸기 전 차례는 앞 계획, 뒤는 새 계획에서. 시각 순. */
fun mealOccurrences(plans: List<MealPlan>, day: String, zone: ZoneId = ZoneId.systemDefault()): List<MealOccurrence> =
    usable(plans).flatMap { plan -> mealOccurrences(plan, day, zone).filter { plan.covers(it.at) } }.sortedBy { it.at }

/** 지금 차례(여러 계획 — 바꾼 날에도 지난 끼니는 앞 계획의 차례). */
fun currentMeal(plans: List<MealPlan>, now: Long, zone: ZoneId = ZoneId.systemDefault()): MealOccurrence? {
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return usable(plans).flatMap { plan ->
        (-1L..1L).flatMap { offset -> plan.slots.mapNotNull { occurrence(plan, it, today.plusDays(offset), zone) } }.filter { plan.covers(it.at) }
    }.filter { it.at - 3_600_000 <= now }.maxByOrNull { it.at }
}

/** 그날(6시 기준) 끼니 차례들, 시각 순. 6시 전 끼니는 달력 다음 날에 온다. */
fun mealOccurrences(plan: MealPlan, day: String, zone: ZoneId = ZoneId.systemDefault()): List<MealOccurrence> {
    val date = LocalDate.parse(day)
    return plan.slots.mapNotNull { occurrence(plan, it, if (it.time < "%02d:00".format(OkcheDay.START_HOUR)) date.plusDays(1) else date, zone) }
        .sortedBy { it.at }
}

/** 수라 기록 `data`: 결과 + 그때 먹던 사료·끼니 이름·준 양·남은 양(있을 때만). 칸 이름 순(iOS sortedKeys와 같은 글자). */
fun mealData(result: String, foodId: String?, slotName: String?, givenGrams: Int?, leftGrams: Int?): String {
    val json = JSONObject()
    foodId?.let { json.put("foodId", it) }
    givenGrams?.let { json.put("givenGrams", it) }
    if (result == "some") leftGrams?.let { json.put("leftGrams", it) }
    json.put("result", result)
    slotName?.let { json.put("slotName", it) }
    return json.toString()
}

private fun JSONObject.intOrNull(key: String) = if (has(key)) optInt(key) else null

private fun JSONObject.foodIdOrNull() = optString("foodId").takeIf { it.isNotEmpty() }

private fun mealJson(record: Record) = runCatching { JSONObject(record.data) }.getOrDefault(JSONObject())

/** 기록의 남은 양(g) — 남김일 때만. */
fun leftGrams(record: Record): Int? = mealJson(record).intOrNull("leftGrams")

/** 기록의 준 양(g). */
fun givenGrams(record: Record): Int? = mealJson(record).intOrNull("givenGrams")

/** 기록의 결과(`all`·`some`·`none`). */
fun mealResult(record: Record): String? = mealJson(record).optString("result").takeIf { it.isNotEmpty() }

/** 일기 수라 바로 기록의 차례: 끼니가 있으면 지금 차례, 그 차례에 이미 기록이 있으면 없음(추가 식사). 끼니가 없으면 없음. */
suspend fun quickMealSlot(dao: OkcheDao, petId: String?, now: Long, zone: ZoneId = ZoneId.systemDefault()): MealOccurrence? {
    val plans = petId?.let { dao.mealPlans(it) } ?: return null
    if (openMealPlan(plans) == null) return null
    val slot = currentMeal(plans, now, zone) ?: return null
    return slot.takeIf { dao.recordBySlot(it.key) == null }
}

/** 끼니 차례 기록의 새 ID: 급식기 차례는 자동 기록과 같은 고정 ID(1-5), 그 ID 줄이 지운 채 있으면 새 ID. */
suspend fun newMealRecordId(dao: OkcheDao, slot: MealOccurrence): String {
    if (slot.mode != MealMode.feeder) return app.okche.store.newId()
    val fixed = FixedIds.feederRecord(slot.key)
    return if (dao.recordIdCount(fixed) == 0) fixed else app.okche.store.newId()
}

/** 수라 화면에서 끼니 결과를 고름: 그 차례 기록이 있으면 결과만 고치고(남김이 아니면 남은 양 비움), 없으면 지금 시각으로 만든다. */
suspend fun pickMeal(dao: OkcheDao, result: String, slot: MealOccurrence, petId: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record {
    val existing = dao.recordBySlot(slot.key)
    return if (existing != null) {
        val json = mealJson(existing)
        // 같은 결과를 다시 고르면 아무것도 안 바꾼다('자동 기록됨' 표시도 그대로).
        if (json.optString("result") == result) return existing
        // 보호자가 고친 기록은 자동 기록이 아니다.
        existing.copy(data = mealData(result, json.foodIdOrNull(), json.optString("slotName").takeIf { it.isNotEmpty() }, json.intOrNull("givenGrams"), json.intOrNull("leftGrams")), updatedAt = now, auto = false)
            .also { dao.update(it) }
    } else {
        Record(id = newMealRecordId(dao, slot), createdAt = now, petId = petId, kind = RecordKind.meal, occurredAt = now, day = OkcheDay.of(now, zone), slotKey = slot.key,
            data = mealData(result, foodIdAt(dao, petId, OkcheDay.of(now, zone)), slot.slot.name, slot.slot.grams, null)).also { dao.insert(it) }
    }
}

/** 끼니 없이 남긴 수라의 결과 바꾸기: 결과만(남김이 아니면 남은 양 비움), 같은 결과면 그대로. */
suspend fun setMealResult(dao: OkcheDao, record: Record, result: String, now: Long = System.currentTimeMillis()) {
    val json = mealJson(record)
    if (json.optString("result") == result) return
    dao.update(record.copy(data = mealData(result, json.foodIdOrNull(), json.optString("slotName").takeIf { it.isNotEmpty() }, json.intOrNull("givenGrams"), json.intOrNull("leftGrams")), updatedAt = now, auto = false))
}

/** 끼니 없이 '지금 먹은 것': 지금 시각, `slotKey` 없음. */
suspend fun pickMealNow(dao: OkcheDao, result: String, petId: String, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record =
    Record(createdAt = now, petId = petId, kind = RecordKind.meal, occurredAt = now, day = OkcheDay.of(now, zone), data = mealData(result, foodIdAt(dao, petId, OkcheDay.of(now, zone)), null, null, null))
        .also { dao.insert(it) }

/** '남은 양 (g, 선택)': 0~2000 정수, 비우면 없음. */
suspend fun setLeftGrams(dao: OkcheDao, stale: Record, grams: Int?, now: Long = System.currentTimeMillis()): Record {
    // 화면이 쥔 기록 대신 지금 저장된 줄을 다시 읽어 고친다(같은 순간 결과 고르기를 덮어 되돌리지 않게).
    val record = if (stale.slotKey == null) stale else dao.recordBySlot(stale.slotKey) ?: return stale // 그 사이 지워졌으면 되살리지 않음
    val json = mealJson(record)
    return record.copy(
        data = mealData(json.optString("result").ifEmpty { "some" }, json.foodIdOrNull(), json.optString("slotName").takeIf { it.isNotEmpty() }, json.intOrNull("givenGrams"), grams),
        updatedAt = now,
    ).also { dao.update(it) }
}

/**
 * 끼니 시간 저장·급여 방식 바꾸기: 열린 계획을 지금 닫고 같은 시각 새 줄(지난 기록은 그대로). 직접 급여에서 끼니가 없으면 닫기만.
 * 바꾼 게 없으면 아무것도 안 한다. 아직 오지 않은 끼니를 미리 남겼으면(1시간 전 구간) 새 계획에 같은 시각이 있을 때 그 차례로 옮긴다
 * — 안 그러면 새 칸이 '아직 기록 전'이 되어 두 번 기록된다.
 */
suspend fun saveMealPlan(dao: OkcheDao, petId: String, mode: MealMode, slots: List<MealSlot>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()) {
    val old = openMealPlan(dao.mealPlans(petId))
    val newSlots = namedSlots(slots)
    if (old != null && old.mode == mode && old.slots == newSlots) return
    if (old == null && newSlots.isEmpty()) return
    val plan = if (newSlots.isEmpty()) null else MealPlan(createdAt = now, petId = petId, mode = mode, slots = newSlots, activeFrom = now)
    val moved = if (old == null || plan == null) emptyList() else dao.petMealsWithSlot(petId).mapNotNull { record ->
        val parts = record.slotKey?.split("|")?.takeIf { it.size == 3 && it[0] == old.id.lowercase() } ?: return@mapNotNull null
        val o = mealOccurrences(plan, parts[1], zone).firstOrNull { it.slot.time == parts[2] } ?: return@mapNotNull null
        if (o.at < now) null else record.copy(slotKey = o.key, updatedAt = now)
    }
    dao.replaceMealPlan(petId, plan, now, moved)
}

// shortcut: 앞으로 올 때마다 급식기 계획의 모든 날을 처음부터 돈다(이미 있으면 건너뜀), 오래 쓰다 느려지면 마지막으로 채운 날을 기기 설정에 두고 그 뒤만 돈다.
// shortcut: 확인과 넣기가 한 트랜잭션이 아니라 수라 고르기와 동시에 돌면 한 차례에 둘이 생길 수 있음(아주 짧은 순간), 백업 합치기(13단계) 때 차례 중복 정리와 함께 본다.
/**
 * 급식기 자동 기록: 급식기 계획마다 `activeFrom`부터 지금(닫혔으면 닫힌 때)까지 지난 차례를 '다 먹음'으로 고정 ID로 채운다.
 * 같은 ID 줄이 있으면(지운 줄 포함) 만들지 않고, 그 차례에 보호자가 남긴 기록이 있어도 만들지 않는다. 몇 개 만들었는지 돌려준다.
 */
suspend fun fillFeeder(dao: OkcheDao, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Int {
    // 아이마다 쓸 계획(열린 줄 하나 — 7장)만, 그중 급식기.
    val plans = dao.mealPlans().groupBy { it.petId }.values.flatMap(::usable).filter { it.mode == MealMode.feeder }
    if (plans.isEmpty()) return 0
    val ids = dao.mealRecordIds().toMutableSet()
    val keys = dao.mealSlotKeys().toMutableSet()
    val changes = dao.foodChanges()
    val made = mutableListOf<Record>()
    for (plan in plans) {
        val until = minOf(plan.activeUntil ?: now, now)
        if (plan.activeFrom >= until) continue
        var date = java.time.Instant.ofEpochMilli(plan.activeFrom).atZone(zone).toLocalDate().minusDays(1)
        val last = java.time.Instant.ofEpochMilli(until).atZone(zone).toLocalDate()
        while (!date.isAfter(last)) {
            for (slot in plan.slots) {
                val o = occurrence(plan, slot, date, zone) ?: continue
                if (o.at < plan.activeFrom || o.at > until || (plan.activeUntil != null && o.at >= plan.activeUntil)) continue
                val id = FixedIds.feederRecord(o.key)
                if (id in ids || o.key in keys) continue
                // `createdAt`은 만든 때, `updatedAt`만 끼니 예정 시각(1-5 — 보호자가 고친 기록이 병합에서 이기게).
                made += Record(id = id, createdAt = now, updatedAt = o.at, petId = plan.petId, kind = RecordKind.meal, occurredAt = o.at, day = o.day, slotKey = o.key,
                    data = mealData("all", foodChangeAt(o.day, plan.petId, changes)?.foodId, slot.name, slot.grams, null), auto = true)
                ids += id
                keys += o.key
            }
            date = date.plusDays(1)
        }
    }
    if (made.isNotEmpty()) dao.insertAll(made)
    return made.size
}
