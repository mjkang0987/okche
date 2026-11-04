package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
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
import app.okche.store.FoodChangeMethod
import app.okche.store.OkcheDatabase
import app.okche.store.SnackKind
import app.okche.store.newId
import app.okche.ui.food.addSnack
import app.okche.ui.food.foodFinished
import app.okche.ui.food.inFoodChange
import app.okche.ui.food.listedSnacks
import app.okche.ui.food.recordSnack
import app.okche.ui.food.removeSnack
import app.okche.ui.food.saveFoodChange
import app.okche.ui.food.snackLastGiven
import app.okche.ui.food.snackListDetail
import app.okche.ui.food.snackPickOrder
import app.okche.ui.food.updateSnack
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import app.okche.ui.record.recordValue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 다과 규칙(plan.md 7-4b). iOS SnackRulesTests와 같은 경우. */
class SnackRulesTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate.of(y, m, d).atTime(h, min).atZone(seoul).toInstant().toEpochMilli()
    private suspend fun snacks() = dao.snacksFlow().first()
    private suspend fun records() = dao.snackRecordsFlow().first()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun addEditRemoveKeepsNameForOldRecords() = runBlocking {
        val pet = newId()
        val salmon = addSnack(dao, " 연어 퓨레 ", SnackKind.lickable, false, at(2026, 10, 1, 9))
        val chicken = addSnack(dao, "동결건조 닭가슴살", SnackKind.freezeDried, true, at(2026, 10, 2, 9))
        assertEquals("연어 퓨레", salmon.name)
        var record = recordSnack(dao, salmon.id, " 반 개 ", at(2026, 10, 20, 16), null, pet, zone = seoul)
        assertEquals("""{"amount":"반 개","snackId":"${salmon.id}"}""", record.data)
        assertEquals("연어 퓨레 · 반 개", recordValue(record, snacks()))
        updateSnack(dao, salmon, "연어 퓨레 츄르", SnackKind.wet, true)
        removeSnack(dao, snacks().first { it.id == salmon.id })
        assertEquals(listOf(chicken.id), listedSnacks(snacks()).map { it.id }) // 목록에서 빠짐
        assertEquals("연어 퓨레 츄르 · 반 개", recordValue(record, snacks())) // 지난 기록 이름은 남음
        // 고치기: 양 비우면 칸 없음, 시간을 바꾸면 day도
        record = recordSnack(dao, chicken.id, "  ", at(2026, 10, 21, 5), record, pet, zone = seoul)
        assertEquals("""{"snackId":"${chicken.id}"}""", record.data)
        assertEquals("2026-10-20", record.day)
        assertEquals(1, records().size)
    }

    @Test fun listDetailCountsThisMonthForPet() = runBlocking {
        val kong = newId()
        val bori = newId()
        val salmon = addSnack(dao, "연어 퓨레", SnackKind.lickable, false)
        val ok = addSnack(dao, "저알러지 처방 간식", SnackKind.prescription, true)
        for ((pet, date) in listOf(kong to at(2026, 10, 3, 9), kong to at(2026, 10, 20, 9), bori to at(2026, 10, 20, 9), kong to at(2026, 9, 30, 9))) {
            recordSnack(dao, salmon.id, "", date, null, pet, zone = seoul)
        }
        val deleted = recordSnack(dao, salmon.id, "", at(2026, 10, 21, 9), null, kong, zone = seoul)
        dao.update(deleted.copy(deletedAt = 1))
        val now = at(2026, 10, 25, 9)
        assertEquals("짜 먹는 간식 · 이번 달 2번", snackListDetail(salmon, kong, records(), now, seoul))
        assertEquals("처방 간식 · 수라 바꾸기 중에도 괜찮아요", snackListDetail(ok, kong, records(), now, seoul))
    }

    @Test fun pickOrderRecentFirstThenAdded() = runBlocking {
        val pet = newId()
        val a = addSnack(dao, "가", SnackKind.jerky, false, at(2026, 10, 1, 9))
        val b = addSnack(dao, "나", SnackKind.jerky, false, at(2026, 10, 2, 9))
        val c = addSnack(dao, "다", SnackKind.jerky, false, at(2026, 10, 3, 9))
        recordSnack(dao, c.id, "", at(2026, 10, 24, 9), null, pet, zone = seoul)
        recordSnack(dao, b.id, "", at(2026, 10, 25, 9), null, pet, zone = seoul)
        recordSnack(dao, a.id, "", at(2026, 10, 25, 10), null, newId(), zone = seoul)
        val order = snackPickOrder(snacks(), pet, records())
        assertEquals(listOf(b.id, c.id, a.id), order.map { it.first.id })
        assertEquals(listOf("2026-10-25", "2026-10-24", null), order.map { it.second })
        assertEquals("오늘", snackLastGiven("2026-10-25", "2026-10-25"))
        assertEquals("어제", snackLastGiven("2026-10-24", "2026-10-25"))
        assertEquals("10월 2일", snackLastGiven("2026-10-02", "2026-10-25"))
    }

    @Test fun foodChangePeriod() = runBlocking {
        val pet = newId()
        assertFalse(inFoodChange(pet, "2026-10-20", emptyList()))
        val change = saveFoodChange(dao, pet, "처방식", "2026-10-01", FoodChangeMethod.gradual)
        assertTrue(inFoodChange(pet, "2026-10-14", dao.foodChanges())) // 14일째까지
        assertFalse(inFoodChange(pet, "2026-10-15", dao.foodChanges()))
        foodFinished(dao, change, "2026-10-10")
        assertTrue(inFoodChange(pet, "2026-10-09", dao.foodChanges())) // 다 바꾼 날 전날까지
        assertFalse(inFoodChange(pet, "2026-10-10", dao.foodChanges()))
    }
}

/** 수라 '+ 다과 · 간식 남기기' → 새 다과 들이고 고르기 → 기록 → 일기 목록 값 → 더보기 다과 목록 → 고치기 → 목록에서 빼기 (7-4b). */
class SnackFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun recordAndList() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasContentDescription("수라 자세히 남기기"))
        rule.onNode(hasContentDescription("수라 자세히 남기기")).performScrollTo().performClick()
        waitFor(hasText("+ 다과 · 간식 남기기"))
        rule.onNode(hasText("+ 다과 · 간식 남기기")).performScrollTo().performClick()
        waitFor(hasText("+ 새 다과 들이기")) // 다과가 없으면 들이기만
        rule.onNode(hasText("기록하기").and(hasClickAction())).performScrollTo().performClick()
        waitFor(hasText("어떤 다과를 줬는지 골라 주세요"))
        rule.onNode(hasText("+ 새 다과 들이기")).performScrollTo().performClick()
        waitFor(hasText("다과에 들이고 고르기"))
        rule.onNode(hasSetTextAction().and(hasText("간식 이름", substring = true))).performTextInput("연어 퓨레")
        rule.onNode(hasText("다과에 들이고 고르기").and(hasClickAction())).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(isSelected().and(hasText("연어 퓨레", substring = true))).fetchSemanticsNodes().isNotEmpty() } // 바로 골라짐
        rule.onNode(hasSetTextAction().and(hasText("얼마나 줬나요?", substring = true))).performTextInput("반 개")
        rule.onNode(hasText("기록하기").and(hasClickAction())).performScrollTo().performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("다과에 들이고 고르기")).fetchSemanticsNodes().isEmpty() && rule.onAllNodes(hasText("얼마나 줬나요?", substring = true)).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasContentDescription("뒤로")).performClick()
        waitFor(hasText("오늘 일기 전체"))
        rule.onNode(hasText("오늘 일기 전체")).performScrollTo().performClick()
        waitFor(hasText("연어 퓨레 · 반 개", substring = true))
        rule.onNode(hasContentDescription("뒤로")).performClick()
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("간식 목록", substring = true))
        rule.onNode(hasText("간식 목록", substring = true)).performScrollTo().performClick()
        waitFor(hasText("짜 먹는 간식 · 이번 달 1번"))
        rule.onNode(hasText("연어 퓨레")).performClick()
        waitFor(hasText("다과 고치기"))
        rule.onNode(hasText("목록에서 빼기")).performClick() // 시트 아래 고정 칸
        waitFor(hasText("연어 퓨레를 목록에서 뺄까요?"))
        rule.onNode(hasText("빼기").and(hasClickAction())).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("짜 먹는 간식 · 이번 달 1번")).fetchSemanticsNodes().isEmpty() }
    }
}
