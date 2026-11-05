package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import app.okche.store.setOnboardingDone
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import org.junit.Rule
import androidx.compose.ui.test.assertCountEquals
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.ui.pet.currentPet
import app.okche.ui.pet.orderedPets
import app.okche.ui.pet.petTitle
import app.okche.ui.pet.withObjectParticle
import app.okche.ui.pet.honorific
import app.okche.ui.pet.petAge
import app.okche.ui.pet.setResting
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 호칭·나이 표기·지금 보는 아이·휴양 바로 저장(plan.md 6-3). iOS PetTitleTests와 같은 경우. */
class PetSealTest {
    private lateinit var db: OkcheDatabase
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun honorificTurnsMamaOnFirstBirthday() {
        assertEquals("아기씨", honorific("2025-11-03", day(2026, 11, 2))) // 전날
        assertEquals("마마", honorific("2025-11-03", day(2026, 11, 3))) // 당일
        assertEquals("마마", honorific("2019-10-25", day(2026, 11, 3)))
        assertEquals("아기씨", honorific(null, day(2026, 11, 3))) // 생일 없음
    }

    @Test fun leapDayBirthTurnsMamaOnFeb28() {
        assertEquals("아기씨", honorific("2024-02-29", day(2025, 2, 27)))
        assertEquals("마마", honorific("2024-02-29", day(2025, 2, 28)))
        assertEquals("1살", petAge("2024-02-29", day(2025, 2, 28)))
    }

    @Test fun ageInMonthsUnderOneYear() {
        assertEquals("0개월", petAge("2026-10-20", day(2026, 11, 3)))
        assertEquals("10개월", petAge("2025-12-03", day(2026, 11, 2)))
        assertEquals("11개월", petAge("2025-11-04", day(2026, 11, 3)))
        assertEquals("1살", petAge("2025-11-03", day(2026, 11, 3)))
        assertEquals("1개월", petAge("2026-01-31", day(2026, 2, 28))) // 달 끝
        assertNull(petAge(null, day(2026, 11, 3)))
    }

    @Test fun objectParticle() {
        assertEquals("콩이를", withObjectParticle("콩이"))
        assertEquals("똘똘을", withObjectParticle("똘똘"))
        assertEquals("Coco을(를)", withObjectParticle("Coco"))
    }

    /** 추억으로 간직한 아이는 호칭 없이 이름만, 목록 맨 뒤(6-4). */
    @Test fun memorialNameAndOrder() {
        val dubu = Pet(createdAt = 1, name = "두부", species = Species.cat, breed = "코리안숏헤어", furColor = "치즈", memorializedAt = 5)
        val kong = Pet(createdAt = 2, name = "콩이", species = Species.cat, breed = "코리안숏헤어", furColor = "치즈")
        assertEquals("두부", petTitle(dubu, day(2026, 11, 3)))
        assertEquals("콩이 아기씨", petTitle(kong, day(2026, 11, 3)))
        assertEquals(listOf("콩이", "두부"), orderedPets(listOf(dubu, kong)).map { it.name })
    }

    @Test fun currentPetFallsBackToFirst() {
        val first = Pet(createdAt = 1, name = "콩이", species = Species.cat, breed = "코리안숏헤어", furColor = "치즈")
        val second = Pet(createdAt = 2, name = "보리", species = Species.cat, breed = "코리안숏헤어", furColor = "치즈")
        assertNull(currentPet(emptyList(), null))
        assertEquals("콩이", currentPet(listOf(first, second), null)?.name) // 없으면 가장 먼저 모신 아이
        assertEquals("보리", currentPet(listOf(first, second), second.id)?.name)
        assertEquals("콩이", currentPet(listOf(first, second), "지운 아이")?.name)
    }

    @Test fun setRestingSavesRightAway() = runBlocking {
        val dao = db.dao()
        setResting(dao, "pet", true, now = 100)
        setResting(dao, "pet", true, now = 200) // 이미 열림 → 그대로
        assertEquals(listOf(100L), dao.openRestPeriods("pet").map { it.startedAt })
        setResting(dao, "pet", false, now = 300)
        assertEquals(emptyList<Long>(), dao.openRestPeriods("pet").map { it.startedAt })
        val ended = db.query("SELECT ended_at FROM rest_period", null).use { c -> c.moveToFirst(); c.getLong(0) }
        assertEquals(300L, ended)
    }
}

/** 낙관 → 아이 바꾸기 시트 → 새로 모심(바로 지금 보는 아이) → 다른 아이 고르기, 실록 제목, 더보기 휴양 스위치 (6-3). */
class PetSwitchFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun close() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    private fun register(name: String) {
        waitFor(hasSetTextAction().and(hasText("이름")))
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput(name)
        rule.onNode(hasText("모시기").and(hasClickAction())).performClick()
    }

    @Test fun switchPets() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
            context.setOnboardingDone(false)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val kongSeal = hasContentDescription("지금 보는 아이 콩이 아기씨 · 아이 바꾸기")
        waitFor(kongSeal)

        rule.onNode(kongSeal).performClick()
        waitFor(hasText("어느 아이를 볼까요?"))
        rule.onNode(hasText("+ 새 아기씨 모시기").and(hasClickAction())).performClick()
        register("보리")
        val boriSeal = hasContentDescription("지금 보는 아이 보리 아기씨 · 아이 바꾸기")
        waitFor(boriSeal)

        rule.onNode(boriSeal).performClick()
        waitFor(hasText("콩이 아기씨"))
        rule.onNode(hasText("보리 아기씨").and(isSelectable())).assertIsSelected()
        rule.onNode(hasText("콩이 아기씨").and(isSelectable())).performClick()
        waitFor(kongSeal)

        rule.onNode(tab("실록")).performClick()
        waitFor(hasText("콩이실록"))

        rule.onNode(tab("더보기")).performClick()
        val rest = hasContentDescription("콩이 아기씨 휴양 중 · 관리 쉬기")
        waitFor(rest)
        rule.onNode(rest).performClick()
        waitFor(hasText("콩이 아기씨 · 휴양 중"))
    }

    /** 먼저 둘러보기(아이 없음): 낙관 자리와 더보기에 '+ 새 아기씨 모시기'. */
    @Test fun noPetShowsRegisterButtons() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            context.setOnboardingDone(true)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasText("+ 새 아기씨 모시기"))
        rule.onNode(tab("문안")).performClick()
        waitFor(hasText("아직 모신 아이가 없어요. 아기씨를 모시면 여기에서 날마다 안부를 여쭤요."))
        assertEquals(2, rule.onAllNodes(hasText("+ 새 아기씨 모시기")).fetchSemanticsNodes().size) // 머리 알약 + 점선 버튼
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("아직 모신 아이가 없어요."))
        rule.onNode(hasText("+ 새 아기씨 모시기").and(hasClickAction())).performClick()
        register("콩이")
        waitFor(hasText("콩이 아기씨"))
    }
}

/** 아이 정보 → 추억으로 간직(문안에 추억 화면) → 추억에서 되돌리기 → 지우기(더보기로) (6-4). */
class PetMemorialFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun close() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun keepRestoreAndDelete() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
            context.setOnboardingDone(false)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("콩이 아기씨"))
        rule.onNode(hasText("콩이 아기씨").and(hasClickAction())).performClick()

        waitFor(hasText("추억으로 간직하기"))
        rule.onNode(hasText("추억으로 간직하기")).performScrollTo().performClick()
        waitFor(hasText("콩이의 기록을 추억으로 간직할게요"))
        rule.onNode(hasText("간직하기").and(hasClickAction())).performClick()
        waitFor(hasText("추억으로 간직 중", substring = true))
        rule.onNode(tab("문안")).assertIsSelected()

        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("콩이 · 추억으로 간직 중"))
        rule.onAllNodes(hasContentDescription("콩이 휴양 중 · 관리 쉬기")).assertCountEquals(0) // 추억 아이는 휴양 스위치 없음
        rule.onNode(hasText("콩이 · 추억으로 간직 중")).performClick()
        waitFor(hasText("추억에서 되돌리기"))
        rule.onNode(hasText("추억에서 되돌리기")).performScrollTo().performClick()
        waitFor(hasText("콩이를 다시 모실까요?"))
        rule.onNode(hasText("다시 모시기").and(hasClickAction())).performClick()
        waitFor(hasText("추억으로 간직하기"))

        rule.onNode(hasText("이 아이 기록 지우기")).performScrollTo().performClick()
        waitFor(hasText("콩이의 기록을 지울까요?"))
        rule.onNode(hasText("지우기").and(hasClickAction())).performClick()
        waitFor(hasText("아직 모신 아이가 없어요."))
    }
}

