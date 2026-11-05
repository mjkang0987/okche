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
import app.okche.store.DoseUnit
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.Species
import app.okche.store.newId
import app.okche.ui.med.MedFields
import app.okche.ui.med.addMed
import app.okche.ui.med.groupedMeds
import app.okche.ui.med.medBlockedMessage
import app.okche.ui.med.medDetail
import app.okche.ui.med.medErrors
import app.okche.ui.med.medFormOf
import app.okche.ui.med.medNumber
import app.okche.ui.med.medRemaining
import app.okche.ui.med.medTakers
import app.okche.ui.med.removeMed
import app.okche.ui.med.unitOf
import app.okche.ui.med.unitTitle
import app.okche.ui.med.updateMed
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 약방 규칙(plan.md 8-1). iOS YakbangTests와 같은 경우. */
class YakbangTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val t0 = 1_790_000_000_000L
    private suspend fun meds() = dao.medicationsFlow().first()
    private fun fields(name: String, form: MedForm = MedForm.pill, dose: String = "1", stock: String = "") = MedFields(name = name, form = form, dose = dose, stock = stock)
    private fun medRecord(med: Medication, status: String, amount: Double, unit: String = med.unit.name, at: Long, deleted: Long? = null) =
        Record(petId = newId(), kind = RecordKind.med, occurredAt = at, deletedAt = deleted,
            data = JSONObject().put("amount", amount).put("medicationId", med.id).put("status", status).put("unit", unit).toString())

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun unitsFollowFormAndNumbers() {
        assertEquals(listOf("알", "포", "ml", "방울", "ml"), MedForm.entries.map { unitTitle(unitOf(it)) })
        assertEquals(listOf("1", "0.5", "24"), listOf(1.0, 0.5, 24.0).map(::medNumber))
    }

    @Test fun errors() {
        assertEquals(mapOf("name" to "약 이름을 넣어 주세요", "dose" to "한 번에 줄 양을 넣어 주세요"), medErrors(fields("  ", dose = "")))
        assertEquals(mapOf("dose" to "0보다 큰 숫자로 넣어 주세요"), medErrors(fields("약", dose = "0")))
        assertEquals(mapOf("stock" to "0 이상 숫자로 넣어 주세요"), medErrors(fields("약", dose = "0,5", stock = "-1")))
        assertEquals(mapOf("shortName" to "6자까지 넣어 주세요"), medErrors(fields("프레드니솔론").copy(shortName = "일곱글자짧은이")))
        assertTrue(medErrors(fields("약", dose = "0.5", stock = "0")).isEmpty())
    }

    @Test fun remainingSubtractsGivenAndVomitedSameUnitAfterCount() = runBlocking {
        val med = addMed(dao, fields(" 프레드니솔론 ", stock = "24").copy(shortName = "  "), t0)
        assertEquals("프레드니솔론", med.name)
        assertNull(med.shortName)
        assertEquals(DoseUnit.pill, med.unit)
        assertEquals(t0, med.stockCountedAt)
        val records = listOf(
            medRecord(med, "given", 1.0, at = t0 + 60_000),
            medRecord(med, "vomited", 0.5, at = t0 + 120_000), // 토했어도 약은 씀
            medRecord(med, "skipped", 1.0, at = t0 + 180_000),
            medRecord(med, "given", 5.0, unit = "ml", at = t0 + 240_000), // 단위가 다르면 안 뺌
            medRecord(med, "given", 3.0, at = t0 - 60_000), // 적기 전
            medRecord(med, "given", 2.0, at = t0 + 300_000, deleted = 1), // 지우면 돌아옴
        )
        assertEquals(22.5, medRemaining(med, records)!!, 0.0001)
        assertEquals("알약 · 1알씩 · 남은 22.5알", medDetail(med, records))
        val syrup = addMed(dao, fields("위장약 시럽", form = MedForm.liquid))
        assertEquals("물약 · 1ml씩 · 남은 양 안 적음", medDetail(syrup, records))
    }

    @Test fun updateOnlyWhenChangedAndStockResets() = runBlocking {
        var med = addMed(dao, fields("유산균", form = MedForm.powder, stock = "30"), t0)
        val records = listOf(medRecord(med, "given", 1.0, at = t0 + 60_000))
        val original = medFormOf(med, records)
        assertEquals("29", original.stock) // 계산한 지금 남은 양
        assertFalse(updateMed(dao, med, original, original, t0 + 500_000))
        assertEquals(t0, meds().single().updatedAt)
        val memo = original.copy(memo = "아침에")
        assertTrue(updateMed(dao, med, original, memo, t0 + 600_000))
        med = meds().single()
        assertEquals(30.0, med.stockAmount!!, 0.0) // 남은 양 칸을 안 바꾸면 그대로
        assertEquals(t0, med.stockCountedAt)
        val stock = memo.copy(stock = "10", form = MedForm.liquid)
        updateMed(dao, med, memo, stock, t0 + 700_000)
        med = meds().single()
        assertEquals(10.0, med.stockAmount!!, 0.0)
        assertEquals(t0 + 700_000, med.stockCountedAt)
        assertEquals(DoseUnit.ml, med.unit) // 모양을 바꾸면 단위도
        updateMed(dao, med, stock, stock.copy(stock = ""))
        med = meds().single()
        assertNull(med.stockAmount)
        assertNull(med.stockCountedAt)
    }

    @Test fun formChangeRecountsFromShownStock() = runBlocking {
        val med = addMed(dao, fields("약", stock = "30"), t0)
        val records = listOf(medRecord(med, "given", 10.0, at = t0 + 60_000))
        val original = medFormOf(med, records)
        updateMed(dao, med, original, original.copy(form = MedForm.liquid), t0 + 500_000) // 모양만 바꿈
        val saved = meds().single()
        assertEquals(20.0, saved.stockAmount!!, 0.0) // 보이던 지금 남은 양으로 다시 셈(옛 단위 기록이 되돌아오지 않게)
        assertEquals(t0 + 500_000, saved.stockCountedAt)
        assertEquals(20.0, medRemaining(saved, records)!!, 0.0)
    }

    @Test fun removeBlockedWhileTakingAndGrouped() = runBlocking {
        val kong = Pet(createdAt = t0, name = "콩이", species = Species.cat, breed = "", furColor = "")
        val bori = Pet(createdAt = t0 + 10, name = "보리", species = Species.dog, breed = "", furColor = "")
        val syrup = addMed(dao, fields("위장약 시럽", form = MedForm.liquid))
        addMed(dao, fields("유산균", form = MedForm.powder).copy(category = MedCategory.supplement))
        val open = MedSchedule(petId = bori.id, medicationId = syrup.id, amount = 1.0, unit = DoseUnit.ml, frequency = MedFrequency.daily, activeFrom = t0)
        val closed = MedSchedule(petId = kong.id, medicationId = syrup.id, amount = 1.0, unit = DoseUnit.ml, frequency = MedFrequency.daily, activeFrom = t0, activeUntil = t0 + 100)
        val pets = listOf(kong, bori)
        assertEquals(listOf("보리"), medTakers(syrup, listOf(open, closed), pets))
        assertEquals("위장약 시럽은 보리가 먹고 있는 약이에요. 먼저 탕약에서 이 약을 '그만 먹이기'로 바꿔 주세요.", medBlockedMessage(syrup, listOf("보리")))
        assertTrue(medBlockedMessage(syrup, listOf("콩이", "보리")).startsWith("위장약 시럽은 콩이·보리가 먹고 있는"))
        assertTrue(medTakers(syrup, listOf(open.copy(activeUntil = t0 + 200), closed), pets).isEmpty()) // 그만 먹이면 뺄 수 있음
        assertEquals(listOf("약", "보조제"), groupedMeds(meds()).map { it.first })
        removeMed(dao, syrup)
        assertEquals(listOf("보조제"), groupedMeds(meds()).map { it.first }) // 빈 묶음 숨김
        assertEquals("위장약 시럽", meds().first { it.id == syrup.id }.name) // 이름은 남음
    }
}

/** 더보기 → 약방(약 없음) → 들이기(오류 → 저장) → 줄 → 고치기 → 약방에서 빼기 확인 (8-1). */
class YakbangFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun addEditRemove() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("약 목록", substring = true))
        rule.onNode(hasText("약 목록", substring = true)).performScrollTo().performClick()
        waitFor(hasText("아직 들인 약이 없어요."))
        rule.onNode(hasText("+ 약 들이기")).performClick()
        waitFor(hasText("약방에 약 들이기"))
        rule.onNode(hasText("약방에 들이기").and(hasClickAction())).assertIsNotEnabled() // 덜 채우면 흐리게 + 남은 것
        waitFor(hasText("약 이름을 넣어 주세요"))
        rule.onNode(hasSetTextAction().and(hasText("약 이름", substring = true))).performTextInput("위장약 시럽")
        rule.onNode(hasText("물약").and(hasClickAction())).performClick()
        rule.onNode(hasSetTextAction().and(hasText("지금 남은 양", substring = true))).performScrollTo().performTextInput("120")
        rule.onNode(hasText("약방에 들이기").and(hasClickAction())).performClick()
        waitFor(hasText("물약 · 1ml씩 · 남은 120ml"))
        rule.onNode(hasText("위장약 시럽")).performClick()
        waitFor(hasText("약 고치기"))
        rule.onNode(hasText("약방에서 빼기")).performScrollTo().performClick() // 내용 맨 아래(고정 칸 밖)
        waitFor(hasText("위장약 시럽을 약방에서 뺄까요?"))
        rule.onNode(hasText("빼기").and(hasClickAction())).performClick()
        waitFor(hasText("아직 들인 약이 없어요."))
    }
}
