package app.okche

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import org.junit.Rule
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.FixedIds
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.OkcheDatabase
import app.okche.ui.record.currentMeal
import app.okche.ui.record.deleteRecord
import app.okche.ui.record.fillFeeder
import app.okche.ui.record.leftGrams
import app.okche.ui.record.mealNames
import app.okche.ui.record.mealOccurrences
import app.okche.ui.record.namedSlots
import app.okche.ui.record.openMealPlan
import app.okche.ui.record.pickMeal
import app.okche.ui.record.quickRows
import app.okche.ui.record.recordValue
import app.okche.ui.record.saveMealPlan
import app.okche.ui.record.saveQuickRecord
import app.okche.ui.record.setLeftGrams
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 끼니 구간·끼니 시간 바꾸기·급식기 자동 기록(plan.md 7-3). iOS MealRulesTests와 같은 경우. */
class MealRulesTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private val pet = "pet"
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate.of(y, m, d).atTime(h, min).atZone(seoul).toInstant().toEpochMilli()
    private suspend fun plan(mode: MealMode, times: List<String>, from: Long): MealPlan {
        saveMealPlan(dao, pet, mode, times.map { MealSlot("", it) }, from)
        return checkNotNull(openMealPlan(dao.mealPlans(pet)))
    }
    private fun meals() = db.query("SELECT occurred_at, auto, data, id, updated_at FROM record WHERE kind = 'meal' ORDER BY occurred_at", null).use { c ->
        List(c.count) { c.moveToNext(); listOf(c.getLong(0), c.getInt(1), c.getString(2), c.getString(3), c.getLong(4)) }
    }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun names() {
        assertEquals(listOf("아침"), mealNames(1))
        assertEquals(listOf("아침", "저녁"), mealNames(2))
        assertEquals(listOf("아침", "점심", "저녁", "끼니 4"), mealNames(4))
        assertEquals(listOf(MealSlot("아침", "08:00", 40), MealSlot("저녁", "18:00")), namedSlots(listOf(MealSlot("", "18:00"), MealSlot("", "08:00", 40))))
    }

    /** 구간: 자기 시간 1시간 전 ~ 다음 끼니 1시간 전. */
    @Test fun currentWindow() = runBlocking {
        val p = plan(MealMode.direct, listOf("08:00", "18:00"), at(2026, 10, 1, 0))
        assertEquals("아침", currentMeal(p, at(2026, 10, 25, 7), seoul)?.slot?.name)
        assertEquals("아침", currentMeal(p, at(2026, 10, 25, 16, 59), seoul)?.slot?.name)
        val evening = currentMeal(p, at(2026, 10, 25, 17), seoul)
        assertEquals("저녁", evening?.slot?.name)
        assertEquals("${p.id}|2026-10-25|18:00", evening?.key)
        assertEquals("${p.id}|2026-10-25|18:00", currentMeal(p, at(2026, 10, 26, 3), seoul)?.key) // 새벽은 어제 저녁
    }

    @Test fun quickMealGoesToSlotThenExtra() = runBlocking {
        val row = quickRows[3]
        assertNull(saveQuickRecord(dao, row, "all", pet, at(2026, 10, 25, 8), seoul).slotKey) // 끼니 없음
        saveMealPlan(dao, pet, MealMode.direct, listOf(MealSlot("", "08:00", 40), MealSlot("", "18:00")), at(2026, 10, 25, 6)) // 아침 전에 정함
        val first = saveQuickRecord(dao, row, "some", pet, at(2026, 10, 25, 9, 30), seoul)
        assertNotNull(first.slotKey)
        assertEquals("""{"givenGrams":40,"result":"some","slotName":"아침"}""", first.data)
        assertNull(saveQuickRecord(dao, row, "all", pet, at(2026, 10, 25, 10), seoul).slotKey) // 추가 식사
        assertEquals("남김", recordValue(first))
    }

    @Test fun savePlanClosesAndReplaces() = runBlocking {
        plan(MealMode.direct, listOf("08:00"), at(2026, 10, 1, 9))
        val new = plan(MealMode.feeder, listOf("19:00", "07:00"), at(2026, 10, 2, 9))
        assertEquals(listOf(at(2026, 10, 2, 9)), dao.mealPlans(pet).mapNotNull { it.activeUntil })
        assertEquals(MealMode.feeder, new.mode)
        assertEquals(listOf("아침", "저녁"), new.slots.map { it.name })
        saveMealPlan(dao, pet, MealMode.direct, emptyList(), at(2026, 10, 3, 9))
        assertNull(openMealPlan(dao.mealPlans(pet)))
    }

    @Test fun pickAndLeftGrams() = runBlocking {
        val p = plan(MealMode.direct, listOf("08:00"), at(2026, 10, 1, 9))
        val slot = mealOccurrences(p, "2026-10-25", seoul).first()
        val record = setLeftGrams(dao, pickMeal(dao, "some", slot, pet, at(2026, 10, 25, 8, 10), seoul), 8)
        assertEquals(8, leftGrams(record))
        val again = pickMeal(dao, "all", slot, pet, at(2026, 10, 25, 9), seoul)
        assertEquals(record.id, again.id)
        assertEquals("""{"result":"all","slotName":"아침"}""", again.data)
        assertEquals(1, meals().size)
    }

    @Test fun occurrencesBeforeSixGoToNextCalendarDay() = runBlocking {
        val p = plan(MealMode.direct, listOf("02:00", "08:00"), at(2026, 10, 1, 9))
        val list = mealOccurrences(p, "2026-10-25", seoul)
        assertEquals(listOf(at(2026, 10, 25, 8), at(2026, 10, 26, 2)), list.map { it.at })
        assertEquals(setOf("2026-10-25"), list.map { it.day }.toSet())
    }

    /** 그날 끼니를 바꿔도 지난 끼니는 앞 계획의 차례로 남는다(두 번 기록 안 됨 — 리뷰 1회차). */
    @Test fun planChangeMidDayKeepsPastSlot() = runBlocking {
        val old = plan(MealMode.direct, listOf("08:00", "18:00"), at(2026, 10, 1, 9))
        val morning = mealOccurrences(old, "2026-10-25", seoul).first()
        pickMeal(dao, "all", morning, pet, at(2026, 10, 25, 8, 10), seoul)
        plan(MealMode.direct, listOf("09:00", "19:00"), at(2026, 10, 25, 10))
        val today = mealOccurrences(dao.mealPlans(pet), "2026-10-25", seoul)
        assertEquals(listOf("08:00", "19:00"), today.map { it.slot.time })
        assertEquals(morning.key, today[0].key)
        assertNull(saveQuickRecord(dao, quickRows[3], "all", pet, at(2026, 10, 25, 10, 30), seoul).slotKey) // 추가 식사
    }

    /** 급식기 차례를 손으로 남기면 자동 기록과 같은 고정 ID, 자동 기록을 고치면 auto 꺼짐. */
    @Test fun feederManualPickUsesFixedId() = runBlocking {
        val p = plan(MealMode.feeder, listOf("07:00", "19:00"), at(2026, 10, 24, 12))
        val evening = mealOccurrences(p, "2026-10-25", seoul).last()
        assertEquals(FixedIds.feederRecord(evening.key), pickMeal(dao, "some", evening, pet, at(2026, 10, 25, 18, 50), seoul).id)
        fillFeeder(dao, at(2026, 10, 26, 8), seoul)
        val morning = mealOccurrences(p, "2026-10-25", seoul)[0]
        assertTrue(checkNotNull(dao.recordBySlot(morning.key)).auto)
        assertEquals(false, pickMeal(dao, "none", morning, pet, at(2026, 10, 26, 9), seoul).auto)
    }

    /** 급식기: 빠진 날까지 채우고, 고정 ID라 한 번만, 지운 것은 되살리지 않고, 계획 전·지금 뒤 끼니는 안 만듦. */
    @Test fun feederFill() = runBlocking {
        val p = plan(MealMode.feeder, listOf("07:00", "19:00"), at(2026, 10, 24, 12))
        assertEquals(4, fillFeeder(dao, at(2026, 10, 26, 8), seoul))
        val made = meals()
        assertEquals(listOf(at(2026, 10, 24, 19), at(2026, 10, 25, 7), at(2026, 10, 25, 19), at(2026, 10, 26, 7)), made.map { it[0] })
        assertEquals(1, made[0][1]) // auto
        assertEquals(at(2026, 10, 24, 19), made[0][4]) // updatedAt = 끼니 예정 시각
        assertEquals(FixedIds.feederRecord("${p.id}|2026-10-24|19:00"), made[0][3])
        assertEquals("""{"result":"all","slotName":"저녁"}""", made[0][2])
        assertEquals(0, fillFeeder(dao, at(2026, 10, 26, 8), seoul)) // 두 번 안 만듦
        val first = checkNotNull(dao.recordBySlot("${p.id}|2026-10-24|19:00"))
        deleteRecord(dao, first)
        assertEquals(0, fillFeeder(dao, at(2026, 10, 26, 8), seoul)) // 지운 것 안 되살림
        saveMealPlan(dao, pet, MealMode.direct, emptyList(), at(2026, 10, 26, 10))
        assertEquals(0, fillFeeder(dao, at(2026, 10, 27, 20), seoul)) // 닫힌 뒤로는 안 만듦
        assertTrue(meals().size == 4)
    }

    /** 고정 ID는 iOS와 같은 값(1-5) — iOS MealRulesTests와 같은 글자. */
    @Test fun feederIdIsLowercaseNameBased() {
        assertEquals("759216b2-dbd6-38f6-80c0-02009c1eba67", FixedIds.feederRecord("p|2026-10-25|07:00"))
    }
}

/** 수라 ⋯ → 끼니 없음(지금 먹은 것·오늘 남긴 수라) → 끼니 시간 정하기 → 끼니 칸 남김 → 급식기로 바꾸기 (7-3). iOS testSuraNoPlanThenPlanThenFeeder와 같은 흐름. */
class SuraFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test fun noPlanThenPlanThenFeeder() {
        // 아이 ID가 매번 새로라 '괜찮아요'를 누른 기록은 남아 있지 않다.
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasContentDescription("수라 자세히 남기기"))
        rule.onNode(hasContentDescription("수라 자세히 남기기")).performScrollTo().performClick()
        waitFor(hasText("끼니 시간을 정해 둘까요?"))
        rule.onNode(hasContentDescription("지금 남김")).performClick()
        waitFor(hasText("오늘 남긴 수라"))
        rule.onNode(hasText("괜찮아요")).performClick()
        rule.waitUntil(3_000) { rule.onAllNodes(hasText("끼니 시간을 정해 둘까요?")).fetchSemanticsNodes().isEmpty() }

        rule.onNode(hasText("끼니 시간 정하기 ›")).performScrollTo().performClick()
        waitFor(hasText("저장하기"))
        rule.onNode(hasText("저장하기")).performScrollTo().performClick()
        waitFor(hasContentDescription("끼니 시간 바꾸기"))
        // 오늘 정한 끼니는 아직 지나지 않은 것만 칸이 생긴다(18시 뒤엔 없음) — 있으면 남김까지.
        val some = rule.onAllNodes(hasText("남김").and(isSelectable())).fetchSemanticsNodes()
        if (some.isNotEmpty()) {
            rule.onAllNodes(hasText("남김").and(isSelectable()))[0].performClick()
            waitFor(hasContentDescription("남은 양 (그램, 선택)", substring = true))
        } else {
            rule.onNode(hasText("오늘은 남은 끼니가 없어요. 내일부터 끼니마다 남겨요.")).assertExists()
        }

        rule.onNode(hasText("급식기 (수라간)")).performClick()
        waitFor(hasContentDescription("급식기 시간 바꾸기"))
    }
}
