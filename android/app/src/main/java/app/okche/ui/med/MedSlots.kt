package app.okche.ui.med

import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDao
import app.okche.store.OkcheDay
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.record.shiftDay
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId

// 탕약 차례·오늘 먹일 약·차례 기록(plan.md 8-3a, 데이터 5장 '바꾼 주기'·'짝 없는 기록', 6-1·6-2). iOS `MedSlots.swift`와 같은 규칙.

/** 차례 하나: 그 줄, `slotKey`, 먹일 시각, 차례의 날(6시 기준). */
data class MedSlot(val schedule: MedSchedule, val key: String, val due: Long, val day: String)

private val handledStatuses = setOf("given", "skipped", "vomited")

private fun json(record: Record) = runCatching { JSONObject(record.data) }.getOrDefault(JSONObject())

/** 바로 앞 줄이 있고 `activeFrom`이 그날인 `times` 줄 — 그날은 `activeFrom` 거르기 없이 넘김 규칙(5장). */
private fun isChangeDay(row: MedSchedule, day: String, series: List<MedSchedule>, zone: ZoneId) =
    row.frequency != MedFrequency.everyNHours && row.frequency != MedFrequency.asNeeded && OkcheDay.of(row.activeFrom, zone) == day &&
        series.any { it.activeUntil == row.activeFrom && it.id != row.id }

/** `times` 줄의 그날 차례(6-1). [fromFilter]가 거짓이면 `activeFrom` 앞 시간도. */
private fun timesSlots(row: MedSchedule, day: String, fromFilter: Boolean, zone: ZoneId): List<MedSlot> {
    if (!isDoseDay(row, day)) return emptyList()
    return row.times.distinct().mapNotNull { time ->
        val due = occurrence(day, time, zone) ?: return@mapNotNull null
        if (fromFilter && due < row.activeFrom) return@mapNotNull null
        if (row.activeUntil?.let { due >= it } == true) return@mapNotNull null
        MedSlot(row, "${row.id}|$day|$time", due, day)
    }.sortedBy { it.due }
}

/** 몇 시간마다 줄의 차례를 [through] 날까지(6-1). 늦게 줬을 때 밀리면 앞 차례의 가장 이른 준·토한 기록 + 간격. */
private fun hoursSlots(row: MedSchedule, given: Map<String, Long>, through: String, zone: ZoneId): List<MedSlot> {
    val first = row.firstAt ?: return emptyList()
    val h = row.everyHours?.takeIf { it > 0 } ?: return emptyList()
    if (row.frequency != MedFrequency.everyNHours) return emptyList()
    val step = h * 3_600_000L
    val slots = mutableListOf<MedSlot>()
    var due = first
    for (n in 0 until 100_000) {
        val day = OkcheDay.of(due, zone)
        if (day > through) break
        if (row.activeUntil?.let { due >= it } == true) break
        val key = "${row.id}|#$n"
        // `firstAt`은 보호자가 정한 0번 차례 — 분 올림 때문에 저장한 순간(`activeFrom`)보다 조금 앞이어도 남긴다.
        if (due >= minOf(row.activeFrom, first)) slots += MedSlot(row, key, due, day)
        due = if (row.shiftOnLate) (given[key] ?: due) + step else first + (n + 1) * step
    }
    return slots
}

/** 그날 차례 하나와 그 차례를 처리한 기록(없으면 남은 차례). 짝 없는 기록이 처리한 차례면 그 기록. */
data class MedSlotEntry(val slot: MedSlot, val record: Record?)

/** 한 아이의 그날 남은 차례(6-2): 처리한 차례·넘김·버림을 빼고 먹일 시각 순. 시간이 지나도 남는다. */
fun remainingSlots(schedules: List<MedSchedule>, records: List<Record>, petId: String, day: String, zone: ZoneId = ZoneId.systemDefault()): List<MedSlot> =
    todaySlots(schedules, records, petId, day, zone).filter { it.record == null }.map { it.slot }

/**
 * 한 아이의 그날 모든 차례(이미 기록한 것 포함, 넘김·버림 빼고) — 오늘 먹일 약 시트(8-3b).
 * [schedules]는 그 아이의 닫힌 줄까지 모두(바꾼 날 규칙에 앞 줄이 필요), [records]는 `med` 기록(지운 것은 거름).
 */
fun todaySlots(schedules: List<MedSchedule>, records: List<Record>, petId: String, day: String, zone: ZoneId = ZoneId.systemDefault()): List<MedSlotEntry> {
    val recs = records.filter { it.deletedAt == null && it.kind == RecordKind.med && it.petId == petId && it.slotKey != null }
    val keyed = recs.groupBy { it.slotKey!! }
    val handled = keyed.filterValues { list -> list.any { json(it).optString("status") in handledStatuses } }.keys
    // 늦게 줬을 때 밀리는 몇 시간마다: 차례마다 가장 이른 준·토한 시각.
    val given = keyed.mapValues { (_, list) -> list.filter { json(it).optString("status") in setOf("given", "vomited") }.minOfOrNull { it.occurredAt } }
        .filterValues { it != null }.mapValues { it.value!! }
    val result = mutableListOf<MedSlotEntry>()
    // 필요할 때만 줄도 묶음에 넣는다(차례는 없지만 '필요할 때만 → 매일'로 바꾼 날의 바로 앞 줄이 됨).
    for (series in schedules.filter { it.petId == petId }.groupBy { it.seriesId }.values) {
        // 열린 줄이 둘 이상이면(백업 병합) `activeFrom`, 같으면 `updatedAt`이 늦은 것 하나만(5장).
        val keep = series.filter { it.activeUntil == null }.maxWithOrNull(compareBy({ it.activeFrom }, { it.updatedAt }))
        val rows = series.filter { it.activeUntil != null || it === keep }.sortedBy { it.activeFrom }
        // 짝 없는 기록은 그 주기의 모든 줄로 따진다 — 병합으로 빠진 열린 줄의 기록은 짝 없음(차례가 없으니).
        val rowById = series.associateBy { it.id }

        fun slots(row: MedSchedule, d: String) =
            if (row.frequency == MedFrequency.everyNHours) hoursSlots(row, given, d, zone).filter { it.day == d }
            else timesSlots(row, d, !isChangeDay(row, d, rows, zone), zone)

        /** 짝 없는 기록: 이 주기 줄의 키인데 그 줄의 차례가 아닌 그날 기록. */
        fun isUnmatched(record: Record): Boolean {
            val key = record.slotKey ?: return false
            val parts = key.split("|")
            val row = rowById[parts.first()] ?: return false
            if (rows.none { it === row }) return true
            if (row.frequency == MedFrequency.everyNHours) {
                val through = shiftDay(maxOf(record.day, day), 1)
                return hoursSlots(row, given, through, zone).none { it.key == key }
            }
            if (parts.size != 3) return true
            return slots(row, parts[1]).none { it.key == key }
        }
        val unmatched = recs.filter { it.day == day && isUnmatched(it) }

        val candidates: List<MedSlot>
        var spent = emptySet<String>() // k에 이미 센 짝 없는 기록
        val last = rows.indexOfLast { isChangeDay(it, day, rows, zone) }
        if (last >= 0) {
            // 바꾼 날: 앞 줄들의 그날 차례 중 기록 있는 것 + 앞 줄들의 짝 없는 기록 = k, 앞 줄 남은 차례는 버림, 새 줄 앞 k개는 넘김.
            val earlier = rows.subList(0, last)
            val earlierUnmatched = unmatched.filter { r -> earlier.any { r.slotKey?.startsWith(it.id + "|") == true } }
            spent = earlierUnmatched.map { it.id }.toSet()
            val k = earlier.flatMap { slots(it, day) }.count { it.key in handled } + earlierUnmatched.size
            candidates = slots(rows[last], day).drop(k) + rows.drop(last + 1).flatMap { slots(it, day) }
        } else {
            candidates = rows.flatMap { slots(it, day) }
        }
        val entries = candidates.sortedBy { it.due }.map { slot ->
            MedSlotEntry(slot, if (slot.key in handled) keyed[slot.key]?.filter { json(it).optString("status") in handledStatuses }?.minByOrNull { it.occurredAt } else null)
        }.toMutableList()
        // 짝 없는 기록은 남은 차례를 앞에서부터 하나씩 처리한 것으로.
        val extras = ArrayDeque(unmatched.filter { it.id !in spent }.sortedBy { it.occurredAt })
        for (i in entries.indices) {
            if (entries[i].record != null) continue
            val r = extras.removeFirstOrNull() ?: break
            entries[i] = MedSlotEntry(entries[i].slot, r)
        }
        result += entries
    }
    return result.sortedWith(compareBy({ it.slot.due }, { it.slot.schedule.createdAt }, { it.slot.schedule.id }))
}

/** 일기 탕약 줄 버튼: 같은 약은 이른 차례 하나만, 최대 3개(8-3 정한 것 ②). */
fun medButtons(slots: List<MedSlot>): List<MedSlot> = slots.distinctBy { it.schedule.medicationId }.take(3)

/** 탕약 줄이 보이는지: 그 아이에게 열린 약 주기가 하나라도(필요할 때만 약만 있어도). */
fun hasOpenSchedule(schedules: List<MedSchedule>, petId: String) = schedules.any { it.petId == petId && it.activeUntil == null }

/** 버튼 글자: 짧은 이름, 없으면 이름. */
fun medShortName(med: Medication?): String = med?.let { m -> m.shortName?.trim()?.takeIf { it.isNotEmpty() } ?: m.name }.orEmpty()

/** '오후 9시'·'오전 8시 30분'(화면 읽기). */
fun spokenTime(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val t = Instant.ofEpochMilli(epochMs).atZone(zone)
    return "${if (t.hour < 12) "오전" else "오후"} ${if (t.hour % 12 == 0) 12 else t.hour % 12}시" + if (t.minute > 0) " ${t.minute}분" else ""
}

/** 차례 기록(바로 기록·'줬어요'): `occurredAt` = 지금, `day` = 그 차례의 날, 양·단위는 그 줄. */
suspend fun recordSlot(dao: OkcheDao, slot: MedSlot, status: String = "given", now: Long = System.currentTimeMillis()): Record =
    recordSlots(dao, listOf(slot to status), now).single()

/** 여러 차례를 한 번에(오늘 먹일 약 시트 '기록하기') — 하나라도 안 되면 아무것도 안 남김. */
suspend fun recordSlots(dao: OkcheDao, picks: List<Pair<MedSlot, String>>, now: Long = System.currentTimeMillis()): List<Record> {
    val records = picks.map { (slot, status) ->
        val s = slot.schedule
        Record(createdAt = now, petId = s.petId, kind = RecordKind.med, occurredAt = now, day = slot.day, slotKey = slot.key,
            data = medData(s.amount, s.medicationId, s.id, status, s.unit))
    }
    dao.insertRecords(records)
    return records
}

/** `med` 기록 내용 — 칸 이름 순(iOS `.sortedKeys`와 같게). 예정에 없던 약은 `scheduleId` 없음. */
private fun medData(amount: Double, medicationId: String, scheduleId: String?, status: String, unit: app.okche.store.DoseUnit) =
    JSONObject().put("amount", amount).put("medicationId", medicationId).apply { if (scheduleId != null) put("scheduleId", scheduleId) }
        .put("status", status).put("unit", unit.name).toString()

/** 예정에 없던 약(`scheduleId`·`slotKey` 없음) 새로 남기기, 또는 탕약 기록 하나 고치기(약은 그대로 — 차례 기록은 차례의 날도 그대로). */
suspend fun saveExtraMed(
    dao: OkcheDao, petId: String, medication: Medication, amount: Double, status: String, occurredAt: Long, editing: Record? = null,
    now: Long = System.currentTimeMillis(),
): Record {
    if (editing != null) {
        val j = json(editing)
        // 칸 이름 순을 지키게 새로 만든다.
        val data = medData(amount, j.optString("medicationId"), j.optString("scheduleId").takeIf { it.isNotEmpty() }, status,
            runCatching { app.okche.store.DoseUnit.valueOf(j.getString("unit")) }.getOrDefault(medication.unit))
        val moved = when {
            editing.occurredAt == occurredAt -> editing
            editing.slotKey == null -> editing.withOccurredAt(occurredAt, now = now)
            else -> editing.copy(occurredAt = occurredAt)
        }
        return moved.copy(data = data, updatedAt = now).also { dao.update(it) }
    }
    return Record(createdAt = now, petId = petId, kind = RecordKind.med, occurredAt = occurredAt,
        data = medData(amount, medication.id, null, status, medication.unit)).also { dao.insert(it) }
}

/** 예정에 없던 약 고르기 순서(8-3): 이 아이의 필요할 때만 약 → 이 아이가 먹는 약 → 나머지(약방 순), 줄 아래 '물약 · 필요할 때만'. */
fun extraMedOptions(meds: List<Medication>, schedules: List<MedSchedule>, pet: app.okche.store.Pet): List<Pair<Medication, String>> {
    val open = schedules.filter { it.petId == pet.id && it.activeUntil == null && it.deletedAt == null }
    fun rank(m: Medication): Int {
        val mine = open.filter { it.medicationId == m.id }
        return if (mine.any { it.frequency == MedFrequency.asNeeded }) 0 else if (mine.isEmpty()) 2 else 1
    }
    return meds.filter { it.deletedAt == null }.withIndex().sortedWith(compareBy({ rank(it.value) }, { it.index })).map { (_, m) ->
        val tag = when (rank(m)) { 0 -> "필요할 때만"; 1 -> "${withSubjectParticle(pet.name)} 먹는 약"; else -> null }
        m to listOfNotNull(medFormTitle(m.form), tag).joinToString(" · ")
    }
}

/** 시트 약 줄 아래 글자 '가루 · 1포 · 사료에 섞어서'. */
fun slotDetail(s: MedSchedule, med: Medication?) = listOfNotNull(med?.let { medFormTitle(it.form) }, amountText(s.amount, s.unit), howToText(s.howTo)).joinToString(" · ")

/** 'HH:mm'(기기 시간대). */
fun hhmm(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String = Instant.ofEpochMilli(epochMs).atZone(zone).let { "%02d:%02d".format(it.hour, it.minute) }

/** '오전 8:00'. */
fun clockText(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()) = clock(hhmm(epochMs, zone))

/** 기록 내용 JSON(시트에서 상태 읽기). */
fun medJson(record: Record) = json(record)

/** 기록 값 글자 '프레드니솔론 1알'·'프레드니솔론 건너뜀'·'프레드니솔론 먹고 토함'(약방에서 뺀 약도 이름은 남음). */
fun medValue(record: Record, meds: List<Medication>): String {
    val j = json(record)
    val name = meds.firstOrNull { it.id == j.optString("medicationId") }?.name ?: "약"
    return when (j.optString("status")) {
        "skipped" -> "$name 건너뜀"
        "vomited" -> "$name 먹고 토함"
        else -> {
            val unit = runCatching { app.okche.store.DoseUnit.valueOf(j.getString("unit")) }.getOrNull()
            if (!j.has("amount") || unit == null) name else "$name ${amountText(j.getDouble("amount"), unit)}"
        }
    }
}
