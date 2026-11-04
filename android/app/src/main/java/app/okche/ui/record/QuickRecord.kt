package app.okche.ui.record

import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

// 일기 바로 기록·방금 남긴 기록·기록 목록 규칙(plan.md 7-1, 데이터 3장). iOS `QuickRecord`와 같은 규칙.

/** 바로 기록 줄 하나: 종류, `data`의 칸 이름, (값, 버튼 글자). 구토는 `contents`가 여러 개라 배열로 저장. */
data class QuickRow(val kind: RecordKind, val key: String, val choices: List<Pair<String, String>>)

/** 수라 결과(값, 화면 글자) — 일기 수라 줄·수라 화면·결과 바꾸기가 같이 쓴다. */
val mealResults = listOf("all" to "다 먹음", "some" to "남김", "none" to "안 먹음")

val quickRows = listOf(
    QuickRow(RecordKind.vomit, "contents", listOf("kibble" to "사료", "digested" to "불은 사료", "liquidFoam" to "거품")),
    QuickRow(RecordKind.stool, "shape", listOf("hard" to "단단", "normal" to "정상", "soft" to "무름")),
    QuickRow(RecordKind.urine, "amount", listOf("little" to "찔끔", "normal" to "보통", "much" to "많음")),
    QuickRow(RecordKind.meal, "result", mealResults),
)

/** 종류 이름(화면 글자). */
fun kindName(kind: RecordKind) = when (kind) {
    RecordKind.vomit -> "구토"
    RecordKind.stool -> "매화"
    RecordKind.urine -> "소피"
    RecordKind.meal -> "수라"
    RecordKind.snack -> "다과"
    RecordKind.weight -> "옥체"
    RecordKind.med -> "탕약"
    RecordKind.glucose -> "혈당"
    RecordKind.insulin -> "인슐린"
}

private val mealTitles = mealResults.toMap()

/** 체중 글자: 그램 → kg 소수 둘째 자리(반올림, 0은 뺌 — iOS `kgText`와 같음). */
fun kgText(grams: Long): String = java.math.BigDecimal.valueOf(grams, 3).setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString() + "kg"

/** 기록 줄 값 글자: 구토·매화·소피는 자세히 시트 규칙([detailLine]), 수라는 '다 먹음', 체중은 kg. 모르는 값이면 빈 글자. */
fun recordValue(record: Record): String {
    detailLine(record)?.let { return it }
    if (record.kind == RecordKind.weight) {
        return runCatching { JSONObject(record.data) }.getOrNull()?.optLong("grams", -1)?.takeIf { it >= 0 }?.let(::kgText).orEmpty()
    }
    if (record.kind != RecordKind.meal) return ""
    val json = runCatching { JSONObject(record.data) }.getOrNull() ?: return ""
    return mealTitles[json.optString("result")].orEmpty()
}

/** 바로 기록: 그때 시각·6시 기준 `day`로 바로 저장. `petId`가 없으면 누군지 모름. */
suspend fun saveQuickRecord(dao: OkcheDao, row: QuickRow, value: String, petId: String?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record {
    // 수라는 끼니가 있으면 지금 끼니 차례로(그 차례에 이미 있으면 추가 식사 — 7-3).
    if (row.kind == RecordKind.meal) {
        val slot = quickMealSlot(dao, petId, now, zone)
        return Record(id = slot?.let { newMealRecordId(dao, it) } ?: app.okche.store.newId(), createdAt = now, petId = petId, kind = RecordKind.meal, occurredAt = now, day = OkcheDay.of(now, zone), slotKey = slot?.key,
            data = mealData(value, app.okche.ui.food.foodIdAt(dao, petId, OkcheDay.of(now, zone)), slot?.slot?.name, slot?.slot?.grams, null)).also { dao.insert(it) }
    }
    val data = JSONObject().put(row.key, if (row.kind == RecordKind.vomit) JSONArray(listOf(value)) else value).toString()
    val record = Record(createdAt = now, petId = petId, kind = row.kind, occurredAt = now, day = OkcheDay.of(now, zone), data = data)
    dao.insert(record)
    return record
}

/** 그날 요약: '구토 1번 · 매화 2번 · 소피 1번 · 수라 2번'(0번은 빼고, 없으면 null). */
fun daySummary(records: List<Record>): String? =
    quickRows.mapNotNull { row -> records.count { it.kind == row.kind }.takeIf { it > 0 }?.let { "${kindName(row.kind)} ${it}번" } }
        .takeIf { it.isNotEmpty() }?.joinToString(" · ")

/** 누구였는지 정하기. */
suspend fun assignRecord(dao: OkcheDao, record: Record, petId: String?, now: Long = System.currentTimeMillis()) {
    if (record.petId != petId) dao.update(record.copy(petId = petId, updatedAt = now))
}

/** 기록 지우기: `deletedAt`·`updatedAt`, '되돌릴 수 없어요'라서 선택 칸을 비움 — `data` = `{}`, `slotKey`(데이터 1-4). */
suspend fun deleteRecord(dao: OkcheDao, record: Record, now: Long = System.currentTimeMillis()) {
    dao.update(record.copy(deletedAt = now, updatedAt = now, data = "{}", slotKey = null))
}

/** `YYYY-MM-DD`를 하루 옮긴 날. */
fun shiftDay(day: String, days: Long): String = LocalDate.parse(day).plusDays(days).toString()

/** 날짜 줄 글자: 오늘이면 '오늘 · 10월 25일 (일)', 아니면 '10월 24일 (토)'. */
fun dayTitle(day: String, today: String): String {
    val date = LocalDate.parse(day)
    val weekday = listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
    val text = "${date.monthValue}월 ${date.dayOfMonth}일 ($weekday)"
    return if (day == today) "오늘 · $text" else text
}

/** 기록의 시각 'HH:mm'(기기 시간대). */
fun recordTime(record: Record, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(record.occurredAt).atZone(zone)
    return "%02d:%02d".format(t.hour, t.minute)
}
