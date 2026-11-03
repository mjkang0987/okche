package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.test.espresso.Espresso
import app.okche.store.setOnboardingDone
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.InsulinPlan
import app.okche.store.InsulinSlot
import app.okche.store.OkcheDatabase
import app.okche.store.RestPeriod
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import app.okche.ui.pet.InsulinRow
import app.okche.ui.pet.InsulinSetupForm
import app.okche.ui.pet.PetEditForm
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.companionDays
import app.okche.ui.pet.registerPet
import app.okche.ui.pet.saveInsulinSetup
import app.okche.ui.pet.savePetEdit
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

/** 아이 정보 고치기·인슐린 설정 저장 규칙(plan.md 6-2, iOS PetEditingTests와 같은 경우). */
class PetEditTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val created = 1_793_000_000_000L
    private val now = created + 3 * 86_400_000L
    private val createdDay = LocalDate.of(2026, 10, 28)
    private val today = LocalDate.of(2026, 10, 31)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun makePet(diabetes: Boolean = false, age: String? = null): String = runBlocking {
        val form = PetForm(
            name = "콩이", birthUnknown = age != null, age = age.orEmpty(), hasDiabetes = diabetes,
            insulinRows = listOf(InsulinRow("08:00", "2"), InsulinRow("20:00", "2")),
        )
        assertEquals(emptyMap<String, String>(), registerPet(dao, form, created, createdDay))
        dao.pets().last().id
    }

    private fun load(id: String) = runBlocking { checkNotNull(PetEditForm.load(dao, id, today)) }
    private fun save(form: PetEditForm, at: Long = now) = runBlocking { savePetEdit(dao, form, at, today) }
    private fun pet(id: String) = runBlocking { checkNotNull(dao.pet(id)) }
    private fun plans() = runBlocking { dao.insulinPlans() }
    private fun rests() = db.query("SELECT ended_at FROM rest_period", null).use { c ->
        List(c.count) { c.moveToPosition(it); if (c.isNull(0)) null else c.getLong(0) }
    }

    @Test fun blankNameChangesNothing() {
        val id = makePet()
        assertEquals(mapOf("name" to "이름을 넣어 주세요"), save(load(id).copy(name = "  ", breed = "샴")))
        assertEquals(listOf("콩이", "코리안숏헤어", created), pet(id).let { listOf(it.name, it.breed, it.updatedAt) })
    }

    @Test fun savesChangedFieldsOnly() {
        val id = makePet()
        assertEquals(emptyMap<String, String>(), save(load(id)))
        assertEquals(created, pet(id).updatedAt) // 아무것도 안 바꾸면 그대로

        val form = load(id).withSpecies(Species.dog)
            .copy(name = " 보리 ", sex = Sex.male, familyDate = LocalDate.of(2023, 5, 26), diagnosis = " 신부전 ")
        assertEquals(emptyMap<String, String>(), save(form))
        val pet = pet(id)
        assertEquals("보리", pet.name)
        assertEquals(Species.dog, pet.species)
        assertEquals(listOf("말티즈", "흰색"), listOf(pet.breed, pet.furColor))
        assertEquals(Sex.male, pet.sex)
        assertEquals("2023-05-26", pet.familyDate)
        assertEquals("신부전", pet.diagnosis)
        assertEquals(now, pet.updatedAt)
    }

    @Test fun futureDatesAreErrors() {
        val id = makePet()
        val tomorrow = today.plusDays(1)
        assertEquals(
            mapOf("birthDate" to "오늘보다 뒤 날짜는 넣을 수 없어요", "familyDate" to "오늘보다 뒤 날짜는 넣을 수 없어요"),
            save(load(id).copy(birthDate = tomorrow, familyDate = tomorrow)),
        )
    }

    /** 어림잡은 생일은 나이를 안 고치면 그대로 — 저장할 때마다 오늘 쪽으로 밀리지 않는다. */
    @Test fun estimatedBirthKeptUntilAgeChanges() {
        val id = makePet(age = "3")
        assertEquals("2023-10-28", pet(id).birthDate)
        val form = load(id)
        assertTrue(form.birthUnknown)
        assertEquals("3", form.age)
        save(form.copy(name = "보리"))
        assertEquals("2023-10-28", pet(id).birthDate)
        assertTrue(pet(id).birthEstimated)

        save(load(id).copy(age = "5"))
        assertEquals("2021-10-31", pet(id).birthDate)

        // 생일을 알게 되면 어림잡음 표시를 끈다.
        save(load(id).copy(birthUnknown = false, birthDate = LocalDate.of(2021, 3, 1)))
        assertEquals("2021-03-01", pet(id).birthDate)
        assertFalse(pet(id).birthEstimated)
    }

    /** 30살로 어림잡은 아이가 한 해 지나 31살로 보여도, 나이를 안 고치면 그대로 저장된다. */
    @Test fun unchangedEstimatedAgeOver30Saves() = runBlocking {
        val id = makePet(age = "30")
        val nextYear = today.plusYears(1)
        val form = checkNotNull(PetEditForm.load(dao, id, nextYear))
        assertEquals("31", form.age)
        assertEquals(emptyMap<String, String>(), savePetEdit(dao, form.copy(name = "보리"), now, nextYear))
        assertEquals(listOf("1996-10-28", "보리"), pet(id).let { listOf(it.birthDate, it.name) })
    }

    @Test fun diabetesOffClosesOpenPlans() {
        val id = makePet(diabetes = true)
        assertEquals(emptyMap<String, String>(), save(load(id).copy(hasDiabetes = false)))
        assertFalse(pet(id).hasDiabetes)
        assertEquals(listOf(now), plans().map { it.activeUntil })
    }

    @Test fun diabetesOnNeedsPlan() {
        val id = makePet()
        assertEquals(mapOf("insulinPlan" to "인슐린 시간과 양을 먼저 설정해 주세요"), save(load(id).copy(hasDiabetes = true)))
        assertFalse(pet(id).hasDiabetes)
    }

    @Test fun restPeriods() = runBlocking {
        val id = makePet()
        save(load(id).copy(resting = true))
        save(load(id), now + 60_000) // 이미 휴양 중이면 새 줄 없음
        assertEquals(listOf<Long?>(null), rests())
        assertTrue(load(id).resting)

        // 두 기기 백업을 합쳐 열린 기간이 둘이어도 끄면 모두 닫힌다.
        dao.insert(RestPeriod(createdAt = now, petId = id, startedAt = now - 3_600_000))
        val later = now + 3_600_000
        save(load(id).copy(resting = false), later)
        assertEquals(listOf<Long?>(later, later), rests())
        assertFalse(load(id).resting)
    }

    @Test fun companionDaysCountsFirstDay() {
        assertEquals(1L, companionDays("2026-10-31", today))
        assertEquals(31L, companionDays("2026-10-01", today))
        assertNull(companionDays(null, today))
        assertNull(companionDays("2026-11-01", today))
    }

    // 인슐린 설정

    @Test fun insulinSetupLoadsOpenPlan() = runBlocking {
        val id = makePet(diabetes = true)
        val form = InsulinSetupForm.load(dao, id)
        assertEquals(listOf(InsulinRow("08:00", "2"), InsulinRow("20:00", "2")), form.rows)
        assertTrue(form.ringDuringQuiet) // 아이 등록도 밤에도 울리기 켜짐
        assertEquals(10, form.leadMinutes)
    }

    @Test fun changingTimesMakesNewPlan() = runBlocking {
        val id = makePet(diabetes = true)
        val form = InsulinSetupForm.load(dao, id)
        val rows = form.rows.toMutableList().also { it[1] = InsulinRow("21:00", "2.5") }
        assertEquals(emptyMap<String, String>(), saveInsulinSetup(dao, form.copy(rows = rows), now))
        val plans = plans().sortedBy { it.activeFrom }
        assertEquals(listOf(now, null), plans.map { it.activeUntil })
        assertEquals(now, plans[1].activeFrom)
        assertEquals(listOf(InsulinSlot("08:00", 2.0), InsulinSlot("21:00", 2.5)), plans[1].slots)
    }

    @Test fun changingNameEditsSamePlan() = runBlocking {
        val id = makePet(diabetes = true)
        val form = InsulinSetupForm.load(dao, id)
        saveInsulinSetup(dao, form, now) // 안 바뀌면 그대로
        assertEquals(listOf(created), plans().map { it.updatedAt })
        saveInsulinSetup(dao, form.copy(name = " 란투스 ", syringe = Syringe.u100, leadMinutes = 30, ringDuringQuiet = false), now)
        val plan = plans().single()
        assertEquals("란투스", plan.name)
        assertEquals(Syringe.u100, plan.syringe)
        assertEquals(30, plan.leadMinutes)
        assertFalse(plan.ringDuringQuiet)
        assertEquals(now, plan.updatedAt)
        assertNull(plan.activeUntil)
    }

    @Test fun twoOpenPlansBothCloseOnChange() = runBlocking {
        val id = makePet(diabetes = true)
        dao.insert(InsulinPlan(createdAt = created, petId = id, slots = listOf(InsulinSlot("09:00", 1.0)), activeFrom = created + 60_000))
        val form = InsulinSetupForm.load(dao, id)
        assertEquals(listOf("09:00"), form.rows.map { it.time }) // activeFrom이 늦은 것
        saveInsulinSetup(dao, form.copy(rows = listOf(InsulinRow("09:00", "1.5"))), now)
        val plans = plans()
        assertEquals(1, plans.count { it.activeUntil == null })
        assertEquals(2, plans.count { it.activeUntil == now })
    }

    /** 열린 계획이 둘이고 이름만 바꾸면 고른 계획(늦은 activeFrom)만 고치고 둘 다 열린 채(데이터 7장). */
    @Test fun twoOpenPlansNameOnlyEditsChosen() = runBlocking {
        val id = makePet(diabetes = true)
        dao.insert(InsulinPlan(createdAt = created, petId = id, slots = listOf(InsulinSlot("09:00", 1.0)), activeFrom = created + 60_000))
        saveInsulinSetup(dao, InsulinSetupForm.load(dao, id).copy(name = "란투스"), now)
        val plans = plans()
        assertEquals(2, plans.count { it.activeUntil == null })
        assertEquals(listOf(listOf(InsulinSlot("09:00", 1.0))), plans.filter { it.name == "란투스" }.map { it.slots })
    }

    /** 이미 고른 종을 다시 골라도 품종·털색은 그대로(아이 등록·고치기 둘 다). */
    @Test fun reselectingSameSpeciesKeepsBreed() {
        val edit = load(makePet()).copy(breed = "페르시안", furColor = "고등어").withSpecies(Species.cat)
        assertEquals("페르시안" to "고등어", edit.breed to edit.furColor)
        val register = PetForm(breed = "페르시안", furColor = "고등어").withSpecies(Species.cat)
        assertEquals("페르시안" to "고등어", register.breed to register.furColor)
    }

    @Test fun insulinSetupMakesPetDiabeticAndSetsNotify() = runBlocking {
        val id = makePet()
        val form = InsulinSetupForm.load(dao, id)
        assertTrue(form.notify)
        assertEquals(mapOf("units0" to "양을 넣어 주세요"), saveInsulinSetup(dao, form.copy(rows = listOf(InsulinRow("08:00"))), now))
        assertFalse(pet(id).hasDiabetes)
        assertEquals(0, plans().size)

        assertEquals(emptyMap<String, String>(), saveInsulinSetup(dao, form.copy(rows = listOf(InsulinRow("08:00", "3")), notify = false), now))
        assertTrue(pet(id).hasDiabetes)
        assertEquals(now, pet(id).updatedAt)
        assertEquals(1, plans().size)
        assertFalse(dao.loadOrCreateSetting().notify.insulinTime)
        // 이제 당뇨를 켠 채 아이 정보를 저장할 수 있다.
        val edit = load(id)
        assertTrue(edit.hasDiabetes)
        assertEquals(emptyMap<String, String>(), save(edit.copy(name = "보리")))
    }
}

/** 더보기 임시 줄 → 아이 정보 고치기 → 인슐린 설정 → 저장 (앱 저장소를 비우고 시작). */
class PetEditFlowTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clearStore() = runBlocking {
        OkcheDatabase.get(context).clearAllTables()
        registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        Unit
    }

    @After fun close() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test fun editFromMore() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val tabMore = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText("더보기"))
        waitFor(tabMore)
        rule.onNode(tabMore).performClick()
        waitFor(hasText("콩이 아기씨"))
        rule.onNodeWithText("콩이 아기씨").performClick()
        waitFor(hasSetTextAction().and(hasText("이름")))
        // performTextInput은 커서(처음엔 맨 앞) 자리에 넣어서 바꾸기로.
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextReplacement("콩이이")

        // 당뇨를 켜고 인슐린 없이 저장하면 오류
        rule.onNodeWithText("당뇨가 있어요").performScrollTo().performClick()
        rule.onNodeWithText("저장하기").performScrollTo().performClick()
        waitFor(hasText("인슐린 시간과 양을 먼저 설정해 주세요"))

        rule.onNodeWithContentDescription("인슐린 이름·양·시간 설정").performScrollTo().performClick()
        waitFor(hasSetTextAction().and(hasContentDescription("첫 번째 양 (단위)")))
        rule.onNode(hasSetTextAction().and(hasContentDescription("첫 번째 양 (단위)"))).performTextInput("2")
        rule.onNode(hasSetTextAction().and(hasContentDescription("두 번째 양 (단위)"))).performTextInput("2")
        // 알림 보낼 때 고르기(펼침 메뉴)
        rule.onNodeWithContentDescription("알림 보낼 때").performScrollTo().performClick()
        waitFor(hasText("30분 전"))
        rule.onNodeWithText("30분 전").performClick()
        rule.onNodeWithText("저장하기").performScrollTo().performClick()

        // 아이 정보로 돌아오면 고치던 이름이 남아 있고, 저장하면 더보기 줄 이름이 바뀐다.
        waitFor(hasText("콩이이"))
        rule.onAllNodes(hasText("인슐린 시간과 양을 먼저 설정해 주세요")).assertCountEquals(0)
        rule.onNodeWithText("저장하기").performScrollTo().performClick()
        waitFor(hasText("콩이이 아기씨"))
        val pet = runBlocking { OkcheDatabase.get(context).dao().pets().single() }
        assertEquals(listOf("콩이이", "true"), listOf(pet.name, pet.hasDiabetes.toString()))
        assertEquals(30, runBlocking { OkcheDatabase.get(context).dao().openInsulinPlans(pet.id) }.single().leadMinutes)
    }

    /** 처음 실행에서 등록(아래로 밀어 '모시기') → 더보기 → 아이 정보: 위에서 시작하고, 뒤로 가면 더보기로 돌아온다. */
    @Test fun editAfterRegisterStartsAtTopAndBackReturnsToMore() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            context.setOnboardingDone(false)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasText("아이 등록하고 시작하기"))
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        waitFor(hasSetTextAction().and(hasText("이름")))
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput("콩이")
        rule.onNode(hasText("모시기").and(hasClickAction())).performScrollTo().performClick()
        val tabMore = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText("더보기"))
        waitFor(tabMore)
        rule.onNode(tabMore).performClick()
        waitFor(hasText("콩이 아기씨"))
        rule.onNodeWithText("콩이 아기씨").performClick()
        waitFor(hasSetTextAction().and(hasText("콩이")))
        rule.onNode(hasSetTextAction().and(hasText("콩이"))).assertIsDisplayed()

        // 뒤로 → 앱이 닫히지 않고 더보기로(아이 정보 머리 '아이 정보'가 사라지고 탭이 보임).
        Espresso.pressBackUnconditionally()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("아이 정보")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(tabMore).assertIsDisplayed()
        assertEquals(Lifecycle.State.RESUMED, scenario!!.state)
    }
}
