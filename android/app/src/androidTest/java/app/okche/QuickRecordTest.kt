package app.okche

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.OkcheDatabase
import app.okche.store.RecordKind
import app.okche.ui.record.assignRecord
import app.okche.ui.record.daySummary
import app.okche.ui.record.dayTitle
import app.okche.ui.record.deleteRecord
import app.okche.ui.record.quickRows
import app.okche.ui.record.recordValue
import app.okche.ui.record.saveQuickRecord
import app.okche.ui.record.shiftDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 바로 기록·방금 남긴 기록·기록 목록 규칙(plan.md 7-1). iOS QuickRecordTests와 같은 경우. */
class QuickRecordTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(y, m, d, h, min).atZone(seoul).toInstant().toEpochMilli()
    private fun row(kind: RecordKind) = quickRows.first { it.kind == kind }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun saveStoresValueAndSixAmDay() = runBlocking {
        val vomit = saveQuickRecord(dao, row(RecordKind.vomit), "kibble", "pet", at(2026, 10, 25, 5, 59), seoul)
        assertEquals("2026-10-24", vomit.day) // 6시 전은 전날
        assertEquals("""{"contents":["kibble"]}""", vomit.data)
        assertEquals("사료", recordValue(vomit))
        val stool = saveQuickRecord(dao, row(RecordKind.stool), "normal", null, at(2026, 10, 25, 6), seoul)
        assertEquals("2026-10-25", stool.day)
        assertNull(stool.petId) // 누군지 모름
        assertEquals("""{"shape":"normal"}""", stool.data)
        assertEquals("정상", recordValue(stool))
        assertEquals("남김", recordValue(saveQuickRecord(dao, row(RecordKind.meal), "some", "pet", at(2026, 10, 25, 8), seoul)))
    }

    @Test fun latestTodaySkipsDeletedAndOtherDays() = runBlocking {
        saveQuickRecord(dao, row(RecordKind.urine), "little", null, at(2026, 10, 24, 22), seoul)
        val first = saveQuickRecord(dao, row(RecordKind.stool), "hard", null, at(2026, 10, 25, 7), seoul)
        val second = saveQuickRecord(dao, row(RecordKind.vomit), "digested", null, at(2026, 10, 25, 9), seoul)
        assertEquals(second.id, dao.latestRecordFlow("2026-10-25").first()?.id)
        deleteRecord(dao, second, at(2026, 10, 25, 9, 5))
        assertEquals("{}", db.query("SELECT data FROM record WHERE id = '${second.id}'", null).use { c -> c.moveToFirst(); c.getString(0) })
        assertEquals(first.id, dao.latestRecordFlow("2026-10-25").first()?.id)
        assertNull(dao.latestRecordFlow("2026-10-26").first()) // 날 바뀜
    }

    @Test fun dayRecordsFilterSummaryAndAssign() = runBlocking {
        saveQuickRecord(dao, row(RecordKind.stool), "normal", "kong", at(2026, 10, 25, 9), seoul)
        saveQuickRecord(dao, row(RecordKind.stool), "soft", "bori", at(2026, 10, 25, 7), seoul)
        val unknown = saveQuickRecord(dao, row(RecordKind.vomit), "kibble", null, at(2026, 10, 26, 2), seoul)
        val all = dao.dayRecordsFlow("2026-10-25").first()
        assertEquals(3, all.size) // 새벽 기록은 전날
        assertEquals(unknown.id, all.first().id) // 늦은 것 위
        assertEquals(1, all.count { it.petId == "kong" })
        assertEquals("구토 1번 · 매화 2번", daySummary(all))
        assertNull(daySummary(emptyList()))
        assignRecord(dao, unknown, "bori", at(2026, 10, 26, 8))
        val assigned = dao.dayRecordsFlow("2026-10-25").first().first { it.id == unknown.id }
        assertEquals("bori", assigned.petId)
        assertEquals(at(2026, 10, 26, 8), assigned.updatedAt)
    }

    @Test fun dayShiftAndTitle() {
        assertEquals("2026-02-28", shiftDay("2026-03-01", -1))
        assertEquals("2027-01-01", shiftDay("2026-12-31", 1))
        assertEquals("오늘 · 10월 25일 (일)", dayTitle("2026-10-25", "2026-10-25"))
        assertEquals("10월 24일 (토)", dayTitle("2026-10-24", "2026-10-25"))
    }
}
