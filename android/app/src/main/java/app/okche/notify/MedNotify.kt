package app.okche.notify

import app.okche.store.InsulinPlan
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RestPeriod
import app.okche.ui.diabetes.insulinHandled
import app.okche.ui.diabetes.insulinSlotAt
import app.okche.ui.diabetes.insulinWindow
import app.okche.ui.diabetes.openInsulinPlan
import app.okche.ui.med.amountText
import app.okche.ui.med.medNumber
import app.okche.ui.med.clockText
import app.okche.ui.med.daysLeft
import app.okche.ui.med.withSubjectParticle
import app.okche.store.QuietHours
import app.okche.ui.med.remainingSlots
import app.okche.ui.record.shiftDay
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// 탕약·인슐린 알림 목록 계산(plan.md 8-4a·9-3 계산 규칙, 데이터 5장·7장 `leadMinutes`·`remindAgainMinutes`). 휴대폰에 거는 일은 8-4b.

enum class MedAlertKind {
    due, again, more, low, insulinDue, insulinAgain;

    /** 인슐린 알림 — 누르면 일기 탭에 그 아이(9-3). */
    val isInsulin get() = this == insulinDue || this == insulinAgain

    /** 같은 시각이면 탕약 → 인슐린 → 다시 알림 순(iOS와 같게, 63개 경계에서 빠지는 것도 같게). */
    val order get() = when (this) {
        due -> 0
        insulinDue -> 1
        again -> 2
        else -> 3
    }
}

data class MedAlert(val id: String, val kind: MedAlertKind, val fireAt: Long, val title: String, val body: String, val petId: String?, val medId: String? = null)

private const val MINUTE = 60_000L
private const val HOUR = 3_600_000L
private const val WINDOW = 72 * 3_600_000L
internal const val LIMIT = 63

/**
 * 오늘(6시 기준)부터 3일 뒤까지 남은 차례의 알림. 같은 시각·같은 종류는 하나로, 이른 것부터 [limit]개(처음 값 63) + 이어짐 알림. 밤 알림 쉬기와 상관없음.
 * [extra](인슐린 알림 — [insulinAlerts])도 함께 이른 것부터 자른다(9-3, 합쳐 63개). 탕약 알림을 꺼도 [extra]는 그대로.
 */
fun medAlerts(
    schedules: List<MedSchedule>, medRecords: List<Record>, pets: List<Pet>, rests: List<RestPeriod>, meds: List<Medication>,
    medTimeOn: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault(), limit: Int = LIMIT, extra: List<MedAlert> = emptyList(),
): List<MedAlert> {
    val pets = if (medTimeOn) pets else emptyList()
    val today = OkcheDay.of(now, zone)
    // 넷째 날 새벽 차례도 몇 분 전이면 72시간 안에 울릴 수 있어 하루 더 본다(창 밖은 아래에서 거름).
    val days = (0L..4L).map { shiftDay(today, it) }
    val live = schedules.filter { it.deletedAt == null }
    val medById = meds.filter { it.deletedAt == null }.associateBy { it.id }

    class Entry(val pet: Pet, val med: Medication, val schedule: MedSchedule, val due: Long)
    val raw = mutableListOf<Triple<MedAlertKind, Long, Entry>>()
    for (pet in pets.filter { it.deletedAt == null && it.memorializedAt == null }) {
        val petRests = rests.filter { it.petId == pet.id && it.deletedAt == null }
        val slots = days.flatMap { remainingSlots(live, medRecords, pet.id, it, zone) }.distinctBy { it.key }
        for (slot in slots) {
            val s = slot.schedule
            val med = medById[s.medicationId] ?: continue
            if (!s.reminder || s.frequency == MedFrequency.asNeeded) continue
            if (petRests.any { it.startedAt <= slot.due && (it.endedAt == null || slot.due < it.endedAt) }) continue
            val e = Entry(pet, med, s, slot.due)
            // 울릴 시각은 분 단위로 내림 — 몇 시간마다 '준 시각부터'는 초가 붙어 같은 분이 따로 묶이고 id가 겹칠 수 있음.
            raw += Triple(MedAlertKind.due, slot.due - s.leadMinutes * MINUTE, e)
            if (s.remindAgainMinutes > 0) raw += Triple(MedAlertKind.again, slot.due + s.remindAgainMinutes * MINUTE, e)
        }
    }

    // 창은 내리기 전 시각으로 견줌(같은 분 안에 다시 걸어도 빠지지 않게).
    val alerts = raw.filter { now < it.second && it.second <= now + WINDOW }.map { Triple(it.first, minute(it.second), it.third) }
        .groupBy({ it.first to it.second }, { it.third })
        .map { (k, list) ->
            val (kind, fireAt) = k
            val entries = list.sortedWith(compareBy({ it.pet.createdAt }, { it.med.name }))
            val texts = entries.map {
                if (kind == MedAlertKind.due) "${it.pet.name} ${it.med.name} ${amountText(it.schedule.amount, it.schedule.unit)}" else "${it.pet.name} ${it.med.name}"
            }
            val body = if (texts.size >= 4) texts.take(3).joinToString(" · ") + " 외 ${texts.size - 3}" else texts.joinToString(" · ")
            val title = if (kind == MedAlertKind.due) "탕약 · " + clockText(entries.minOf { it.due }, zone) else "아직 안 준 약이 있어요"
            MedAlert("med.${kind.name}.${fireAt / 1000}", kind, fireAt, title, body, entries.first().pet.id)
        }
        .plus(extra)
        .sortedWith(compareBy({ it.fireAt }, { it.kind.order }))
        .take(limit.coerceAtLeast(0))
    val last = alerts.lastOrNull() ?: return alerts
    return alerts + MedAlert("med.more", MedAlertKind.more, last.fireAt + MINUTE, "옥체", "옥체를 열면 알림이 이어져요", null)
}

/**
 * 인슐린 알림(plan.md 9-3, 데이터 7장): 아이마다 쓰는 계획(열린 줄)의 앞으로 차례에 '차례 − `leadMinutes`', 다시 알림이 있으면 '차례 + `remindAgainMinutes`'.
 * 차례 구간에 그 아이의 인슐린 기록(건너뜀 포함)이 있으면 둘 다 빠짐(미리 맞힌 경우). 휴양 중·추억·지운 아이 빠짐,
 * 밤 알림 쉬기 시간이면 계획의 `ringDuringQuiet`일 때만. 같은 시각·종류는 하나로. iOS `MedNotify.insulinAlerts`와 같음.
 */
fun insulinAlerts(
    plans: List<InsulinPlan>, insulinRecords: List<Record>, pets: List<Pet>, rests: List<RestPeriod>,
    insulinTimeOn: Boolean, quiet: QuietHours?, now: Long, zone: ZoneId = ZoneId.systemDefault(),
): List<MedAlert> {
    if (!insulinTimeOn) return emptyList()
    val end = now + WINDOW
    class Entry(val kind: MedAlertKind, val fireAt: Long, val due: Long, val pet: Pet, val units: Double)
    val entries = mutableListOf<Entry>()
    for (pet in pets.filter { it.deletedAt == null && it.memorializedAt == null }) {
        val plan = openInsulinPlan(plans, pet.id) ?: continue
        val petRests = rests.filter { it.petId == pet.id && it.deletedAt == null }
        // 다시 알림(최대 1시간 뒤)이 아직 남은 지난 차례부터 구간을 하나씩 넘김 — 차례 − 1시간이 구간 시작.
            // shortcut: 맞혔는지는 그 차례 구간 안 기록으로만 본다 — 두 차례가 다시 알림 분보다 가까우면(08:00·08:30에 1시간 뒤) 다시 알림이 어긋날 수 있음, 그런 계획이 생기면 다시 알림은 차례 시각 이후 기록으로 본다.
        var cursor = now - 3 * HOUR
        while (true) {
            val (start, windowEnd) = insulinWindow(plan, cursor, zone) ?: break
            if (start > end) break
            cursor = windowEnd
            val slot = insulinSlotAt(plan, start, zone) ?: continue
            if (slot.occurrence < plan.activeFrom || insulinHandled(pet.id, insulinRecords, slot) != null) continue
            if (petRests.any { it.startedAt <= slot.occurrence && (it.endedAt == null || slot.occurrence < it.endedAt) }) continue
            val fires = buildList {
                add(MedAlertKind.insulinDue to slot.occurrence - plan.leadMinutes * MINUTE)
                if (plan.remindAgainMinutes > 0) add(MedAlertKind.insulinAgain to slot.occurrence + plan.remindAgainMinutes * MINUTE)
            }
            for ((kind, fireAt) in fires) {
                if (fireAt <= now || fireAt > end) continue
                if (quiet != null && quiet.enabled && !plan.ringDuringQuiet && inQuiet(Instant.ofEpochMilli(fireAt).atZone(zone).toLocalTime(), quiet)) continue
                entries += Entry(kind, minute(fireAt), slot.occurrence, pet, slot.units)
            }
        }
    }
    return entries.groupBy { it.kind to it.fireAt }.map { (k, list) ->
        val (kind, fireAt) = k
        val sorted = list.sortedBy { it.pet.createdAt }
        val texts = sorted.map { "${it.pet.name} ${medNumber(it.units)}단위" }
        val body = if (texts.size >= 4) texts.take(3).joinToString(" · ") + " 외 ${texts.size - 3}" else texts.joinToString(" · ")
        val isDue = kind == MedAlertKind.insulinDue
        val title = if (isDue) "인슐린 · " + clockText(sorted.minOf { it.due }, zone) else "아직 인슐린 기록이 없어요"
        MedAlert("insulin.${if (isDue) "due" else "again"}.${fireAt / 1000}", kind, fireAt, title, body, sorted.first().pet.id)
    }.sortedBy { it.fireAt }
}

private fun minute(ms: Long): Long = Math.floorDiv(ms, MINUTE) * MINUTE

/**
 * 남은 약 알림(plan.md 8-4 '남은 약 알림'): 약방 '○일분'이 [lowMedDays] 이하가 되는 날 오전 10시에 약마다 한 번.
 * 이미 기준 이하면 오늘 10시(지났으면 내일). [sent]는 이미 울린 '약 id|stockCountedAt' — 남은 양을 다시 적으면 다시 보낸다.
 * 밤 알림 쉬기가 10시에 걸리면 보내지 않는다. 지금 뒤 72시간 안만.
 */
fun lowMedAlerts(
    meds: List<Medication>, schedules: List<MedSchedule>, medRecords: List<Record>,
    lowMedOn: Boolean, lowMedDays: Int, quiet: QuietHours?, sent: Set<String>,
    now: Long, zone: ZoneId = ZoneId.systemDefault(),
): List<MedAlert> {
    if (!lowMedOn) return emptyList()
    val ten = LocalTime.of(10, 0)
    if (quiet != null && quiet.enabled && inQuiet(ten, quiet)) return emptyList()
    val today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    fun at(day: LocalDate) = day.atTime(ten).atZone(zone).toInstant().toEpochMilli()
    return meds.filter { it.deletedAt == null && it.stockAmount != null }.mapNotNull { med ->
        val d = daysLeft(med, schedules, medRecords) ?: return@mapNotNull null
        if (lowMedKey(med) in sent) return@mapNotNull null
        val day = if (d <= lowMedDays) today.let { if (at(it) > now) it else it.plusDays(1) } else today.plusDays((d - lowMedDays).toLong())
        val fireAt = at(day)
        if (fireAt <= now || fireAt > now + WINDOW) return@mapNotNull null
        // 울리는 날에 남을 일수(하루에 하루분씩 줄어듦) — 10시가 지나 내일로 밀리면 하루 적게.
        val left = d - java.time.temporal.ChronoUnit.DAYS.between(today, day).toInt()
        MedAlert("low.${med.id}", MedAlertKind.low, fireAt, "약방", lowMedBody(med.name, left), null, med.id)
    }.sortedBy { it.fireAt }.take(LIMIT)
}

/** '프레드니솔론이 3일분 남았어요', 하루분도 안 남으면 '프레드니솔론이 거의 다 떨어졌어요'. */
fun lowMedBody(name: String, days: Int): String =
    if (days > 0) "${withSubjectParticle(name)} ${days}일분 남았어요" else "${withSubjectParticle(name)} 거의 다 떨어졌어요"

/** 남은 약 알림을 한 번만 보내려고 기억하는 열쇠 — 남은 양을 다시 적으면 바뀐다. */
fun lowMedKey(med: Medication) = "${med.id}|${med.stockCountedAt}"

/** `HH:mm` 쉬는 시간 안인지(자정을 넘는 22:00~07:00도). 시작 = 끝이면 비어 있음. */
private fun inQuiet(t: LocalTime, quiet: QuietHours): Boolean {
    val start = runCatching { LocalTime.parse(quiet.start) }.getOrNull() ?: return false
    val end = runCatching { LocalTime.parse(quiet.end) }.getOrNull() ?: return false
    return if (start <= end) t >= start && t < end else t >= start || t < end
}
