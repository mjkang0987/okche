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

val quickRows = listOf(
    QuickRow(RecordKind.vomit, "contents", listOf("kibble" to "사료", "digested" to "불은 사료", "liquidFoam" to "거품")),
    QuickRow(RecordKind.stool, "shape", listOf("hard" to "단단", "normal" to "정상", "soft" to "무름")),
    QuickRow(RecordKind.urine, "amount", listOf("little" to "찔끔", "normal" to "보통", "much" to "많음")),
    QuickRow(RecordKind.meal, "result", listOf("all" to "다 먹음", "some" to "남김", "none" to "안 먹음")),
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

/** 값 글자(데이터 3장 값 → 화면 글자). 시트에서만 고르는 값(7-2)도 미리 둔다. */
private val valueTitles = mapOf(
    "contents" to mapOf("kibble" to "사료", "digested" to "불은 사료", "liquidFoam" to "거품", "hairball" to "헤어볼", "plant" to "풀", "foreign" to "이물질"),
    "shape" to mapOf("hard" to "단단", "normal" to "정상", "soft" to "무름", "loose" to "묽음", "watery" to "물 같음"),
    "amount" to mapOf("little" to "찔끔", "normal" to "보통", "much" to "많음"),
    "result" to mapOf("all" to "다 먹음", "some" to "남김", "none" to "안 먹음"),
)

/** 기록 줄 값 글자: 구토 '사료', 매화 '정상', 소피 '보통', 수라 '다 먹음'. 모르는 값이면 빈 글자. */
fun recordValue(record: Record): String {
    val row = quickRows.firstOrNull { it.kind == record.kind } ?: return ""
    val json = runCatching { JSONObject(record.data) }.getOrNull() ?: return ""
    val raw = json.optJSONArray(row.key)?.let { a -> List(a.length()) { a.optString(it) } } ?: json.optString(row.key).takeIf { it.isNotEmpty() }?.let(::listOf).orEmpty()
    return raw.mapNotNull { valueTitles[row.key]?.get(it) }.joinToString(" · ")
}

/** 바로 기록: 그때 시각·6시 기준 `day`로 바로 저장. `petId`가 없으면 누군지 모름. */
suspend fun saveQuickRecord(dao: OkcheDao, row: QuickRow, value: String, petId: String?, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Record {
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

/** 기록 지우기: `deletedAt`·`updatedAt`, '되돌릴 수 없어요'라서 `data` = `{}`(데이터 1-4). */
suspend fun deleteRecord(dao: OkcheDao, record: Record, now: Long = System.currentTimeMillis()) {
    dao.update(record.copy(deletedAt = now, updatedAt = now, data = "{}"))
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
