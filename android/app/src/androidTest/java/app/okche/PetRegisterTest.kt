package app.okche

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.InsulinSlot
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.RecordKind
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import app.okche.ui.pet.InsulinRow
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.PetRegisterSaver
import app.okche.ui.pet.registerPet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

/** 아이 등록 저장 규칙(iOS와 같음). */
class PetRegisterTest {
    private lateinit var db: OkcheDatabase
    private val now = 1_793_000_000_000L
    private val today = LocalDate.of(2026, 10, 29)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun save(form: PetForm, today: LocalDate = this.today) = runBlocking { registerPet(db.dao(), form, now, today) }

    private fun count(table: String) = db.query("SELECT count(*) FROM $table", null).use { it.moveToFirst(); it.getInt(0) }

    private fun assertNothingSaved() = assertEquals(listOf(0, 0, 0), listOf(count("pet"), count("record"), count("insulin_plan")))

    @Test fun blankNameIsErrorAndSavesNothing() {
        assertEquals(mapOf("name" to "이름을 넣어 주세요"), save(PetForm(name = "  ", weight = "4", hasDiabetes = true,
            insulinRows = listOf(InsulinRow("08:00", "2")))))
        assertNothingSaved()
    }

    @Test fun savesPetWithTrimmedFieldsAndDefaults() = runBlocking {
        assertEquals(emptyMap<String, String>(), save(PetForm(name = " 콩이 ", sex = Sex.female, neutered = true,
            birthDate = LocalDate.of(2019, 10, 25), diagnosis = "  ")))
        val pet = db.dao().pets().single()
        assertEquals("콩이", pet.name)
        assertEquals(Species.cat, pet.species)
        assertEquals("코리안숏헤어", pet.breed)
        assertEquals("치즈", pet.furColor)
        assertEquals(Sex.female, pet.sex)
        assertEquals(true, pet.neutered)
        assertEquals("2019-10-25", pet.birthDate)
        assertEquals(false, pet.birthEstimated)
        assertNull(pet.diagnosis)
        assertEquals(false, pet.hasDiabetes)
        assertEquals(now, pet.createdAt)
        db.query("SELECT sex, neutered FROM pet", null).use { it.moveToFirst(); assertEquals("female 1", "${it.getString(0)} ${it.getInt(1)}") }
        // 고르지 않은 성별·중성화·생일은 없음, 체중이 없으면 기록 없음.
        save(PetForm(name = "보리"))
        val second = db.dao().pets().last()
        assertEquals(listOf(null, null, null), listOf(second.sex, second.neutered, second.birthDate))
        assertEquals(0, count("record"))
    }

    @Test fun speciesChangeResetsBreedAndFur() {
        val dog = PetForm(breed = "샴", furColor = "턱시도").withSpecies(Species.dog)
        assertEquals(listOf("말티즈", "흰색"), listOf(dog.breed, dog.furColor))
        val cat = dog.copy(breed = "시바").withSpecies(Species.cat)
        assertEquals(listOf("코리안숏헤어", "치즈"), listOf(cat.breed, cat.furColor))
    }

    @Test fun estimatedAgeBecomesBirthDate() = runBlocking {
        // 생일 칸 값은 '생일을 몰라요'를 켜면 쓰지 않는다.
        save(PetForm(name = "보리", birthUnknown = true, age = "3", birthDate = LocalDate.of(2000, 1, 1)))
        val pet = db.dao().pets().single()
        assertEquals("2023-10-29", pet.birthDate)
        assertTrue(pet.birthEstimated)
        // 2/29에서 1년 빼면 2/28(java.time 규칙), 0살은 오늘.
        save(PetForm(name = "윤", birthUnknown = true, age = "1"), today = LocalDate.of(2028, 2, 29))
        save(PetForm(name = "아기", birthUnknown = true, age = "0"))
        assertEquals(listOf("2027-02-28", "2026-10-29"), db.dao().pets().drop(1).map { it.birthDate })
        // 나이를 비우면 생일 없음, 어림 아님.
        save(PetForm(name = "모름", birthUnknown = true))
        val unknown = db.dao().pets().last()
        assertEquals(null to false, unknown.birthDate to unknown.birthEstimated)
    }

    @Test fun ageAndWeightAndBirthErrors() {
        for (age in listOf("31", "-1", "2.5", "세 살")) {
            assertEquals(age, mapOf("age" to "0에서 30 사이로 넣어 주세요"), save(PetForm(name = "콩이", birthUnknown = true, age = age)))
        }
        // 무한대·NaN·지수로 넘치는 값은 숫자가 아니고, 그램으로 반올림해 0이면 체중이 아니다.
        for (weight in listOf("0", "100.1", "-3", "abc", "NaN", "Infinity", "1e400", "9".repeat(400), "0.0004")) {
            assertEquals(weight, mapOf("weight" to "0보다 크고 100 이하인 숫자로 넣어 주세요"), save(PetForm(name = "콩이", weight = weight)))
        }
        assertEquals(mapOf("birthDate" to "오늘보다 뒤 날짜는 넣을 수 없어요"),
            save(PetForm(name = "콩이", birthDate = today.plusDays(1))))
        // 오류는 화면 위에서부터 순서.
        assertEquals(listOf("name", "age", "weight"), save(PetForm(birthUnknown = true, age = "99", weight = "0")).keys.toList())
        assertNothingSaved()
    }

    /** 저장소 오류는 칸 오류가 아니라 save 하나로. */
    @Test fun storeFailureIsSaveError() {
        db.close()
        assertEquals(mapOf("save" to "저장하지 못했어요. 다시 해 주세요."), save(PetForm(name = "콩이", weight = "4")))
    }

    /** iOS와 같은 숫자 규칙: ASCII 숫자와 점·쉼표 하나만. */
    @Test fun numbersAreAsciiOnly() {
        val weightError = mapOf("weight" to "0보다 크고 100 이하인 숫자로 넣어 주세요")
        for (weight in listOf("４.５", "٤", "1.5f", "1e1", "+3", "0x1", "1.2.3", "1,2,3", "4 5", ".", "1d")) {
            assertEquals(weight, weightError, save(PetForm(name = "콩이", weight = weight)))
        }
        val ageError = mapOf("age" to "0에서 30 사이로 넣어 주세요")
        for (age in listOf("３", "٣", "+3", "3.0", "1e1", "99999999999999999999")) {
            assertEquals(age, ageError, save(PetForm(name = "콩이", birthUnknown = true, age = age)))
        }
        val units = mapOf("units0" to "0보다 크고 100 이하인 숫자로 넣어 주세요")
        assertEquals(units, save(PetForm(name = "콩이", hasDiabetes = true, insulinRows = listOf(InsulinRow("08:00", "2.5f")))))
        assertNothingSaved()
        // 받는 꼴: 앞뒤 빈칸, 쉼표 소수점, 끝 점, 앞 점, 0으로 시작하는 나이.
        for ((weight, age) in listOf(" 4.5 " to " 03 ", "4," to "0", ".5" to "30")) {
            assertEquals(weight, emptyMap<String, String>(), save(PetForm(name = "콩이", weight = weight, birthUnknown = true, age = age)))
        }
    }

    /** 저장 중 '모시기'를 또 눌러도 한 번만 저장한다. */
    @Test fun saverIgnoresSecondTapWhileSaving() {
        val saver = PetRegisterSaver()
        val form = PetForm(name = "콩이")
        saver.save(db.dao(), form)
        saver.save(db.dao(), form)
        val deadline = System.currentTimeMillis() + 5_000
        while (saver.result == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(emptyMap<String, String>(), saver.result)
        assertEquals(1, count("pet"))
    }

    /** 저장 중 저장소가 오류를 내도 앱이 죽지 않고 '모시기' 위 문구로 끝난다. */
    @Test fun saverReportsStoreFailure() {
        val saver = PetRegisterSaver()
        db.close()
        saver.save(db.dao(), PetForm(name = "콩이"))
        val deadline = System.currentTimeMillis() + 5_000
        while (saver.result == null && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals(mapOf("save" to "저장하지 못했어요. 다시 해 주세요."), saver.result)
        assertEquals(false, saver.saving)
    }

    @Test fun weightBecomesGramsRecord() = runBlocking {
        save(PetForm(name = "콩이", weight = "4.6"))
        save(PetForm(name = "보리", weight = "100"))
        save(PetForm(name = "쉼표", weight = "3,25"))
        save(PetForm(name = "아주 작음", weight = "0.0005")) // 반올림해 1그램
        val pets = db.dao().pets()
        val grams = pets.map { pet ->
            val record = db.dao().records(pet.id, RecordKind.weight, OkcheDay.of(now)).single()
            assertEquals(now, record.occurredAt)
            record.data
        }
        assertEquals(listOf("""{"grams":4600}""", """{"grams":100000}""", """{"grams":3250}""", """{"grams":1}"""), grams)
    }

    @Test fun diabetesSavesInsulinPlanSortedByTime() = runBlocking {
        save(PetForm(name = "콩이", hasDiabetes = true, insulinName = "  ", syringe = Syringe.u40,
            insulinRows = listOf(InsulinRow("20:00", "1.5"), InsulinRow("08:00", "2"), InsulinRow("12:00", "0.5"))))
        val pet = db.dao().pets().single()
        assertTrue(pet.hasDiabetes)
        val plan = db.dao().insulinPlans().single()
        assertEquals(pet.id, plan.petId)
        assertNull(plan.name)
        assertEquals(Syringe.u40, plan.syringe)
        assertEquals(listOf(InsulinSlot("08:00", 2.0), InsulinSlot("12:00", 0.5), InsulinSlot("20:00", 1.5)), plan.slots)
        assertEquals(now, plan.activeFrom)
        assertNull(plan.activeUntil)
        // 처음 값: 주사기 '잘 모르겠어요', 이름은 빈칸 빼고.
        save(PetForm(name = "보리", hasDiabetes = true, insulinName = " 란투스 ", insulinRows = listOf(InsulinRow("08:00", "1"))))
        val second = db.dao().insulinPlans().single { it.petId != pet.id }
        assertEquals("란투스" to Syringe.unknown, second.name to second.syringe)
    }

    @Test fun noDiabetesSavesNoPlanEvenWithRows() = runBlocking {
        // 인슐린 칸에 넣다가 당뇨를 끈 경우 — 양이 비어 있어도 오류 없이 인슐린은 저장하지 않는다.
        assertEquals(emptyMap<String, String>(), save(PetForm(name = "콩이", insulinRows = listOf(InsulinRow("08:00"), InsulinRow("08:00")))))
        assertEquals(false, db.dao().pets().single().hasDiabetes)
        assertEquals(0, count("insulin_plan"))
    }

    @Test fun insulinErrors() {
        val form = PetForm(name = "콩이", hasDiabetes = true)
        assertEquals(mapOf("units0" to "양을 넣어 주세요", "units1" to "양을 넣어 주세요"), save(form))
        val range = "0보다 크고 100 이하인 숫자로 넣어 주세요"
        for (units in listOf("0", "-1", "100.5", "abc", "Infinity", "1e400")) {
            assertEquals(units, mapOf("units0" to range), save(form.copy(insulinRows = listOf(InsulinRow("08:00", units)))))
        }
        assertEquals(mapOf("insulinRows" to "시간을 하나 이상 넣어 주세요"), save(form.copy(insulinRows = emptyList())))
        assertEquals(mapOf("time2" to "같은 시간이 두 번 있어요", "units2" to range),
            save(form.copy(insulinRows = listOf(InsulinRow("08:00", "2"), InsulinRow("20:00", "2"), InsulinRow("08:00", "0")))))
        assertEquals(mapOf("units0" to "양을 넣어 주세요"), save(form.copy(insulinRows = listOf(InsulinRow("08:00", " ")))))
        assertNothingSaved()
    }
}
