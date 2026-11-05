package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
import app.okche.ui.med.nextSlot
import app.okche.ui.med.medShortName
import app.okche.ui.med.medValue
import app.okche.ui.med.recordSlot
import app.okche.ui.med.remainingSlots
import app.okche.ui.med.spokenTime
import app.okche.ui.med.clockText
import app.okche.ui.med.extraMedOptions
import app.okche.ui.med.hhmm
import app.okche.ui.med.medRemaining
import app.okche.ui.med.recordSlots
import app.okche.ui.med.saveExtraMed
import app.okche.ui.med.slotDetail
import app.okche.ui.med.todaySlots
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
        // 08:00 안 주고 10:00에 그만 먹이면 그날 남은 차례도 바로 빠짐(뒤를 잇는 줄 없음).
        assertTrue(remaining(listOf(row(times = listOf("08:00", "20:00"), from = at(20, 9), until = at(27, 10))), emptyList()).isEmpty())
        // 추억으로 간직(10:00)했다가 같은 날 되돌려(11:00) 다시 연 줄이 있으면 그만 먹인 게 아님 — 안 준 08:00은 남음.
        val kept = row(times = listOf("08:00", "20:00"), from = at(20, 9), until = at(27, 10))
        assertEquals(listOf(at(27, 8), at(27, 20)), remaining(listOf(kept, row(times = listOf("08:00", "20:00"), series = kept, from = at(27, 11))), emptyList()))
        // 추억 되돌린 날 다시 그만 먹이면(12:00) 옛 줄의 안 준 08:00도 빠짐.
        val reopened = row(times = listOf("08:00", "20:00"), series = kept, from = at(27, 11), until = at(27, 12))
        assertTrue(remaining(listOf(kept, reopened), emptyList()).isEmpty())
        // 바꾼 다음 날은 새 줄 그대로.
        assertEquals(listOf(at(28, 9), at(28, 21)), remainingSlots(listOf(s1, s2), listOf(g), pet, "2026-10-28").map { it.due })
    }

    @Test fun hoursShiftAndUnmatched() {
        val late = row(MedFrequency.everyNHours, hours = 8, firstAt = at(27, 8), from = at(27, 7))
        // 늦게 줬을 때 밀림: #1 = 09:00 + 8 = 17:00, #2 = 01:00(새벽이라 같은 날).
        assertEquals(listOf(at(27, 17), at(28, 1)), remaining(listOf(late), listOf(given(late, "${late.id}|#0", at(27, 9)))))
        // 처음 먹일 때가 저장한 순간보다 조금 앞이어도(분 올림) 0번 차례는 남음.
        assertEquals(at(27, 8), remaining(listOf(row(MedFrequency.everyNHours, hours = 8, firstAt = at(27, 8), from = at(27, 8) + 30_000)), emptyList()).first())
        // 옛 firstAt으로 다시 연 줄(추억 되돌리기)은 연 때 뒤 차례만.
        assertEquals(listOf(at(28, 0)), remaining(listOf(row(MedFrequency.everyNHours, hours = 8, firstAt = at(13, 8), from = at(27, 20))), emptyList()))
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

    /** 8-3b: 오늘 모든 차례(기록한 것 포함)·여러 개 한 번에·예정에 없던 약·기록 고치기·고르기 순서. */
    @Test fun todaySheetAndExtra() = runBlocking {
        val med = Medication(name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill, stockAmount = 10.0)
        val syrup = Medication(name = "위장약 시럽", category = MedCategory.medicine, form = MedForm.liquid, doseAmount = 2.0, unit = DoseUnit.ml)
        val other = Medication(name = "유산균", category = MedCategory.supplement, form = MedForm.powder, doseAmount = 1.0, unit = DoseUnit.pack)
        listOf(med, syrup, other).forEach { dao.insert(it) }
        val s = row(times = listOf("08:00", "20:00"), med = med.id, from = at(20, 9))
        val need = row(MedFrequency.asNeeded, med = syrup.id, from = at(20, 9))
        // 08:00은 줬고 20:00은 남음 — 시트에는 둘 다, 기록한 차례는 그 기록과 함께.
        val given8 = given(s, key(s, "08:00"), at(27, 8, 5))
        val entries = todaySlots(listOf(s, need), listOf(given8), pet, day)
        assertEquals(listOf(at(27, 8), at(27, 20)), entries.map { it.slot.due })
        assertTrue(entries[0].record === given8)
        assertEquals(null, entries[1].record)
        assertEquals("08:05", hhmm(at(27, 8, 5)))
        assertEquals("오후 8:00", clockText(at(27, 20)))
        assertEquals("알약 · 1알", slotDetail(s, med))

        // 기록하기: 고른 차례마다(건너뜀도) 한 번에.
        val saved = recordSlots(dao, listOf(entries[1].slot to "skipped"), at(27, 21))
        assertEquals(listOf(key(s, "20:00")), saved.map { it.slotKey })
        assertEquals("프레드니솔론 건너뜀", recordValue(saved[0], meds = listOf(med)))
        assertTrue(remainingSlots(listOf(s, need), listOf(given8) + saved, pet, day).isEmpty())

        // 예정에 없던 약: 키 없음, 약방 남은 양에서 빠짐.
        val extra = saveExtraMed(dao, pet, med, 2.0, "given", at(27, 12))
        assertEquals(null, extra.slotKey)
        assertFalse(JSONObject(extra.data).has("scheduleId"))
        assertEquals(8.0, medRemaining(med, listOf(extra))!!, 0.0)
        assertEquals(2, remainingSlots(listOf(s), listOf(extra), pet, day).size) // 차례는 그대로

        // 고치기: 양·상태·시간. 차례 기록은 차례의 날 그대로, 예정에 없던 약은 바뀐 시각의 날.
        val slotRecord = recordSlot(dao, entries[1].slot, now = at(27, 21))
        val edited = saveExtraMed(dao, pet, med, 1.0, "vomited", at(28, 7), editing = slotRecord)
        assertEquals(listOf(day, "프레드니솔론 먹고 토함"), listOf(edited.day, recordValue(edited, meds = listOf(med))))
        assertEquals("2026-10-28", saveExtraMed(dao, pet, med, 2.0, "given", at(28, 7), editing = extra).day)

        // 고르기 순서: 이 아이의 필요할 때만 → 먹는 약 → 나머지.
        val kong = app.okche.store.Pet(id = pet, name = "콩이", species = app.okche.store.Species.cat, breed = "", furColor = "")
        val options = extraMedOptions(listOf(other, med, syrup), listOf(s, need), kong)
        assertEquals(listOf("위장약 시럽", "프레드니솔론", "유산균"), options.map { it.first.name })
        assertEquals(listOf("물약 · 필요할 때만", "알약 · 콩이가 먹는 약", "가루"), options.map { it.second })
    }

    /** 지우기·빼기 뒤 '되돌리기'(2026-11-05 사용자): 기록은 지울 때 비운 내용까지, 약방 약, 그만 먹인 줄(새 줄이 없을 때만). */
    @Test fun removeThenRestore() = runBlocking {
        val med = Medication(name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill)
        dao.insert(med)
        val s = row(times = listOf("20:00"), med = med.id, from = at(20, 9))
        dao.insert(s)
        val record = recordSlot(dao, remainingSlots(listOf(s), emptyList(), pet, day)[0], now = at(27, 21))
        app.okche.ui.record.deleteRecord(dao, record)
        assertEquals("{}", dao.medRecordsFlow().first().let { if (it.isEmpty()) "{}" else it.single().data })
        app.okche.ui.record.restoreRecord(dao, record)
        val back = dao.medRecordsFlow().first().single()
        assertEquals(listOf(record.data, record.slotKey), listOf(back.data, back.slotKey))
        app.okche.ui.med.removeMed(dao, med)
        app.okche.ui.med.restoreMed(dao, med)
        assertEquals(null, dao.medicationsFlow().first().single().deletedAt)
        app.okche.ui.med.stopSchedule(dao, s, at(27, 22))
        app.okche.ui.med.resumeSchedule(dao, s)
        assertEquals(null, dao.medSchedules().single().activeUntil)
        // 그 사이 새 줄이 열렸으면 되살리지 않음(한 주기에 열린 줄 둘이 되지 않게).
        app.okche.ui.med.stopSchedule(dao, dao.medSchedules().single(), at(27, 22))
        dao.insert(row(times = listOf("21:00"), series = s, from = at(27, 23)))
        assertTrue(runCatching { app.okche.ui.med.resumeSchedule(dao, s) }.isFailure) // '되돌리지 못했어요'로 알림
        assertTrue(dao.medSchedules().first { it.id == s.id }.activeUntil != null)
    }

    /** 8-3c: 다음 탕약 — 모든 아이(추억 빼고)의 오늘 남은 차례 중 가장 이른 것, 같으면 먼저 모신 아이. */
    @Test fun nextAcrossPets() {
        val kong = app.okche.store.Pet(id = pet, createdAt = at(1, 9), name = "콩이", species = app.okche.store.Species.cat, breed = "", furColor = "")
        val bori = app.okche.store.Pet(createdAt = at(2, 9), name = "보리", species = app.okche.store.Species.cat, breed = "", furColor = "")
        val gone = app.okche.store.Pet(createdAt = at(3, 9), name = "별이", species = app.okche.store.Species.cat, breed = "", furColor = "", memorializedAt = at(4, 9))
        fun s(p: app.okche.store.Pet, t: String) = row(times = listOf(t), from = at(20, 9)).copy(petId = p.id)
        val rows = listOf(s(kong, "21:00"), s(bori, "21:00"), s(bori, "23:00"), s(gone, "07:00"))
        val next = nextSlot(rows, emptyList(), listOf(bori, gone, kong), day)
        assertEquals("콩이", next?.second?.name) // 같은 21:00이면 먼저 모신 아이, 추억 아이(07:00)는 빼고
        assertEquals(at(27, 21), next?.first?.due)
        assertEquals(null, nextSlot(emptyList(), emptyList(), listOf(kong), day))
        assertEquals("약", app.okche.ui.main.plainWord(RecordKind.med))
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
        // '되돌리기' = 지우기 확인 창 → 지움 → '기록을 지웠어요 · 되돌리기' → 되돌리면 기록이 돌아옴.
        rule.onNode(hasText("되돌리기").and(hasClickAction())).performClick()
        waitFor(hasText("이 기록을 지울까요?"))
        rule.onNode(hasText("지우기").and(hasClickAction())).performClick()
        waitFor(hasText("기록을 지웠어요"))
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("탕약 프레드니솔론", substring = true)).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasText("되돌리기").and(hasClickAction())).performClick()
        waitFor(hasText("콩이 · 탕약 프레드니솔론 1알", substring = true))
    }

    /** 8-3b: ⋯ 오늘 먹일 약 시트 → 줬어요 → 기록하기, 예정에 없던 약 → 기록하기, 연필 → 먹고 토함으로 고치기. */
    @Test fun sheetExtraEdit() {
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
        waitFor(hasContentDescription("탕약 자세히 남기기"))
        rule.onNode(hasContentDescription("탕약 자세히 남기기")).performClick()
        waitFor(hasText("다음 차례"))
        rule.onNode(hasText("기록하기").and(hasClickAction())).assertIsNotEnabled() // 고른 게 없으면 흐리게 + 안내
        waitFor(hasText("먹인 약을 하나 이상 고르면 기록할 수 있어요"))
        rule.onAllNodes(hasText("줬어요").and(hasClickAction()))[0].performClick()
        rule.onNode(hasText("기록하기").and(hasClickAction())).performClick()
        waitFor(hasText("콩이 · 탕약 프레드니솔론 1알", substring = true))
        rule.onNode(hasContentDescription("탕약 자세히 남기기")).performClick()
        waitFor(hasText("에 기록함", substring = true))
        rule.onNode(hasText("+ 예정에 없던 약 남기기")).performScrollTo().performClick()
        waitFor(hasText("정해 둔 시간 밖에 준 약이라 탕약 횟수(1/3)에는 들어가지 않아요. 약방 남은 양에서는 빠져요."))
        rule.onNode(hasText("프레드니솔론").and(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))).performClick()
        rule.onAllNodes(hasText("기록하기").and(hasClickAction())).onLast().performClick() // 위에 뜬 시트의 것
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("+ 예정에 없던 약 남기기")).fetchSemanticsNodes().isEmpty() } // 두 시트 다 닫힘
        rule.onNode(hasContentDescription("콩이 탕약 기록 고치기", substring = true)).performScrollTo().performClick()
        waitFor(hasText("콩이 · 탕약 기록", substring = true))
        rule.onNode(hasText("먹고 토함").and(hasClickAction())).performClick()
        rule.onNode(hasText("기록하기").and(hasClickAction())).performClick()
        waitFor(hasText("탕약 프레드니솔론 먹고 토함", substring = true))
    }

    /** 8-3c: 문안 '예정' 줄 → 오늘 먹일 약 시트, 탕약 화면 '다음 탕약' 띠 '줬어요' → 기록 알림. */
    @Test fun mainPendingAndNextBand() {
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
        waitFor(tab("문안"))
        rule.onNode(tab("문안")).performClick()
        val pending = hasContentDescription("오늘 먹일 약 보기", substring = true) // 차례가 막 지나면 '놓침'
        waitFor(pending)
        rule.onAllNodes(pending)[0].performScrollTo().performClick() // 하루에 차례가 여럿이면 이른 것
        waitFor(hasText("다음 차례"))
        rule.onNode(hasContentDescription("닫기")).performClick()
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("약 알림", substring = true))
        rule.onNode(hasText("약 알림", substring = true)).performScrollTo().performClick()
        val band = hasContentDescription("콩이 프레드니솔론 1알 줬어요", substring = true)
        waitFor(band)
        rule.onNode(band).performClick()
        waitFor(hasText("프레드니솔론 줌 · ", substring = true))
    }
}
