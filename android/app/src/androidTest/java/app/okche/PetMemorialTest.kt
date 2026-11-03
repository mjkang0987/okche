package app.okche

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.DoseUnit
import app.okche.store.FoodChange
import app.okche.store.FoodChangeMethod
import app.okche.store.InsulinPlan
import app.okche.store.InsulinSlot
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Photo
import app.okche.store.PhotoPet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.RestPeriod
import app.okche.store.Species
import app.okche.ui.pet.MemorialStats
import app.okche.ui.pet.memorialStats
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 추억으로 간직하기·되돌리기·아이 지우기·추억 화면 숫자(plan.md 6-4). iOS PetMemorialTests와 같은 경우. */
class PetMemorialTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atTime(9, 0).atZone(seoul).toInstant().toEpochMilli()
    private val created = at(2026, 10, 1)
    private val kept = at(2026, 10, 20)
    private val back = at(2026, 11, 1)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    private fun pet(name: String = "콩이", diabetes: Boolean = false) = runBlocking {
        Pet(createdAt = created, name = name, species = Species.cat, breed = "코리안숏헤어", furColor = "치즈", hasDiabetes = diabetes)
            .also { dao.insert(it) }
    }

    private fun med(petId: String, until: Long? = null) = runBlocking {
        MedSchedule(createdAt = created, petId = petId, medicationId = "med", amount = 1.0, unit = DoseUnit.pill,
            frequency = MedFrequency.daily, times = listOf("08:00"), activeFrom = created, activeUntil = until).also { dao.insert(it) }
    }

    private fun meds() = runBlocking { dao.medSchedules() }

    @Test fun memorializeClosesOpenPlansAtSameTime() = runBlocking {
        val kong = pet()
        val open = med(kong.id)
        val closed = med(kong.id, until = at(2026, 10, 5))
        dao.insert(InsulinPlan(createdAt = created, petId = kong.id, slots = listOf(InsulinSlot("08:00", 2.0)), activeFrom = created))
        dao.insert(MealPlan(createdAt = created, petId = kong.id, mode = MealMode.direct, slots = listOf(MealSlot("아침", "08:00")), activeFrom = created))
        dao.insert(RestPeriod(createdAt = created, petId = kong.id, startedAt = created))
        val other = med(pet("보리").id)

        dao.memorialize(kong.id, kept)
        assertEquals(kept, dao.pet(kong.id)?.memorializedAt)
        val byId = meds().associateBy { it.id }
        assertEquals(kept, byId.getValue(open.id).activeUntil)
        assertEquals(at(2026, 10, 5), byId.getValue(closed.id).activeUntil) // 이미 닫힌 줄은 그대로
        assertNull(byId.getValue(other.id).activeUntil) // 다른 아이는 그대로
        assertEquals(listOf(kept), dao.insulinPlans().map { it.activeUntil })
        assertEquals(listOf(kept), dao.mealPlans().map { it.activeUntil })
        assertEquals(emptyList<RestPeriod>(), dao.openRestPeriods(kong.id))
    }

    @Test fun restoreReopensOnlyRowsClosedWhenKept() = runBlocking {
        val kong = pet(diabetes = true)
        val open = med(kong.id)
        med(kong.id, until = at(2026, 10, 5))
        dao.insert(InsulinPlan(createdAt = created, petId = kong.id, name = "란투스", slots = listOf(InsulinSlot("08:00", 2.0)), activeFrom = created))
        dao.insert(RestPeriod(createdAt = created, petId = kong.id, startedAt = created))
        dao.memorialize(kong.id, kept)

        dao.restoreFromMemorial(kong.id, back)
        assertNull(dao.pet(kong.id)?.memorializedAt)
        val reopened = meds().filter { it.activeUntil == null }
        assertEquals(listOf(open.seriesId), reopened.map { it.seriesId }) // 약 주기 seriesId 그대로
        assertEquals(listOf(back), reopened.map { it.activeFrom })
        assertEquals(3, meds().size)
        assertEquals(listOf("란투스"), dao.openInsulinPlans(kong.id).map { it.name })
        assertEquals(emptyList<RestPeriod>(), dao.openRestPeriods(kong.id)) // 휴양은 다시 안 염
    }

    @Test fun deleteMarksEverythingAndKeepsSharedPhotos() = runBlocking {
        val kong = pet()
        val bori = pet("보리")
        dao.insert(Record(createdAt = created, petId = kong.id, kind = RecordKind.vomit, occurredAt = created, day = "2026-10-01"))
        dao.insert(Record(createdAt = created, petId = bori.id, kind = RecordKind.vomit, occurredAt = created, day = "2026-10-01"))
        med(kong.id)
        dao.insert(FoodChange(createdAt = created, petId = kong.id, foodId = "food", startedOn = "2026-10-01", method = FoodChangeMethod.gradual))
        val alone = Photo(createdAt = created, takenAt = created, localDate = "2026-10-01", file = "a.jpg").also { dao.insert(it) }
        val shared = Photo(createdAt = created, takenAt = created, localDate = "2026-10-01", file = "b.jpg").also { dao.insert(it) }
        dao.insert(PhotoPet(createdAt = created, photoId = alone.id, petId = kong.id))
        dao.insert(PhotoPet(createdAt = created, photoId = shared.id, petId = kong.id))
        dao.insert(PhotoPet(createdAt = created, photoId = shared.id, petId = bori.id))

        dao.deletePet(kong.id, kept)
        assertNull(dao.pet(kong.id))
        assertEquals(listOf(bori.id), db.query("SELECT pet_id FROM record WHERE deleted_at IS NULL", null).use { c -> List(c.count) { c.moveToNext(); c.getString(0) } })
        assertEquals(emptyList<MedSchedule>(), meds())
        assertEquals(1L, db.query("SELECT COUNT(*) FROM food_change WHERE deleted_at = $kept", null).use { c -> c.moveToFirst(); c.getLong(0) })
        assertEquals(listOf(shared.id), dao.photos().map { it.id }) // 남은 아이가 없는 사진만 지움
        assertEquals(listOf(bori.id), db.query("SELECT pet_id FROM photo_pet WHERE deleted_at IS NULL", null).use { c -> List(c.count) { c.moveToNext(); c.getString(0) } })
    }

    @Test fun memorialStatsCountDays() = runBlocking {
        val kong = pet()
        val today = LocalDate.of(2026, 11, 3)
        assertEquals(MemorialStats(null, null, null), memorialStats(dao, kong, today, seoul)) // 가족 된 날·기록 모두 없음
        dao.insert(Record(createdAt = created, petId = kong.id, kind = RecordKind.vomit, occurredAt = at(2026, 10, 25), day = "2026-10-25"))
        dao.insert(Record(createdAt = created, petId = kong.id, kind = RecordKind.stool, occurredAt = at(2026, 3, 14), day = "2026-03-14"))
        dao.insert(Record(createdAt = created, deletedAt = created, petId = kong.id, kind = RecordKind.stool, occurredAt = at(2025, 1, 1), day = "2025-01-01"))
        // 가족 된 날이 없으면 첫 기록 날부터(첫날 = 1일)
        assertEquals(MemorialStats(235, "2026년 3월 14일", "2026년 10월 25일"), memorialStats(dao, kong, today, seoul))
        assertEquals(1258L, memorialStats(dao, kong.copy(familyDate = "2023-05-26"), today, seoul).days)
    }

    /** 두 번 간직해도 처음 간직 시각 그대로 — 되돌릴 줄을 잃지 않는다. */
    @Test fun memorializeTwiceKeepsFirstTime() = runBlocking {
        val kong = pet()
        val open = med(kong.id)
        dao.memorialize(kong.id, kept)
        dao.memorialize(kong.id, back)
        assertEquals(kept, dao.pet(kong.id)?.memorializedAt)
        dao.restoreFromMemorial(kong.id, back)
        assertEquals(listOf(open.seriesId), meds().filter { it.activeUntil == null }.map { it.seriesId })
    }

    /** 간직 중 당뇨를 껐거나 인슐린을 새로 설정했으면 되돌릴 때 인슐린을 다시 열지 않는다(열린 계획 하나만). */
    @Test fun restoreSkipsInsulinWhenNotDiabeticOrAlreadyOpen() = runBlocking {
        val kong = pet(diabetes = true)
        dao.insert(InsulinPlan(createdAt = created, petId = kong.id, slots = listOf(InsulinSlot("08:00", 2.0)), activeFrom = created))
        dao.memorialize(kong.id, kept)
        dao.insert(InsulinPlan(createdAt = kept, petId = kong.id, slots = listOf(InsulinSlot("09:00", 3.0)), activeFrom = at(2026, 10, 25)))
        dao.restoreFromMemorial(kong.id, back)
        assertEquals(listOf(listOf(InsulinSlot("09:00", 3.0))), dao.openInsulinPlans(kong.id).map { it.slots })

        val bori = pet("보리", diabetes = true)
        dao.insert(InsulinPlan(createdAt = created, petId = bori.id, slots = listOf(InsulinSlot("08:00", 2.0)), activeFrom = created))
        dao.memorialize(bori.id, kept)
        dao.update(checkNotNull(dao.pet(bori.id)).copy(hasDiabetes = false))
        dao.restoreFromMemorial(bori.id, back)
        assertEquals(0, dao.openInsulinPlans(bori.id).size)
    }

    /** 지우면 선택 칸을 비운다(데이터 1-4). */
    @Test fun deleteClearsOptionalFields() = runBlocking {
        val kong = pet()
        dao.update(kong.copy(diagnosis = "IBD", familyDate = "2023-05-26"))
        dao.insert(Record(createdAt = created, petId = kong.id, kind = RecordKind.vomit, occurredAt = created, data = """{"color":"yellow"}"""))
        dao.deletePet(kong.id, kept)
        val row = db.query("SELECT diagnosis, family_date FROM pet", null).use { c -> c.moveToFirst(); listOf(c.getString(0), c.getString(1)) }
        assertEquals(listOf<String?>(null, null), row)
        assertEquals("{}", db.query("SELECT data FROM record", null).use { c -> c.moveToFirst(); c.getString(0) })
    }
}

