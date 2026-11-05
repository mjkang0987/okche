package app.okche.notify

import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RestPeriod
import app.okche.ui.med.amountText
import app.okche.ui.med.clockText
import app.okche.ui.med.remainingSlots
import app.okche.ui.record.shiftDay
import java.time.ZoneId

// 탕약 알림 목록 계산(plan.md 8-4a 계산 규칙, 데이터 5장 `leadMinutes`·`remindAgainMinutes`). 휴대폰에 거는 일은 8-4b.

enum class MedAlertKind { due, again, more }

data class MedAlert(val id: String, val kind: MedAlertKind, val fireAt: Long, val title: String, val body: String, val petId: String?)

private const val MINUTE = 60_000L
private const val WINDOW = 72 * 3_600_000L
private const val LIMIT = 63

/** 오늘(6시 기준)부터 3일 뒤까지 남은 차례의 알림. 같은 시각·같은 종류는 하나로, 이른 것부터 63개 + 이어짐 알림. 밤 알림 쉬기와 상관없음. */
fun medAlerts(
    schedules: List<MedSchedule>, medRecords: List<Record>, pets: List<Pet>, rests: List<RestPeriod>, meds: List<Medication>,
    medTimeOn: Boolean, now: Long, zone: ZoneId = ZoneId.systemDefault(),
): List<MedAlert> {
    if (!medTimeOn) return emptyList()
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
        .sortedWith(compareBy({ it.fireAt }, { it.kind.ordinal }))
        .take(LIMIT)
    val last = alerts.lastOrNull() ?: return alerts
    return alerts + MedAlert("med.more", MedAlertKind.more, last.fireAt + MINUTE, "옥체", "옥체를 열면 알림이 이어져요", null)
}

private fun minute(ms: Long): Long = Math.floorDiv(ms, MINUTE) * MINUTE
