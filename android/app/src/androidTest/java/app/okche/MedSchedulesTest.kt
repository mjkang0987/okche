package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelected
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.DoseUnit
import app.okche.store.HowTo
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.store.newId
import app.okche.ui.med.MedFields
import app.okche.ui.med.ScheduleFields
import app.okche.ui.med.addMed
import app.okche.ui.med.addSchedules
import app.okche.ui.med.amountText
import app.okche.ui.med.cardDetail
import app.okche.ui.med.clock
import app.okche.ui.med.dailyAmount
import app.okche.ui.med.daysLeft
import app.okche.ui.med.doseDayText
import app.okche.ui.med.frequencyText
import app.okche.ui.med.hoursPreview
import app.okche.ui.med.isDoseDay
import app.okche.ui.med.medCards
import app.okche.ui.med.nextDue
import app.okche.ui.med.occurrence
import app.okche.ui.med.scheduleErrors
import app.okche.ui.med.stockText
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** 약 주기·탕약 규칙(plan.md 8-2a). iOS MedSchedulesTests와 같은 경우. */
class MedSchedulesTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun schedule(
        freq: MedFrequency, times: List<String> = listOf("08:00"), every: Int? = null, start: String? = null, weekdays: List<Int> = emptyList(),
        hours: Int? = null, firstAt: Long? = null, amount: Double = 1.0, pet: String = newId(), med: String = newId(), unit: DoseUnit = DoseUnit.pill,
    ) = MedSchedule(petId = pet, medicationId = med, amount = amount, unit = unit, frequency = freq, times = times, everyDays = every, startDate = start,
        weekdays = weekdays, everyHours = hours, firstAt = firstAt, activeFrom = 0)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun texts() {
        assertEquals("1알", amountText(1.0, DoseUnit.pill))
        assertEquals("반 포", amountText(0.5, DoseUnit.pack))
        assertEquals("0.5ml", amountText(0.5, DoseUnit.ml))
        assertEquals("하루 2번", frequencyText(schedule(MedFrequency.daily, listOf("08:00", "21:00"))))
        assertEquals("이틀에 한 번", frequencyText(schedule(MedFrequency.everyNDays, every = 2)))
        assertEquals("5일에 한 번", frequencyText(schedule(MedFrequency.everyNDays, every = 5)))
        assertEquals("월·목", frequencyText(schedule(MedFrequency.weekdays, weekdays = listOf(4, 1))))
        assertEquals("8시간마다", frequencyText(schedule(MedFrequency.everyNHours, hours = 8)))
        assertEquals("콩이 · 하루 1번 · 1알 · 사료에 섞어서", cardDetail(schedule(MedFrequency.daily).copy(howTo = HowTo.inFood), "콩이"))
        assertEquals("콩이 · 하루 1번 · 1알", cardDetail(schedule(MedFrequency.daily).copy(howTo = HowTo.plain), "콩이"))
        assertEquals("오후 9:05", clock("21:05"))
        assertEquals("오전 12:30", clock("00:30"))
    }

    @Test fun doseDaysAndNextDue() {
        val every2 = schedule(MedFrequency.everyNDays, listOf("21:00"), every = 2, start = "2026-10-27")
        assertTrue(isDoseDay(every2, "2026-10-27"))
        assertFalse(isDoseDay(every2, "2026-10-28"))
        assertTrue(isDoseDay(every2, "2026-10-29"))
        assertFalse(isDoseDay(every2, "2026-10-25")) // 시작 전
        assertEquals("오늘 먹는 날 · 다음 10/29", doseDayText(every2, "2026-10-27"))
        assertEquals("다음 10/29", doseDayText(every2, "2026-10-28"))
        assertEquals(at(2026, 10, 29, 21), nextDue(every2, at(2026, 10, 27, 22)))
        val weekly = schedule(MedFrequency.weekdays, weekdays = listOf(1, 4)) // 월·목 — 2026-10-26이 월요일
        assertTrue(isDoseDay(weekly, "2026-10-26"))
        assertFalse(isDoseDay(weekly, "2026-10-27"))
        assertTrue(isDoseDay(weekly, "2026-10-29"))
        assertEquals(at(2026, 10, 28, 2), occurrence("2026-10-27", "02:00")) // 06:00 전 시간은 다음 날 새벽(1-2)
        val daily = schedule(MedFrequency.daily, listOf("21:00", "08:00"))
        assertEquals(at(2026, 10, 27, 21), nextDue(daily, at(2026, 10, 27, 9)))
        assertEquals(at(2026, 10, 28, 8), nextDue(daily, at(2026, 10, 27, 22)))
        val hours = schedule(MedFrequency.everyNHours, emptyList(), hours = 8, firstAt = at(2026, 10, 27, 8))
        assertEquals(at(2026, 10, 27, 8), nextDue(hours, at(2026, 10, 27, 7)))
        assertEquals(at(2026, 10, 27, 16), nextDue(hours, at(2026, 10, 27, 9)))
        assertNull(nextDue(schedule(MedFrequency.asNeeded, emptyList())))
        assertEquals(4, hoursPreview(at(2026, 10, 27, 8), 36).size)
    }

    @Test fun cardsOrderAndDaysLeft() = runBlocking {
        val kong = Pet(createdAt = at(2026, 1, 1, 9), name = "콩이", species = Species.cat, breed = "", furColor = "")
        val bori = Pet(createdAt = at(2026, 1, 2, 9), name = "보리", species = Species.dog, breed = "", furColor = "")
        val dubu = Pet(name = "두부", species = Species.cat, breed = "", furColor = "", memorializedAt = 1)
        val now = at(2026, 10, 27, 9)
        val a = schedule(MedFrequency.daily, listOf("21:00"), pet = kong.id)
        val b = schedule(MedFrequency.daily, listOf("21:00"), pet = bori.id)
        val c = schedule(MedFrequency.daily, listOf("12:00"), pet = bori.id)
        val need = schedule(MedFrequency.asNeeded, emptyList(), pet = kong.id)
        val memorial = schedule(MedFrequency.daily, pet = dubu.id)
        val closed = schedule(MedFrequency.daily, pet = kong.id).copy(activeUntil = now)
        assertEquals(listOf(c.id, a.id, b.id, need.id), medCards(listOf(need, a, b, c, memorial, closed), listOf(kong, bori, dubu), now).map { it.first.id })

        val med = addMed(dao, MedFields(name = "유산균", form = MedForm.powder, stock = "30"))
        val twice = schedule(MedFrequency.daily, listOf("08:00", "21:00"), pet = kong.id, med = med.id, unit = DoseUnit.pack) // 하루 2포
        val half = schedule(MedFrequency.daily, listOf("08:00"), amount = 0.5, pet = bori.id, med = med.id, unit = DoseUnit.pack) // 하루 반 포
        assertEquals(12, daysLeft(med, listOf(twice, half), emptyList())) // 30 ÷ 2.5 = 12
        assertEquals("남은 약 12일분", stockText(med, listOf(twice, half), emptyList()))
        assertEquals("남은 30포", stockText(med, emptyList(), emptyList()))
        assertEquals(1.0 / 3, dailyAmount(schedule(MedFrequency.everyNDays, every = 3)), 0.0001)
        assertEquals(2.0 / 7, dailyAmount(schedule(MedFrequency.weekdays, weekdays = listOf(1, 4))), 0.0001)
        assertEquals(3.0, dailyAmount(schedule(MedFrequency.everyNHours, emptyList(), hours = 8)), 0.0)
        assertEquals(0.0, dailyAmount(schedule(MedFrequency.asNeeded, emptyList())), 0.0)
    }

    @Test fun addMakesOneRowPerPet() = runBlocking {
        val kong = newId()
        val bori = newId()
        var f = ScheduleFields()
        assertEquals(setOf("pets", "med"), scheduleErrors(f).keys)
        f = f.copy(petIds = listOf(kong, bori), medicationId = newId(), dose = "0.5", frequency = MedFrequency.everyNDays, everyDays = "1")
        assertEquals(mapOf("everyDays" to "2 이상 숫자로 넣어 주세요"), scheduleErrors(f))
        f = f.copy(everyDays = "2", times = listOf("21:00", "08:00"), startDate = LocalDate.of(2026, 10, 27))
        val now = at(2026, 10, 27, 9)
        val rows = addSchedules(dao, f, DoseUnit.pill, now)
        assertEquals(listOf(kong, bori), rows.map { it.petId })
        assertEquals(listOf(now, now), rows.map { it.activeFrom })
        assertEquals(rows.map { it.id }, rows.map { it.seriesId }) // 각자
        assertEquals(listOf("08:00", "21:00"), rows[0].times)
        assertEquals(0.5, rows[0].amount, 0.0)
        assertEquals("2026-10-27", rows[0].startDate)
        assertNull(rows[0].everyHours)
        assertEquals(mapOf("weekdays" to "요일을 하나 이상 골라 주세요"), scheduleErrors(f.copy(frequency = MedFrequency.weekdays)))
    }
}

/** 더보기 → 탕약(약 없음) → 약 추가(오류 → 새 약 들이고 고르기 → 며칠마다) → 저장 → 카드 (8-2a). */
class TangyakFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun addMed() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("약 알림", substring = true))
        rule.onNode(hasText("약 알림", substring = true)).performScrollTo().performClick()
        waitFor(hasText("아직 먹이는 약이 없어요. 약을 추가하면 일기 탕약 줄과 알림에 나와요."))
        rule.onNode(hasText("+ 약 추가")).performClick()
        waitFor(hasText("저장하기"))
        rule.onNode(hasText("저장하기").and(hasClickAction())).assertIsNotEnabled() // 약을 고르기 전엔 흐리게 + 남은 것
        waitFor(hasText("약을 골라 주세요", substring = true)) // 남은 것 줄은 끝에 ' ›'
        rule.onNode(hasText("+ 약방에 새 약 들이기")).performScrollTo().performClick()
        waitFor(hasText("약방에 들이고 고르기"))
        rule.onNode(hasSetTextAction().and(hasText("약 이름", substring = true))).performTextInput("프레드니솔론")
        rule.onNode(hasText("약방에 들이고 고르기").and(hasClickAction())).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isSelected().and(hasText("프레드니솔론", substring = true))).fetchSemanticsNodes().isNotEmpty() } // 들이면 골라짐
        rule.onNode(hasText("며칠마다").and(hasClickAction())).performScrollTo().performClick()
        rule.onNode(hasText("저장하기").and(hasClickAction())).performClick()
        waitFor(hasText("먹고 있는 약"))
        waitFor(androidx.compose.ui.test.hasContentDescription("콩이 · 이틀에 한 번 · 1알", substring = true))
        // 카드 → 고치기(매일로) → 다시 카드 → 이 약 그만 먹이기 (8-2b)
        rule.onNode(androidx.compose.ui.test.hasContentDescription("· 고치기", substring = true)).performClick()
        waitFor(hasText("아이나 약을 바꾸려면 이 약을 그만 먹이고 새로 추가해 주세요."))
        rule.onNode(hasText("매일").and(hasClickAction())).performScrollTo().performClick()
        rule.onNode(hasText("저장하기").and(hasClickAction())).performClick()
        waitFor(androidx.compose.ui.test.hasContentDescription("콩이 · 하루 1번 · 1알", substring = true))
        rule.onNode(androidx.compose.ui.test.hasContentDescription("· 고치기", substring = true)).performClick()
        waitFor(hasText("이 약 그만 먹이기"))
        rule.onNode(hasText("이 약 그만 먹이기")).performScrollTo().performClick() // 내용 맨 아래(고정 칸 밖)
        waitFor(hasText("프레드니솔론을 그만 먹일까요?"))
        rule.onNode(hasText("그만 먹이기").and(hasClickAction())).performClick()
        waitFor(hasText("아직 먹이는 약이 없어요. 약을 추가하면 일기 탕약 줄과 알림에 나와요."))
    }
}

/** 약 고치기·그만 먹이기(plan.md 8-2b). iOS MedChangeTests와 같은 경우. */
class MedChangeTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private suspend fun all() = dao.medSchedules()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private suspend fun make(freq: MedFrequency, pets: List<String>, now: Long) = addSchedules(
        dao,
        ScheduleFields(
            petIds = pets, medicationId = newId(), frequency = freq, everyDays = "2", startDate = LocalDate.of(2026, 10, 27),
            times = listOf("08:00", "21:00"), everyHours = "8", firstAt = at(2026, 10, 27, 8),
        ),
        DoseUnit.pill, now,
    )

    @Test fun noChangeSameRowAndNewRow() = runBlocking {
        val t0 = at(2026, 10, 27, 7)
        val kong = newId()
        val bori = newId()
        val rows = make(MedFrequency.everyNDays, listOf(kong, bori), t0)
        var s = rows[0]
        val original = app.okche.ui.med.scheduleFieldsOf(s, t0)
        assertEquals(app.okche.ui.med.ChangeResult.None, app.okche.ui.med.changeSchedule(dao, s, original, original, at(2026, 10, 27, 9)))
        val reminder = original.copy(reminder = false, howTo = HowTo.inFood, leadMinutes = 30)
        assertEquals(app.okche.ui.med.ChangeResult.SameRow, app.okche.ui.med.changeSchedule(dao, s, original, reminder, at(2026, 10, 27, 9))) // 그 줄 고침
        assertEquals(2, all().size)
        s = all().first { it.id == s.id }
        assertEquals(listOf(false, true, true), listOf(s.reminder, s.howTo == HowTo.inFood, s.leadMinutes == 30))
        val times = reminder.copy(times = listOf("09:00", "21:00"))
        val t1 = at(2026, 10, 27, 10)
        assertEquals(app.okche.ui.med.ChangeResult.NewRow, app.okche.ui.med.changeSchedule(dao, s, reminder, times, t1))
        assertEquals(t1, dao.medSchedules().first { it.id == s.id }.activeUntil) // 닫음
        val next = all().first { it.activeUntil == null && it.petId == kong }
        assertEquals(s.seriesId, next.seriesId)
        assertEquals(t1, next.activeFrom)
        assertEquals(listOf("09:00", "21:00"), next.times)
        assertEquals("2026-10-27", next.startDate) // 며칠마다 셈 그대로
        assertNull(all().first { it.id == rows[1].id }.activeUntil) // 다른 아이 줄 그대로
    }

    @Test fun stopAndFirstAtPrefill() = runBlocking {
        val t0 = at(2026, 10, 27, 7)
        val hours = make(MedFrequency.everyNHours, listOf(newId()), t0)[0]
        // 기록이 없으면 옛 줄 다음 차례, 처음 정한 시간대로면 옛 firstAt + m × 새 간격
        assertEquals(at(2026, 10, 27, 16), app.okche.ui.med.firstAtPrefill(hours, 8, true, listOf(hours), emptyList(), at(2026, 10, 27, 9)))
        assertEquals(at(2026, 10, 27, 14), app.okche.ui.med.firstAtPrefill(hours, 6, false, listOf(hours), emptyList(), at(2026, 10, 27, 9)))
        val given = app.okche.store.Record(petId = hours.petId, kind = app.okche.store.RecordKind.med, occurredAt = at(2026, 10, 27, 8, 30),
            data = org.json.JSONObject().put("amount", 1).put("medicationId", hours.medicationId).put("scheduleId", hours.id).put("status", "given").put("unit", "pill").toString())
        assertEquals(at(2026, 10, 27, 12, 30), app.okche.ui.med.firstAtPrefill(hours, 4, true, listOf(hours), listOf(given), at(2026, 10, 27, 9))) // 마지막 준 기록 + 새 간격
        app.okche.ui.med.stopSchedule(dao, hours, at(2026, 10, 27, 9))
        assertEquals(at(2026, 10, 27, 9), all().single().activeUntil)
        assertTrue(app.okche.ui.med.openSeries(all()).isEmpty())
        // 그만 먹인 줄은 되살리지도, 그만 먹인 때를 덮지도 않음
        val closed = all().single()
        app.okche.ui.med.stopSchedule(dao, closed, at(2026, 10, 27, 11))
        assertEquals(at(2026, 10, 27, 9), all().single().activeUntil)
        val original = app.okche.ui.med.scheduleFieldsOf(closed, t0)
        assertEquals(app.okche.ui.med.ChangeResult.None, app.okche.ui.med.changeSchedule(dao, closed, original, original.copy(times = listOf("10:00")), at(2026, 10, 27, 11)))
        assertEquals(1, all().size)
        // 폼을 오래 열어 둬서 처음 먹일 때(08:00)가 저장한 때(09:00:30)보다 이르면 저장한 때(분 올림)로.
        assertEquals(at(2026, 10, 27, 9, 1), make(MedFrequency.everyNHours, listOf(newId()), at(2026, 10, 27, 9) + 30_000)[0].firstAt)
    }
}
