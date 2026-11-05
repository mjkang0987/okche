package app.okche

import app.okche.notify.MedAlertKind
import app.okche.notify.medAlerts
import app.okche.store.DoseUnit
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.RestPeriod
import app.okche.store.Species
import app.okche.store.newId
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 탕약 알림 목록 계산(plan.md 8-4a 계산 규칙). 순수 함수, 지금 = 10/27 12:00. iOS MedNotifyTests와 같은 경우. */
class MedNotifyTest {
    private fun at(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 10, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val now = at(27, 12)
    private fun pet(name: String, created: Long, memorial: Long? = null) =
        Pet(name = name, species = Species.cat, breed = "", furColor = "", createdAt = created, memorializedAt = memorial)
    private fun med(name: String) = Medication(name = name, category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill)
    private fun row(
        pet: Pet, med: Medication, times: List<String>, lead: Int = 10, again: Int = 0, reminder: Boolean = true,
        freq: MedFrequency = MedFrequency.daily, hours: Int? = null, firstAt: Long? = null, amount: Double = 1.0, unit: DoseUnit = DoseUnit.pill,
    ) = MedSchedule(
        petId = pet.id, medicationId = med.id, amount = amount, unit = unit, frequency = freq, times = times,
        everyHours = hours, firstAt = firstAt, reminder = reminder, leadMinutes = lead, remindAgainMinutes = again, activeFrom = at(20, 9),
    )
    private val kong = pet("콩이", at(1, 9))
    private val bori = pet("보리", at(2, 9))

    private fun alerts(rows: List<MedSchedule>, meds: List<Medication>, records: List<Record> = emptyList(), pets: List<Pet> = listOf(kong, bori),
                       rests: List<RestPeriod> = emptyList(), on: Boolean = true) = medAlerts(rows, records, pets, rests, meds, on, now)

    @Test fun leadAndWindow() {
        val p = med("프레드니솔론")
        val list = alerts(listOf(row(kong, p, listOf("21:00"))), listOf(p))
        // 27일 12시부터 72시간: 27·28·29일 21:00 − 10분, 30일 20:50은 창 밖.
        assertEquals(listOf(at(27, 20, 50), at(28, 20, 50), at(29, 20, 50)), list.filter { it.kind == MedAlertKind.due }.map { it.fireAt })
        assertEquals("med.due.${at(27, 20, 50) / 1000}", list.first().id)
        assertEquals("탕약 · 오후 9:00", list.first().title)
        assertEquals("콩이 프레드니솔론 1알", list.first().body)
        assertEquals(kong.id, list.first().petId)
        assertEquals(MedAlertKind.more, list.last().kind)
        assertEquals(at(29, 20, 51), list.last().fireAt)
        assertEquals(listOf("med.more", "옥체", "옥체를 열면 알림이 이어져요"), listOf(list.last().id, list.last().title, list.last().body))
        assertNull(list.last().petId)
        // 지난 차례(08:00)는 울리지 않음, 몇 분 전 0이면 차례 시각.
        assertTrue(alerts(listOf(row(kong, p, listOf("08:00"))), listOf(p)).none { it.fireAt == at(27, 7, 50) })
        assertEquals(at(27, 21), alerts(listOf(row(kong, p, listOf("21:00"), lead = 0)), listOf(p)).first().fireAt)
    }

    @Test fun handledAndExcluded() {
        val p = med("프레드니솔론")
        val s = row(kong, p, listOf("21:00"))
        val given = Record(
            petId = kong.id, kind = RecordKind.med, occurredAt = at(27, 11), day = "2026-10-27", slotKey = "${s.id}|2026-10-27|21:00",
            data = JSONObject().put("amount", 1).put("medicationId", p.id).put("scheduleId", s.id).put("status", "given").put("unit", "pill").toString(),
        )
        // 미리 준 오늘 차례는 빠지고 내일부터.
        assertEquals(at(28, 20, 50), alerts(listOf(s), listOf(p), records = listOf(given)).first().fireAt)
        assertTrue(alerts(listOf(row(kong, p, listOf("21:00"), reminder = false)), listOf(p)).isEmpty())
        assertTrue(alerts(listOf(s), listOf(p), on = false).isEmpty())
        val gone = pet("별이", at(3, 9), memorial = at(4, 9))
        assertTrue(alerts(listOf(row(gone, p, listOf("21:00"))), listOf(p), pets = listOf(gone)).isEmpty())
        assertTrue(alerts(listOf(s), listOf(p), pets = listOf(kong.copy(deletedAt = at(26, 9)))).isEmpty())
        assertTrue(alerts(listOf(row(kong, p, listOf("21:00"), freq = MedFrequency.asNeeded)), listOf(p)).isEmpty())
        assertTrue(alerts(listOf(s), listOf(p.copy(deletedAt = at(26, 9)))).isEmpty())
        // 휴양 중(28일 끝)이면 27일만 빠짐, 끝나지 않은 휴양이면 모두, 지운 휴양은 셈에 안 듦.
        val rest = RestPeriod(petId = kong.id, startedAt = at(26, 9), endedAt = at(28, 9))
        assertEquals(at(28, 20, 50), alerts(listOf(s), listOf(p), rests = listOf(rest)).first().fireAt)
        assertTrue(alerts(listOf(s), listOf(p), rests = listOf(rest.copy(endedAt = null))).isEmpty())
        assertEquals(at(27, 20, 50), alerts(listOf(s), listOf(p), rests = listOf(rest.copy(endedAt = null, deletedAt = at(26, 10)))).first().fireAt)
    }

    @Test fun groupingAndAgain() {
        val p = med("프레드니솔론"); val u = med("유산균"); val a = med("가바펜틴"); val z = med("츄르약")
        val list = alerts(listOf(row(bori, u, listOf("21:00"), amount = 0.5, unit = DoseUnit.pack), row(kong, p, listOf("21:00"), again = 30)), listOf(p, u))
        val first = list[0]
        assertEquals("탕약 · 오후 9:00", first.title)
        assertEquals("콩이 프레드니솔론 1알 · 보리 유산균 반 포", first.body) // 먼저 모신 아이부터
        assertEquals(kong.id, first.petId)
        val again = list.first { it.kind == MedAlertKind.again }
        assertEquals(at(27, 21, 30), again.fireAt)
        assertEquals("아직 안 준 약이 있어요", again.title)
        assertEquals("콩이 프레드니솔론", again.body)
        assertEquals("med.again.${at(27, 21, 30) / 1000}", again.id)
        // 넷이면 셋 + 외 1(같은 아이는 약 이름 순).
        val four = alerts(listOf(row(kong, p, listOf("21:00")), row(kong, u, listOf("21:00")), row(kong, a, listOf("21:00")), row(bori, z, listOf("21:00"))), listOf(p, u, a, z))
        assertEquals("콩이 가바펜틴 1알 · 콩이 유산균 1알 · 콩이 프레드니솔론 1알 외 1", four[0].body)
        // 같은 시각이면 차례 알림이 먼저, 몇 분 전이 달라 같은 시각에 울리면 제목은 가장 이른 차례.
        val tie = alerts(listOf(row(kong, p, listOf("20:00"), again = 30), row(bori, u, listOf("20:40"))), listOf(p, u))
        assertEquals(listOf(MedAlertKind.due, MedAlertKind.again), tie.filter { it.fireAt == at(27, 20, 30) }.map { it.kind })
        val mixed = alerts(listOf(row(kong, p, listOf("21:00"), lead = 30), row(bori, u, listOf("20:30"), lead = 0)), listOf(p, u))
        assertEquals("탕약 · 오후 8:30", mixed[0].title)
    }

    @Test fun capAndMore() {
        val p = med("프레드니솔론")
        // 1시간마다 → 72시간에 72개 → 63개 + 이어짐.
        val list = alerts(listOf(row(kong, p, emptyList(), lead = 0, freq = MedFrequency.everyNHours, hours = 1, firstAt = at(27, 12, 30))), listOf(p))
        assertEquals(64, list.size)
        assertEquals(63, list.count { it.kind == MedAlertKind.due })
        assertEquals(list[62].fireAt + 60_000, list.last().fireAt)
        assertEquals(64, list.map { it.id }.toSet().size)
        assertEquals(list.map { it.fireAt }.sorted(), list.map { it.fireAt })
        // 초가 붙은 차례(12:30:12)도 분 단위로 내려 울림.
        val odd = alerts(listOf(row(kong, p, emptyList(), lead = 0, freq = MedFrequency.everyNHours, hours = 8, firstAt = at(27, 12, 30) + 12_345)), listOf(p))
        assertEquals(at(27, 12, 30), odd.first().fireAt)
    }
}
