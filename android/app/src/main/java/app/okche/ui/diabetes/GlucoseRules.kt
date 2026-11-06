package app.okche.ui.diabetes

import app.okche.store.InsulinPlan
import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

// 혈당 규칙(plan.md 9-1, 데이터 3장·7장). iOS `GlucoseRules`와 같은 규칙.

@Suppress("EnumEntryName")
enum class GlucoseTiming { beforeInsulin, afterInsulin, other }

@Suppress("EnumEntryName")
enum class GlucoseSign { weak, wobbly, seizure }

val glucoseTimingTitles = listOf(GlucoseTiming.beforeInsulin to "인슐린 맞기 전", GlucoseTiming.afterInsulin to "맞고 난 뒤", GlucoseTiming.other to "그 밖")
val glucoseSignTitles = listOf(GlucoseSign.weak to "기운이 없고 축 늘어져요", GlucoseSign.wobbly to "비틀거리거나 떨어요", GlucoseSign.seizure to "경련을 해요")

/** 값 글자의 짧은 말. */
private val timingWords = mapOf(GlucoseTiming.beforeInsulin to "맞기 전", GlucoseTiming.afterInsulin to "맞고 난 뒤", GlucoseTiming.other to "그 밖")
private val signWords = mapOf(GlucoseSign.weak to "기운 없음", GlucoseSign.wobbly to "비틀거림", GlucoseSign.seizure to "경련")

private val digits = Regex("[0-9]+")

/** 앞뒤 빈칸 뺀 숫자만 1~999(6-1 숫자 규칙). */
fun glucoseNumber(text: String): Int? = text.trim().takeIf { digits.matches(it) }?.toIntOrNull()?.takeIf { it in 1..999 }

/** 빈칸이면 안내 없음(버튼만 못 누름), 틀리면 '1에서 999 사이로 넣어 주세요'. */
fun glucoseError(text: String): String? = if (text.isBlank() || glucoseNumber(text) != null) null else "1에서 999 사이로 넣어 주세요"

/** 쓰는 인슐린 계획(7장): 열린 줄 중 늦은 `activeFrom`, 같으면 늦은 `updatedAt`. */
fun openInsulinPlan(plans: List<InsulinPlan>, petId: String): InsulinPlan? =
    plans.filter { it.petId == petId && it.deletedAt == null && it.activeUntil == null }
        .maxWithOrNull(compareBy<InsulinPlan> { it.activeFrom }.thenBy { it.updatedAt })

/** 지금 차례 구간(7장): 각 차례는 자기 시간 1시간 전부터 다음 차례 1시간 전까지(날을 넘김). 지금이 든 [시작, 끝). 차례가 없으면 null. */
fun insulinWindow(plan: InsulinPlan, now: Long, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long>? {
    val times = plan.slots.mapNotNull { runCatching { LocalTime.parse(it.time) }.getOrNull() }.distinct()
    if (times.isEmpty()) return null
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    // 어제~모레의 구간 시작(차례 − 1시간)을 이어 놓고 지금이 든 칸을 찾는다(자정 가까운 차례는 시작이 전날로 넘어가서 모레까지).
    val starts = (-1L..2L).flatMap { offset -> times.map { today.plusDays(offset).atTime(it).atZone(zone).minusHours(1).toInstant().toEpochMilli() } }.sorted()
    return starts.zipWithNext().firstOrNull { (a, b) -> now in a until b }
}

/** 남기기 짐작: 열린 계획이 없으면 그 밖, 지금 구간에 다 맞음·일부만 기록이 있으면 맞고 난 뒤, 아니면 null(묻기 — 건너뜀만 있어도). */
fun glucoseGuess(petId: String, plans: List<InsulinPlan>, insulinRecords: List<Record>, now: Long, zone: ZoneId = ZoneId.systemDefault()): GlucoseTiming? {
    val plan = openInsulinPlan(plans, petId) ?: return GlucoseTiming.other
    val (start, end) = insulinWindow(plan, now, zone) ?: return null
    val given = insulinRecords.any { r ->
        r.petId == petId && r.deletedAt == null && r.kind == RecordKind.insulin && r.occurredAt in start until end &&
            runCatching { JSONObject(r.data).optString("status") }.getOrNull() in setOf("full", "partial")
    }
    return if (given) GlucoseTiming.afterInsulin else null
}

/** `glucose` 기록 내용 — 칸 이름 순(iOS `.sortedKeys`와 같게), 증상은 늘 배열(없으면 빈 배열). */
fun glucoseData(mgdl: Int, timing: GlucoseTiming, signs: Set<GlucoseSign>): String =
    JSONObject().put("mgdl", mgdl).put("signs", JSONArray(GlucoseSign.entries.filter { it in signs }.map { it.name })).put("timing", timing.name).toString()

/** 새 기록이면 넣고, 고치기면 그 기록의 `data`(시간을 바꿨으면 `occurredAt`·`day`도)·`updatedAt`만 바꾼다. */
suspend fun saveGlucose(
    dao: OkcheDao, petId: String?, mgdl: Int, timing: GlucoseTiming, signs: Set<GlucoseSign>, occurredAt: Long, editing: Record? = null,
    now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
): Record {
    val at = minOf(occurredAt, now) // 지금보다 뒤는 안 됨
    val data = glucoseData(mgdl, timing, signs)
    return if (editing != null) {
        val changed = if (at != editing.occurredAt) editing.withOccurredAt(at, zone, now) else editing.copy(updatedAt = now)
        changed.copy(data = data).also { dao.update(it) }
    } else {
        Record(createdAt = now, petId = petId, kind = RecordKind.glucose, occurredAt = at, day = OkcheDay.of(at, zone), data = data).also { dao.insert(it) }
    }
}

private fun json(r: Record) = runCatching { JSONObject(r.data) }.getOrNull()

fun glucoseMgdl(r: Record): Int? = json(r)?.takeIf { it.has("mgdl") }?.optInt("mgdl", -1)?.takeIf { it > 0 }
fun glucoseTiming(r: Record): GlucoseTiming? = json(r)?.optString("timing")?.let { t -> GlucoseTiming.entries.firstOrNull { it.name == t } }
fun glucoseSigns(r: Record): Set<GlucoseSign> {
    val a = json(r)?.optJSONArray("signs") ?: return emptySet()
    return List(a.length()) { a.optString(it) }.mapNotNull { s -> GlucoseSign.entries.firstOrNull { it.name == s } }.toSet()
}

/** 값 글자 '186 · 맞고 난 뒤 · 비틀거림'(줄 이름 '혈당'은 앞에서 붙음). 수치가 없으면 빈 글자. */
fun glucoseValue(r: Record): String {
    val n = glucoseMgdl(r) ?: return ""
    val signs = glucoseSigns(r)
    return (listOf("$n") + listOfNotNull(glucoseTiming(r)?.let(timingWords::get)) + GlucoseSign.entries.filter { it in signs }.mapNotNull(signWords::get))
        .joinToString(" · ")
}
