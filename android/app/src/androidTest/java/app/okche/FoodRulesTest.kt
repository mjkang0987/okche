package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.FoodChangeMethod
import app.okche.store.OkcheDatabase
import app.okche.store.newId
import app.okche.ui.food.foodChangeAt
import app.okche.ui.food.foodCheck
import app.okche.ui.food.foodFinished
import app.okche.ui.food.foodLaterNote
import app.okche.ui.food.foodLine
import app.okche.ui.food.foodNotYet
import app.okche.ui.food.previousFoodNames
import app.okche.ui.food.saveFoodChange
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import app.okche.ui.record.assignRecord
import app.okche.ui.record.pickMealNow
import app.okche.ui.record.quickRows
import app.okche.ui.record.saveQuickRecord
import app.okche.ui.record.setMealResult
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 사료 바꾸기 규칙(plan.md 7-4a). iOS FoodRulesTests와 같은 경우. */
class FoodRulesTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate.of(y, m, d).atTime(h, min).atZone(seoul).toInstant().toEpochMilli()
    private suspend fun foods() = dao.foodsFlow().first()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun sameNameFoodIsReusedAndCurrentIsLatest() = runBlocking {
        val pet = newId()
        val a = saveFoodChange(dao, pet, " 건사료 ", "2026-10-01", FoodChangeMethod.gradual, at(2026, 10, 1, 9))
        val b = saveFoodChange(dao, pet, "가수분해 처방식", "2026-10-14", FoodChangeMethod.atOnce, at(2026, 10, 14, 9))
        val c = saveFoodChange(dao, pet, "건사료", "2026-10-14", FoodChangeMethod.gradual, at(2026, 10, 14, 10))
        assertEquals(2, foods().size) // 앞뒤 빈칸 뺀 같은 이름은 한 사료
        assertEquals(a.foodId, c.foodId)
        assertEquals("건사료", foods().first { it.id == a.foodId }.name)
        val changes = dao.foodChanges()
        assertEquals(c.id, foodChangeAt("2026-10-20", pet, changes)?.id) // 같은 날 둘이면 늦게 만든 것
        assertEquals(a.id, foodChangeAt("2026-10-05", pet, changes)?.id)
        assertNull(foodChangeAt("2026-09-30", pet, changes))
        assertEquals("가수분해 처방식 · 10/14부터", foodLine(b, foods().first { it.id == b.foodId }))
    }

    @Test fun previousNamesSkipCurrentAndDuplicates() = runBlocking {
        val pet = newId()
        saveFoodChange(dao, pet, "건사료", "2026-09-01", FoodChangeMethod.gradual, at(2026, 9, 1, 9))
        saveFoodChange(dao, pet, "연어 캔", "2026-09-10", FoodChangeMethod.gradual, at(2026, 9, 10, 9))
        saveFoodChange(dao, pet, "건사료", "2026-09-20", FoodChangeMethod.gradual, at(2026, 9, 20, 9))
        saveFoodChange(dao, pet, "가수분해 처방식", "2026-10-14", FoodChangeMethod.gradual, at(2026, 10, 14, 9))
        saveFoodChange(dao, newId(), "다른 아이 사료", "2026-10-01", FoodChangeMethod.gradual)
        assertEquals(listOf("건사료", "연어 캔"), previousFoodNames(pet, "2026-10-20", dao.foodChanges(), foods()))
    }

    @Test fun checkSteps() = runBlocking {
        val pet = newId()
        var change = saveFoodChange(dao, pet, "처방식", "2026-10-01", FoodChangeMethod.gradual)
        assertNull(foodCheck(change, "2026-10-06")) // 6일째
        assertEquals(7, foodCheck(change, "2026-10-07")?.first)
        assertEquals(9, foodCheck(change, "2026-10-09")?.second) // 다음 단계 전날까지
        foodNotYet(dao, change, 7)
        change = dao.foodChanges().first()
        assertNull(foodCheck(change, "2026-10-09"))
        assertEquals(10, foodCheck(change, "2026-10-10")?.first)
        assertEquals(14, foodCheck(change, "2026-10-14")?.first)
        assertNull(foodCheck(change, "2026-10-15")) // 14일까지 답 없으면 끝
        assertEquals("아직이면 10일·12일·14일째에 한 번 더 물어볼게요.", foodLaterNote(7))
        assertNull(foodLaterNote(14))
        foodFinished(dao, change, "2026-10-12")
        change = dao.foodChanges().first()
        assertEquals("2026-10-12", change.finishedOn)
        assertNull(foodCheck(change, "2026-10-12"))
    }

    @Test fun mealRecordsCarryFoodOfThatDay() = runBlocking {
        val pet = newId()
        val old = saveFoodChange(dao, pet, "건사료", "2026-10-01", FoodChangeMethod.gradual)
        val new = saveFoodChange(dao, pet, "처방식", "2026-10-14", FoodChangeMethod.gradual)
        val row = quickRows.first { it.kind == app.okche.store.RecordKind.meal }
        val before = saveQuickRecord(dao, row, "all", pet, at(2026, 10, 14, 5), seoul) // 6시 전 = 13일
        val after = pickMealNow(dao, "some", pet, at(2026, 10, 14, 8), seoul)
        assertEquals(old.foodId, JSONObject(before.data).getString("foodId"))
        assertEquals(new.foodId, JSONObject(after.data).getString("foodId"))
        setMealResult(dao, after, "all")
        val edited = dao.dayRecordsFlow("2026-10-14").first().first { it.id == after.id }
        assertEquals(new.foodId, JSONObject(edited.data).getString("foodId")) // 고쳐도 그대로
        val noFood = saveQuickRecord(dao, row, "all", null, at(2026, 10, 14, 9), seoul)
        assertFalse(JSONObject(noFood.data).has("foodId"))
        assignRecord(dao, noFood, pet) // 누구였는지 정하면 그 아이가 그날 먹던 사료
        val assigned = JSONObject(dao.dayRecordsFlow("2026-10-14").first().first { it.id == noFood.id }.data)
        assertEquals(new.foodId, assigned.getString("foodId"))
        assertEquals("all", assigned.getString("result"))
    }
}

/** 더보기 '사료 · 처방식' → 사료 바꾸기 시트(이름 없으면 알림) → 바꾸기 → 아이 정보 '지금 먹는 사료' 줄 (7-4a). */
class FoodFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun changeFoodFromMore() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("사료 · 처방식"))
        rule.onNode(hasText("사료 · 처방식")).performScrollTo().performClick()
        waitFor(hasText("콩이 · 사료 바꾸기"))
        rule.onNode(hasText("바꾸기").and(hasClickAction())).assertIsNotEnabled() // 덜 채우면 흐리게 + 남은 것
        waitFor(hasText("사료 이름을 넣으면 바꿀 수 있어요"))
        rule.onNode(hasSetTextAction()).performTextInput("가수분해 처방식")
        rule.onNode(hasText("바꾸기").and(hasClickAction())).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("콩이 · 사료 바꾸기")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(hasText("콩이 아기씨").and(hasClickAction())).performClick()
        waitFor(hasText("가수분해 처방식 · ", substring = true))
    }
}
