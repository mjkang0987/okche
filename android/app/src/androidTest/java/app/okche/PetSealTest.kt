package app.okche

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.ui.pet.currentPet
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
