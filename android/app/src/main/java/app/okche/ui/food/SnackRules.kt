package app.okche.ui.food

import app.okche.store.FoodChange
import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.Snack
import app.okche.store.SnackKind
import app.okche.ui.record.shiftDay
import org.json.JSONObject
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

// 다과 규칙(plan.md 7-4b, 데이터 8장·7장 `snack`). iOS `SnackRules.swift`와 같은 규칙.

val snackKinds = listOf(
    SnackKind.lickable to "짜 먹는 간식", SnackKind.freezeDried to "동결건조", SnackKind.jerky to "져키·스틱", SnackKind.wet to "캔·파우치",
    SnackKind.prescription to "처방 간식", SnackKind.human to "사람 음식",
)

fun snackKindTitle(kind: SnackKind) = snackKinds.first { it.first == kind }.second

private fun json(record: Record) = runCatching { JSONObject(record.data) }.getOrDefault(JSONObject())

/** 다과 기록의 다과 ID(소문자 — 두 플랫폼 같은 글자). */
fun snackIdOf(record: Record): String? = json(record).optString("snackId").takeIf { it.isNotEmpty() }

fun snackAmountOf(record: Record): String? = json(record).optString("amount").takeIf { it.isNotEmpty() }

/** 다과 기록 `data`: `snackId` + `amount`(앞뒤 빈칸 뺀 글자, 비면 없음). 칸 이름 순(iOS sortedKeys와 같은 글자). */
fun snackData(snackId: String, amount: String): String {
    val json = JSONObject()
    amount.trim().takeIf { it.isNotEmpty() }?.let { json.put("amount", it) }
    json.put("snackId", snackId)
    return json.toString()
}

/** 기록 값 글자 '연어 퓨레 · 반 개'(목록에서 뺀 다과도 이름은 남음 — 1-4). */
fun snackValue(record: Record, snacks: List<Snack>): String =
    listOfNotNull(snackIdOf(record)?.let { id -> snacks.firstOrNull { it.id == id }?.name }, snackAmountOf(record)).joinToString(" · ")

/** 다과 목록: 목록에서 빼지 않은 다과, 들인 순. */
fun listedSnacks(snacks: List<Snack>) = snacks.filter { it.deletedAt == null }.sortedBy { it.createdAt }

/** 다과 목록 줄 아래 글자: '짜 먹는 간식 · 이번 달 6번 · 수라 바꾸기 중에도 괜찮아요'(0번이면 횟수 뺌). 이번 달 = 기기 달력 달, 지금 보는 아이. */
fun snackListDetail(snack: Snack, petId: String?, records: List<Record>, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): String {
    val month = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
    val count = if (petId == null) 0 else records.count {
        it.kind == RecordKind.snack && it.deletedAt == null && it.petId == petId && snackIdOf(it) == snack.id &&
            YearMonth.from(Instant.ofEpochMilli(it.occurredAt).atZone(zone)) == month
    }
    return listOfNotNull(snackKindTitle(snack.kind), if (count > 0) "이번 달 ${count}번" else null, if (snack.okDuringFoodChange) "수라 바꾸기 중에도 괜찮아요" else null)
        .joinToString(" · ")
}

/** 다과 기록 시트의 고르기 순서: 이 아이에게 최근 준 다과 먼저, 나머지는 들인 순. (다과, 마지막 준 날 `day`) */
fun snackPickOrder(snacks: List<Snack>, petId: String, records: List<Record>): List<Pair<Snack, String?>> {
    val last = records.filter { it.kind == RecordKind.snack && it.deletedAt == null && it.petId == petId }
        .groupBy { snackIdOf(it) }.mapValues { (_, list) -> list.maxBy { it.occurredAt } }
    val listed = listedSnacks(snacks).map { it to last[it.id] }
    val given = listed.filter { it.second != null }.sortedByDescending { it.second!!.occurredAt }
    return (given + listed.filter { it.second == null }).map { (snack, record) -> snack to record?.day }
}

/** 마지막 준 날 글자: '오늘'·'어제'·'10월 20일'. */
fun snackLastGiven(day: String, today: String): String = when (day) {
    today -> "오늘"
    shiftDay(today, -1) -> "어제"
    else -> day.split("-").mapNotNull { it.toIntOrNull() }.takeIf { it.size == 3 }?.let { "${it[1]}월 ${it[2]}일" } ?: day
}

/** 수라 바꾸기 기간(데이터 8장): 지금 먹는 사료 바꾸기의 `startedOn`부터 `finishedOn` 전날까지, 없으면 14일째까지. */
fun inFoodChange(petId: String, day: String, changes: List<FoodChange>): Boolean {
    val change = foodChangeAt(day, petId, changes) ?: return false
    val number = foodDayNumber(change.startedOn, day) ?: return false
    return change.finishedOn?.let { day < it } ?: (number <= 14)
}

/** 다과 들이기(이름 앞뒤 빈칸 뺌). */
suspend fun addSnack(dao: OkcheDao, name: String, kind: SnackKind, okDuringFoodChange: Boolean, now: Long = System.currentTimeMillis()) =
    Snack(createdAt = now, name = name.trim(), kind = kind, okDuringFoodChange = okDuringFoodChange).also { dao.insert(it) }

suspend fun updateSnack(dao: OkcheDao, snack: Snack, name: String, kind: SnackKind, okDuringFoodChange: Boolean, now: Long = System.currentTimeMillis()) =
    dao.update(snack.copy(name = name.trim(), kind = kind, okDuringFoodChange = okDuringFoodChange, updatedAt = now))

/** 목록에서 빼기: `deletedAt`만(이름은 남아 지난 기록 글자에 쓰임 — 1-4). */
suspend fun removeSnack(dao: OkcheDao, snack: Snack, now: Long = System.currentTimeMillis()) =
    dao.update(snack.copy(deletedAt = now, updatedAt = now))

/** 다과 기록하기: 새 기록이면 넣고, 고치기면 `data`·(시간을 바꿨으면)`occurredAt`·`day`·`updatedAt`. */
suspend fun recordSnack(dao: OkcheDao, snackId: String, amount: String, occurredAt: Long, editing: Record?, petId: String,
                        now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record {
    val at = minOf(occurredAt, now) // 지금보다 뒤는 안 됨
    val data = snackData(snackId, amount)
    if (editing == null) {
        return Record(createdAt = now, petId = petId, kind = RecordKind.snack, occurredAt = at, day = OkcheDay.of(at, zone), data = data).also { dao.insert(it) }
    }
    val updated = if (at != editing.occurredAt) editing.withOccurredAt(at, zone, now).copy(data = data) else editing.copy(data = data, updatedAt = now)
    dao.update(updated)
    return updated
}
