package app.okche.ui.diabetes

import app.okche.store.InsulinPlan
import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.med.hhmm
import app.okche.ui.med.medNumber
import app.okche.ui.pet.toDecimal
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

// 인슐린 규칙(plan.md 9-2, 데이터 3장·7장). iOS `InsulinRules`와 같은 규칙.

/** 시각이 든 차례: 차례 시각 HH:mm, 정해 둔 양, 구간 [start, end), 차례 시각(start + 1시간), `slotKey`. */
data class InsulinSlotAt(val time: String, val units: Double, val start: Long, val end: Long, val occurrence: Long, val key: String)

private const val HOUR = 3_600_000L

/** [at]이 든 구간의 차례(7장). `slotKey` = `{planId}|{차례 시각의 6시 기준 날}|{HH:mm}`. 차례가 없으면 null. */
fun insulinSlotAt(plan: InsulinPlan, at: Long, zone: ZoneId = ZoneId.systemDefault()): InsulinSlotAt? {
    val (start, end) = insulinWindow(plan, at, zone) ?: return null
    val occurrence = start + HOUR
    val local = Instant.ofEpochMilli(occurrence).atZone(zone).toLocalTime()
    val slot = plan.slots.firstOrNull { runCatching { LocalTime.parse(it.time) }.getOrNull() == local } ?: return null
    val time = hhmm(occurrence, zone)
    return InsulinSlotAt(time, slot.units, start, end, occurrence, "${plan.id}|${OkcheDay.of(occurrence, zone)}|$time")
}

private fun json(r: Record) = runCatching { JSONObject(r.data) }.getOrNull()

fun insulinStatus(r: Record): String? = json(r)?.optString("status")?.takeIf { it in setOf("full", "partial", "skipped") }
fun insulinUnits(r: Record): Double? = json(r)?.takeIf { it.has("units") }?.optDouble("units")?.takeIf { it.isFinite() }
fun insulinMeal(r: Record): String? = json(r)?.optString("mealBefore")?.takeIf { it in setOf("all", "some", "none") }

private fun isInsulinIn(r: Record, petId: String, start: Long, end: Long) =
    r.petId == petId && r.deletedAt == null && r.kind == RecordKind.insulin && r.occurredAt in start until end

/** 처리한 상태(두 번 맞히기 방지): 구간 안 그 아이의 가장 늦은 `insulin` 기록(상태 무엇이든, 어느 계획·기기에서 왔든). */
fun insulinHandled(petId: String, records: List<Record>, slot: InsulinSlotAt): Record? =
    records.filter { isInsulinIn(it, petId, slot.start, slot.end) }.maxByOrNull { it.occurredAt }

/** 다음 차례 시각 = 지금 구간 끝 + 1시간. */
fun insulinNext(slot: InsulinSlotAt): Long = slot.end + HOUR

/** 이미 맞은 시간대 경고: [at]이 든 구간에 그 아이의 다른(고치는 기록 빼고) `full`·`partial` 기록 — 가장 늦은 것. 건너뜀만이면 없음. */
fun insulinTwice(petId: String, plan: InsulinPlan?, records: List<Record>, at: Long, editingId: String?, zone: ZoneId = ZoneId.systemDefault()): Record? {
    val slot = plan?.let { insulinSlotAt(it, at, zone) } ?: return null
    return records.filter { it.id != editingId && isInsulinIn(it, petId, slot.start, slot.end) && insulinStatus(it) in setOf("full", "partial") }
        .maxByOrNull { it.occurredAt }
}

/** 맞은 양 칸: 0보다 크고 100 이하(소수 됨 — 인슐린 설정과 같은 숫자 규칙). */
fun insulinUnitsNumber(text: String): Double? = text.toDecimal()?.takeIf { it > 0 && it <= 100 }

/** 빈칸이면 안내 없음(버튼만 흐리게), 틀리면 인슐린 설정과 같은 글. */
fun insulinUnitsError(text: String): String? = if (text.isBlank() || insulinUnitsNumber(text) != null) null else "0보다 크고 100 이하인 숫자로 넣어 주세요"

/** `insulin` 기록 내용 — 칸 이름 순(iOS `.sortedKeys`와 같게). 계획이 없으면 `planId`·밥을 안 골랐으면 `mealBefore` 칸 없음. */
fun insulinData(planId: String?, units: Double, status: String, mealBefore: String?): String =
    JSONObject().apply {
        if (mealBefore != null) put("mealBefore", mealBefore)
        if (planId != null) put("planId", planId)
        put("status", status)
        put("units", if (status == "skipped") 0.0 else units)
    }.toString()

/**
 * 인슐린 남기기(일기 '맞음'·'건너뜀'과 시트 '기록하기'). 차례 = 시각이 든 구간의 차례([plan]이 없으면 `planId`·`slotKey` 없음).
 * 고치기는 같은 기록의 `data`·`slotKey`(시간을 바꿨으면 `occurredAt`·`day`도)·`updatedAt` — 계획이 없으면 기록의 계획·차례 그대로.
 */
suspend fun saveInsulin(
    dao: OkcheDao, petId: String?, plan: InsulinPlan?, units: Double, status: String, mealBefore: String?, occurredAt: Long,
    editing: Record? = null, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
): Record {
    val at = minOf(occurredAt, now) // 지금보다 뒤는 안 됨
    val slot = plan?.let { insulinSlotAt(it, at, zone) }
    val planId = if (slot != null && plan != null) plan.id else editing?.let { json(it)?.optString("planId")?.takeIf(String::isNotEmpty) }
    val slotKey = if (slot != null) slot.key else editing?.slotKey
    val data = insulinData(planId, units, status, mealBefore)
    return if (editing != null) {
        val changed = if (at != editing.occurredAt) editing.withOccurredAt(at, zone, now) else editing.copy(updatedAt = now)
        changed.copy(data = data, slotKey = slotKey).also { dao.update(it) }
    } else {
        Record(createdAt = now, petId = petId, kind = RecordKind.insulin, occurredAt = at, day = OkcheDay.of(at, zone), slotKey = slotKey, data = data)
            .also { dao.insert(it) }
    }
}

private val mealWords = mapOf("all" to "밥 다 먹음", "some" to "밥 남김", "none" to "밥 안 먹음")

/** 값 글자 '2단위 맞음'·'1.5단위 일부만'·'건너뜀' + ' · 밥 안 먹음'(줄 이름 '인슐린'은 앞에서 붙음). 모르는 값이면 빈 글자. */
fun insulinValue(r: Record): String {
    val main = when (insulinStatus(r)) {
        "skipped" -> "건너뜀"
        "full" -> insulinUnits(r)?.let { "${medNumber(it)}단위 맞음" }
        "partial" -> insulinUnits(r)?.let { "${medNumber(it)}단위 일부만" }
        else -> null
    } ?: return ""
    return listOfNotNull(main, insulinMeal(r)?.let(mealWords::get)).joinToString(" · ")
}

/** 시트 고르기 값. */
val insulinStatusTitles = listOf("full" to "다 맞았어요", "partial" to "일부만 들어갔어요", "skipped" to "건너뛰었어요")
val insulinMealTitles = listOf("all" to "다 먹음", "some" to "남김", "none" to "안 먹음")

/** 인슐린 설정 화면 열기(아이 id) — 일기 '설정하기 ›'·시트 '인슐린 이름·양·시간 바꾸기 ›'. RootTabs가 준다. */
val LocalOpenInsulinSetup = androidx.compose.runtime.staticCompositionLocalOf<(String) -> Unit> { {} }
