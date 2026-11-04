package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.DoseUnit
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDatabase
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.newId
import app.okche.ui.med.hasOpenSchedule
import app.okche.ui.med.medButtons
import app.okche.ui.med.medShortName
import app.okche.ui.med.medValue
import app.okche.ui.med.recordSlot
import app.okche.ui.med.remainingSlots
import app.okche.ui.med.spokenTime
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import app.okche.ui.record.deleteRecord
import app.okche.ui.record.recordValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 탕약 차례·오늘 남은 차례·차례 기록(plan.md 8-3a, 데이터 5장 예 1~3·6-1·6-2). iOS MedSlotsTests와 같은 경우. */
class MedSlotsTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val pet = newId()
    private val day = "2026-10-27"
    private fun at(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 10, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun row(
        freq: MedFrequency = MedFrequency.daily, times: List<String> = emptyList(), hours: Int? = null, firstAt: Long? = null, shift: Boolean = true,
        med: String = newId(), series: MedSchedule? = null, from: Long, until: Long? = null,
    ): MedSchedule {
        val id = newId()
        return MedSchedule(id = id, petId = pet, medicationId = series?.medicationId ?: med, seriesId = series?.seriesId ?: id, amount = 1.0, unit = DoseUnit.pill,
            frequency = freq, times = times, everyHours = hours, firstAt = firstAt, shiftOnLate = shift, activeFrom = from, activeUntil = until)
    }
    private fun given(s: MedSchedule, key: String, time: Long, recordDay: String? = null, status: String = "given") = Record(
        petId = pet, kind = RecordKind.med, occurredAt = time, day = recordDay ?: app.okche.store.OkcheDay.of(time), slotKey = key,
        data = JSONObject().put("amount", 1).put("medicationId", s.medicationId).put("scheduleId", s.id).put("status", status).put("unit", "pill").toString(),
    )
    private fun key(s: MedSchedule, time: String, d: String = "2026-10-27") = "${s.id}|$d|$time"
    private fun remaining(rows: List<MedSchedule>, records: List<Record>) = remainingSlots(rows, records, pet, day).map { it.due }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun firstDayAndBefore6() {
        // 처음 만든 날은 activeFrom 뒤 시간부터, 06:00 전 시간은 다음 날 새벽.
        val s = row(times = listOf("08:00", "20:00", "02:00"), from = at(27, 10))
        assertEquals(listOf(at(27, 20), at(28, 2)), remaining(listOf(s), emptyList()))
        // 처리한 차례는 빠지고(건너뜀도), 시간이 지나도 남는다.
        val old = row(times = listOf("08:00", "20:00"), from = at(20, 9))
        assertEquals(listOf(at(27, 20)), remaining(listOf(old), listOf(given(old, key(old, "08:00"), at(27, 8, 5), status = "skipped"))))
        assertEquals(listOf(at(27, 8), at(27, 20)), remaining(listOf(old), emptyList()))
    }

    @Test fun changedDayExamples() {
        // 예 1: 08:00에 주고 10:00에 09:00·21:00으로 → 09:00 넘김, 21:00만.
        val s1 = row(times = listOf("08:00", "20:00"), from = at(20, 9), until = at(27, 10))
        val s2 = row(times = listOf("09:00", "21:00"), series = s1, from = at(27, 10))
        val g = given(s1, key(s1, "08:00"), at(27, 8))
        assertEquals(listOf(at(27, 21)), remaining(listOf(s1, s2), listOf(g)))
        // 예 2: 안 주고 바꾸면 옛 08:00은 버리고 새 09:00(지났어도)·21:00.
        assertEquals(listOf(at(27, 9), at(27, 21)), remaining(listOf(s1, s2), emptyList()))
        // 예 3: 08:00에 주고 08:30에 S2, 10:00에 S3 → k = 1.
        val a1 = row(times = listOf("08:00", "20:00"), from = at(20, 9), until = at(27, 8, 30))
        val a2 = row(times = listOf("09:00", "21:00"), series = a1, from = at(27, 8, 30), until = at(27, 10))
        val a3 = row(times = listOf("10:30", "22:00"), series = a1, from = at(27, 10))
        assertEquals(listOf(at(27, 22)), remaining(listOf(a3, a1, a2), listOf(given(a1, key(a1, "08:00"), at(27, 8)))))
        // 필요할 때만 → 매일로 바꾼 날도 바꾼 날 규칙(k = 0, 지난 08:00도 뜸).
        val n1 = row(MedFrequency.asNeeded, from = at(20, 9), until = at(27, 10))
        val n2 = row(times = listOf("08:00", "20:00"), series = n1, from = at(27, 10))
        assertEquals(listOf(at(27, 8), at(27, 20)), remaining(listOf(n1, n2), emptyList()))
        // 같은 주기에 열린 줄이 둘(백업 병합)이면 activeFrom이 늦은 것 하나만.
        val o1 = row(times = listOf("08:00"), from = at(20, 9))
        val o2 = row(times = listOf("09:00"), series = o1, from = at(21, 9))
        assertEquals(listOf(at(27, 9)), remaining(listOf(o1, o2), emptyList()))
        // 빠진 열린 줄로 준 기록은 짝 없는 기록 — 남은 차례 앞에서 하나 처리(하루 세 번 먹이지 않게).
        assertEquals(listOf(at(27, 21)), remaining(listOf(o1, row(times = listOf("09:00", "21:00"), series = o1, from = at(27, 7))),
            listOf(given(o1, key(o1, "08:00"), at(27, 8)))))
        // 바꾼 다음 날은 새 줄 그대로.
        assertEquals(listOf(at(28, 9), at(28, 21)), remainingSlots(listOf(s1, s2), listOf(g), pet, "2026-10-28").map { it.due })
    }

    @Test fun hoursShiftAndUnmatched() {
        val late = row(MedFrequency.everyNHours, hours = 8, firstAt = at(27, 8), from = at(27, 7))
        // 늦게 줬을 때 밀림: #1 = 09:00 + 8 = 17:00, #2 = 01:00(새벽이라 같은 날).
        assertEquals(listOf(at(27, 17), at(28, 1)), remaining(listOf(late), listOf(given(late, "${late.id}|#0", at(27, 9)))))
        // 처음 정한 시간대로: 16:00, 00:00.
        val fixed = row(MedFrequency.everyNHours, hours = 8, firstAt = at(27, 8), shift = false, from = at(27, 7))
        assertEquals(listOf(at(27, 16), at(28, 0)), remaining(listOf(fixed), listOf(given(fixed, "${fixed.id}|#0", at(27, 9)))))
        // 짝 없는 기록: 닫힌 옛 줄 키로 오늘 준 기록 → 남은 차례 앞에서 하나 처리.
        val old = row(times = listOf("08:00"), from = at(20, 9), until = at(26, 12))
        val now = row(times = listOf("08:00", "20:00"), series = old, from = at(26, 12))
        assertEquals(listOf(at(27, 20)), remaining(listOf(old, now), listOf(given(old, key(old, "08:00"), at(27, 8, 10)))))
    }

    @Test fun buttonsAndRecord() = runBlocking {
        val a = row(times = listOf("08:00", "20:00"), from = at(20, 9))
        val b = row(times = listOf("09:00"), from = at(20, 9))
        val c = row(times = listOf("10:00"), from = at(20, 9))
        val d = row(times = listOf("11:00"), from = at(20, 9))
        assertEquals(listOf(at(27, 8), at(27, 9), at(27, 10)), medButtons(remainingSlots(listOf(a, b, c, d), emptyList(), pet, day)).map { it.due }) // 같은 약 하나·최대 3
        assertTrue(hasOpenSchedule(listOf(a), pet))
        assertFalse(hasOpenSchedule(listOf(a), newId()))

        // 누르면 기록(day = 차례의 날 — 저녁 20:00 차례를 다음 날 06:30에 줘도 전날) → 빠짐, 지우면 돌아옴.
        val med = Medication(name = "프레드니솔론", shortName = "프레드", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill)
        dao.insert(med)
        val s = row(times = listOf("20:00"), med = med.id, from = at(20, 9))
        val slot = remainingSlots(listOf(s), emptyList(), pet, day)[0]
        val record = recordSlot(dao, slot, now = at(28, 6, 30))
        assertEquals(listOf(day, key(s, "20:00")), listOf(record.day, record.slotKey))
        assertEquals("프레드니솔론 1알", recordValue(record, meds = listOf(med)))
        val saved = dao.medRecordsFlow().first()
        assertTrue(remainingSlots(listOf(s), saved, pet, day).isEmpty())
        deleteRecord(dao, saved.single())
        assertEquals(1, remainingSlots(listOf(s), dao.medRecordsFlow().first(), pet, day).size)
        assertEquals("프레드", medShortName(med))
        assertEquals("프레드니솔론 건너뜀", medValue(given(s, "x", at(27, 9), status = "skipped"), listOf(med)))
        assertEquals("프레드니솔론 먹고 토함", medValue(given(s, "x", at(27, 9), status = "vomited"), listOf(med)))
        assertEquals("오후 9시", spokenTime(at(27, 21)))
    }
}

/** 몇 시간마다 약(처음 먹일 때 = 지금) → 일기 탕약 버튼 → 기록·되돌리기 알림 → 되돌리기 → 버튼이 돌아옴(8-3a). */
class RecordMedFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun giveAndUndo() {
        runBlocking {
            val dao = OkcheDatabase.get(context).dao()
            OkcheDatabase.get(context).clearAllTables()
            val petId = newId()
            registerPet(dao, PetForm(name = "콩이"), petId = petId)
            val med = Medication(name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill)
            dao.insert(med)
            val now = System.currentTimeMillis()
            dao.insert(MedSchedule(petId = petId, medicationId = med.id, amount = 1.0, unit = DoseUnit.pill, frequency = MedFrequency.everyNHours,
                everyHours = 8, firstAt = now, activeFrom = now))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("일기"))
        rule.onNode(tab("일기")).performClick()
        val give = hasContentDescription("탕약 프레드니솔론", substring = true).and(hasContentDescription("바로 기록", substring = true))
        waitFor(give)
        rule.onNode(give).performClick()
        waitFor(hasText("프레드니솔론 줌 · ", substring = true))
        waitFor(hasText("콩이 · 탕약 프레드니솔론 1알", substring = true))
        rule.onNode(hasText("되돌리기").and(hasClickAction())).performClick()
        waitFor(give) // 차례가 줄에 돌아옴
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("탕약 프레드니솔론", substring = true)).fetchSemanticsNodes().isEmpty() }
    }
}
