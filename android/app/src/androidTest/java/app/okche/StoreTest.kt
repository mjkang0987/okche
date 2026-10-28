package app.okche

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.FixedIds
import app.okche.store.Food
import app.okche.store.FoodChange
import app.okche.store.FoodChangeMethod
import app.okche.store.InsulinPlan
import app.okche.store.InsulinSlot
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.DoseUnit
import app.okche.store.BodyView
import app.okche.store.CelebrateTone
import app.okche.store.HowTo
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Photo
import app.okche.store.PhotoKind
import app.okche.store.PhotoPet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.RestPeriod
import app.okche.store.Setting
import app.okche.store.Snack
import app.okche.store.SnackKind
import app.okche.store.Species
import app.okche.store.Syringe
import app.okche.store.WeightReminder
import app.okche.store.fixedId
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class StoreTest {
    private lateinit var db: OkcheDatabase

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun at(zone: String, y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneId.of(zone)).toInstant().toEpochMilli()

    // 고정 ID (1-5)

    /** iOS StoreTests와 같은 기대값 — 두 플랫폼이 같은 이름에서 같은 ID를 만들어야 백업을 합쳐도 한 줄. */
    @Test fun fixedIdMatchesIos() {
        assertEquals("c0236f43-0deb-3ae8-a64f-633d62651603", FixedIds.SETTING)
        assertEquals("92c60769-4d57-30ba-9ba1-6cf8ccd959d9", FixedIds.feederRecord("abc|2026-10-28|08:00"))
        assertEquals("075f430b-623d-35eb-97d9-51fe7a58e9df", fixedId("옥체"))
    }

    /** 선택지 저장 값이 문서·iOS(`StoreTests.testChoiceValuesMatchDoc`)와 글자 하나까지 같은지. */
    @Test fun choiceValuesMatchDoc() {
        val expected = mapOf(
            "Species" to "cat dog",
            "RecordKind" to "vomit stool urine meal snack weight med glucose insulin",
            "MedCategory" to "medicine supplement",
            "MedForm" to "pill powder liquid topical injection",
            "DoseUnit" to "pill ml pack drop",
            "MedFrequency" to "daily everyNDays weekdays everyNHours asNeeded",
            "HowTo" to "plain inFood inTreat syringe",
            "Syringe" to "u40 u100 unknown",
            "FoodChangeMethod" to "gradual atOnce",
            "SnackKind" to "lickable freezeDried jerky wet prescription human",
            "MealMode" to "direct feeder",
            "PhotoKind" to "normal body",
            "BodyView" to "top side",
            "CelebrateTone" to "joseon plain",
        )
        val actual = listOf(
            Species.entries, RecordKind.entries, MedCategory.entries, MedForm.entries, DoseUnit.entries, MedFrequency.entries,
            HowTo.entries, Syringe.entries, FoodChangeMethod.entries, SnackKind.entries, MealMode.entries, PhotoKind.entries,
            BodyView.entries, CelebrateTone.entries,
        ).associate { values -> values.first().javaClass.simpleName to values.joinToString(" ") { it.name } }
        assertEquals(expected, actual)
    }

    // 하루 기준 6시 (1-2) — iOS와 같은 경우

    @Test fun dayStartsAtSixLocal() {
        val seoul = ZoneId.of("Asia/Seoul")
        assertEquals("2026-10-27", OkcheDay.of(at("Asia/Seoul", 2026, 10, 28, 5, 59), seoul))
        assertEquals("2026-10-28", OkcheDay.of(at("Asia/Seoul", 2026, 10, 28, 6, 0), seoul))
        assertEquals("2026-10-27", OkcheDay.of(at("Asia/Seoul", 2026, 10, 28, 0, 0), seoul))
        assertEquals("2026-10-28", OkcheDay.of(at("Asia/Seoul", 2026, 10, 28, 23, 59), seoul))
        assertEquals("2026-12-31", OkcheDay.of(at("Asia/Seoul", 2027, 1, 1, 2, 0), seoul))
    }

    @Test fun dayFollowsTimeZone() {
        val instant = at("Asia/Seoul", 2026, 10, 28, 10, 0) // UTC 01:00
        assertEquals("2026-10-28", OkcheDay.of(instant, ZoneId.of("Asia/Seoul")))
        assertEquals("2026-10-27", OkcheDay.of(instant, ZoneId.of("UTC")))
    }

    /** 경과 시간 6시간을 빼면 3/8 06:00과 11/1 05:59가 틀린다. */
    @Test fun dayOnDaylightSavingDays() {
        val ny = ZoneId.of("America/New_York")
        assertEquals("2026-03-07", OkcheDay.of(at("America/New_York", 2026, 3, 8, 3, 30), ny))
        assertEquals("2026-03-08", OkcheDay.of(at("America/New_York", 2026, 3, 8, 6, 0), ny))
        assertEquals("2026-03-08", OkcheDay.of(at("America/New_York", 2026, 3, 9, 2, 30), ny))
        assertEquals("2026-10-31", OkcheDay.of(at("America/New_York", 2026, 11, 1, 5, 59), ny))
        assertEquals("2026-11-01", OkcheDay.of(at("America/New_York", 2026, 11, 1, 6, 0), ny))
    }

    @Test fun recordDayIsSetOnCreateAndTimeChange() {
        val seoul = ZoneId.of("Asia/Seoul")
        val t = at("Asia/Seoul", 2026, 10, 28, 2, 0)
        val record = Record(createdAt = 0, petId = null, kind = RecordKind.vomit, occurredAt = t, day = OkcheDay.of(t, seoul))
        assertEquals("2026-10-27", record.day)
        val edited = record.withOccurredAt(at("Asia/Seoul", 2026, 10, 28, 7, 0), seoul, now = 100)
        assertEquals("2026-10-28", edited.day)
        assertEquals(100L, edited.updatedAt)
        assertEquals(0L, edited.createdAt)
    }

    // 저장소

    @Test fun allEntitiesRoundTrip() = runBlocking {
        val dao = db.dao()
        val now = at("Asia/Seoul", 2026, 10, 28, 9, 0)
        val pet = Pet(createdAt = now, name = "콩이", species = Species.cat, breed = "코리안 숏헤어", furColor = "치즈",
            birthDate = "2015-04-01", hasDiabetes = true)
        val med = Medication(createdAt = now, name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill,
            doseAmount = 0.5, unit = DoseUnit.pill)
        val food = Food(createdAt = now, name = "처방 사료")
        val photo = Photo(createdAt = now, takenAt = now, localDate = "2026-10-28", kind = PhotoKind.body, bodyView = BodyView.top,
            bodyScore = 3, file = "x.heic")
        dao.insert(pet)
        dao.insert(RestPeriod(createdAt = now, petId = pet.id, startedAt = now))
        dao.insert(Record(createdAt = now, petId = pet.id, kind = RecordKind.glucose, occurredAt = now,
            day = OkcheDay.of(now, ZoneId.of("Asia/Seoul")), data = """{"mgdl":180}"""))
        dao.insert(med)
        dao.insert(MedSchedule(createdAt = now, petId = pet.id, medicationId = med.id, amount = 0.5, unit = DoseUnit.pill,
            frequency = MedFrequency.weekdays, times = listOf("08:00", "20:00"), weekdays = listOf(1, 3, 5), activeFrom = now))
        dao.insert(InsulinPlan(createdAt = now, petId = pet.id, syringe = Syringe.u40,
            slots = listOf(InsulinSlot("08:00", 2.0), InsulinSlot("20:00", 1.5)), activeFrom = now))
        dao.insert(food)
        dao.insert(FoodChange(createdAt = now, petId = pet.id, foodId = food.id, startedOn = "2026-10-28", method = FoodChangeMethod.gradual))
        dao.insert(Snack(createdAt = now, name = "츄르", kind = SnackKind.lickable))
        dao.insert(MealPlan(createdAt = now, petId = pet.id, mode = MealMode.feeder, slots = listOf(MealSlot("아침", "08:00")), activeFrom = now))
        dao.insert(photo)
        dao.insert(PhotoPet(createdAt = now, photoId = photo.id, petId = pet.id))
        dao.insert(Setting(createdAt = now))

        for (table in listOf("pet", "rest_period", "record", "medication", "med_schedule", "insulin_plan", "food",
            "food_change", "snack", "meal_plan", "photo", "photo_pet", "setting")) {
            db.query("SELECT count(*) FROM $table", null).use { it.moveToFirst(); assertEquals(table, 1, it.getInt(0)) }
        }
        assertEquals(listOf(pet), dao.pets())
        val schedule = dao.medSchedules().single()
        assertEquals(schedule.id, schedule.seriesId)
        assertEquals(listOf("08:00", "20:00"), schedule.times)
        assertEquals(listOf(1, 3, 5), schedule.weekdays)
        assertTrue(schedule.shiftOnLate)
        assertEquals(listOf(2.0, 1.5), dao.insulinPlans().single().slots.map { it.units })
        assertEquals(listOf(MealSlot("아침", "08:00")), dao.mealPlans().single().slots)
        assertEquals(photo, dao.photos().single())
        val record = dao.records(pet.id, RecordKind.glucose, "2026-10-28").single()
        assertEquals("""{"mgdl":180}""", record.data)
        // 선택지는 문서의 영어 값 글자로 저장된다.
        db.query("SELECT species FROM pet", null).use { it.moveToFirst(); assertEquals("cat", it.getString(0)) }
        db.query("SELECT frequency FROM med_schedule", null).use { it.moveToFirst(); assertEquals("weekdays", it.getString(0)) }
        val setting = dao.loadOrCreateSetting()
        assertEquals(FixedIds.SETTING, setting.id)
        assertEquals(WeightReminder(7, "10:00"), setting.weightReminder)
        assertEquals(3, setting.lowMedDays)
        assertFalse(setting.quietHours.enabled)
        assertTrue(setting.notify.lastYearToday)
    }

    /** 아이 + 종류 + 날짜 검색에서 다른 아이·종류·날짜, 모르는 아이, 지운 기록은 빠진다. */
    @Test fun fetchRecordsByPetKindAndDay() = runBlocking {
        val dao = db.dao()
        val seoul = ZoneId.of("Asia/Seoul")
        val pet = "pet-1"
        fun rec(petId: String?, kind: RecordKind, h: Int, deletedAt: Long? = null): Record {
            val t = at("Asia/Seoul", 2026, 10, 28, h)
            return Record(petId = petId, kind = kind, occurredAt = t, day = OkcheDay.of(t, seoul), deletedAt = deletedAt)
        }
        dao.insert(rec(pet, RecordKind.vomit, 2)) // 전날
        dao.insert(rec(pet, RecordKind.vomit, 7))
        dao.insert(rec(pet, RecordKind.stool, 7))
        dao.insert(rec(null, RecordKind.vomit, 7))
        dao.insert(rec(pet, RecordKind.vomit, 8, deletedAt = 1))
        assertEquals(1, dao.records(pet, RecordKind.vomit, "2026-10-28").size)
    }

    /** 설정은 있으면 그대로 읽고 없을 때만 만든다 — 지운 줄도 되살리지 않는다. */
    @Test fun loadOrCreateSettingKeepsExisting() = runBlocking {
        val dao = db.dao()
        dao.insert(Setting(lowMedDays = 5, deletedAt = 1))
        val setting = dao.loadOrCreateSetting()
        assertEquals(5, setting.lowMedDays)
        assertEquals(1L, setting.deletedAt)
        db.query("SELECT count(*) FROM setting", null).use { it.moveToFirst(); assertEquals(1, it.getInt(0)) }
    }

    /** Room은 같은 id를 덮어쓰지 않고 오류를 낸다(SwiftData와 다름). 이 동작이 바뀌면 고정 ID 규칙을 다시 본다. */
    @Test fun insertingSameIdFails() = runBlocking {
        val dao = db.dao()
        val id = FixedIds.feederRecord("plan|2026-10-28|08:00")
        dao.insert(Record(id = id, petId = null, kind = RecordKind.meal, occurredAt = 0, auto = true, deletedAt = 1))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { dao.insert(Record(id = id, petId = null, kind = RecordKind.meal, occurredAt = 0, auto = true)) }
        }
        Unit
    }
}
