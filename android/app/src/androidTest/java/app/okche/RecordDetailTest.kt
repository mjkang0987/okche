package app.okche

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.OkcheDatabase
import app.okche.store.RecordKind
import app.okche.ui.record.detailFields
import app.okche.ui.record.detailValues
import app.okche.ui.record.picksAlert
import app.okche.ui.record.quickRows
import app.okche.ui.record.recordValue
import app.okche.ui.record.saveDetail
import app.okche.ui.record.saveQuickRecord
import app.okche.ui.record.toggleDetail
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** 자세히 시트 저장·고치기·의서 레이어 조건·기록 줄 값 글자(plan.md 7-2). iOS RecordDetailTests와 같은 경우. */
class RecordDetailTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(y: Int, m: Int, d: Int, h: Int, min: Int = 0) = LocalDate.of(y, m, d).atTime(h, min).atZone(seoul).toInstant().toEpochMilli()
    private fun field(kind: RecordKind, key: String) = detailFields(kind).first { it.key == key }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun saveMultiSingleNumberAndEmpty() = runBlocking {
        val values = mapOf("contents" to listOf("foreign", "kibble"), "color" to listOf("red"), "count" to listOf("3"))
        val vomit = saveDetail(dao, RecordKind.vomit, values, at(2026, 10, 25, 9), null, null, at(2026, 10, 25, 9, 5), seoul)
        assertEquals("""{"color":"red","contents":["kibble","foreign"],"count":3}""", vomit.data) // 여럿은 시트 순서, 횟수는 숫자
        assertEquals(mapOf("contents" to listOf("kibble", "foreign"), "color" to listOf("red"), "count" to listOf("3")), detailValues(vomit))
        assertEquals("사료 · 이물질 · 붉은색 · 3번 이상", recordValue(vomit))

        val empty = saveDetail(dao, RecordKind.urine, emptyMap(), at(2026, 10, 25, 9), null, null, at(2026, 10, 25, 9), seoul)
        assertEquals("{}", empty.data) // 아무것도 안 골라도 기록
        assertEquals("자세한 내용 없음", recordValue(empty))
    }

    @Test fun lineSkipsUsualValues() = runBlocking {
        val now = at(2026, 10, 25, 9)
        assertEquals("자세한 내용 없음", recordValue(saveDetail(dao, RecordKind.vomit, mapOf("color" to listOf("food"), "count" to listOf("1")), now, null, null, now, seoul)))
        assertEquals("묽음 · 피 · 점액", recordValue(saveDetail(dao, RecordKind.stool, mapOf("shape" to listOf("loose"), "color" to listOf("brown"), "signs" to listOf("mucus", "blood")), now, null, null, now, seoul)))
        assertEquals("찔끔 · 힘만 줌", recordValue(saveDetail(dao, RecordKind.urine, mapOf("amount" to listOf("little"), "color" to listOf("paleYellow"), "signs" to listOf("straining")), now, null, null, now, seoul)))
    }

    /** 시간 바꾸기: 6시 경계로 `day`를 다시 정하고, 지금보다 뒤는 지금으로. */
    @Test fun timeChangeAndEditKeepsOthers() = runBlocking {
        val record = saveQuickRecord(dao, quickRows[1], "normal", "kong", at(2026, 10, 25, 9), seoul)
        val edited = saveDetail(dao, RecordKind.stool, mapOf("shape" to listOf("soft"), "signs" to listOf("blood")), at(2026, 10, 25, 5, 30), record, null, at(2026, 10, 25, 10), seoul)
        val saved = dao.dayRecordsFlow("2026-10-24").first().single()
        assertEquals(edited, saved)
        assertEquals("2026-10-24", saved.day) // 5:30은 전날
        assertEquals(at(2026, 10, 25, 5, 30), saved.occurredAt)
        assertEquals(at(2026, 10, 25, 10), saved.updatedAt)
        assertEquals("kong", saved.petId) // 고치기는 아이·만든 때 그대로
        assertEquals(record.createdAt, saved.createdAt)
        assertEquals("""{"shape":"soft","signs":["blood"]}""", saved.data)

        val later = saveDetail(dao, RecordKind.urine, emptyMap(), at(2026, 10, 25, 23), null, null, at(2026, 10, 25, 10), seoul)
        assertEquals(at(2026, 10, 25, 10), later.occurredAt)
    }

    @Test fun toggle() {
        val contents = field(RecordKind.vomit, "contents")
        var values = toggleDetail(toggleDetail(emptyMap(), contents, "kibble"), contents, "plant")
        assertEquals(listOf("kibble", "plant"), values["contents"])
        values = toggleDetail(values, contents, "kibble")
        assertEquals(listOf("plant"), values["contents"])
        val color = field(RecordKind.vomit, "color")
        values = toggleDetail(toggleDetail(values, color, "red"), color, "green")
        assertEquals(listOf("green"), values["color"]) // 하나 고르기는 바뀜
        assertNull(toggleDetail(values, color, "green")["color"]) // 다시 누르면 풂
    }

    @Test fun alertOnlyWhenNewlyPicked() {
        val none = emptyMap<String, List<String>>()
        assertTrue(picksAlert(RecordKind.vomit, none, mapOf("color" to listOf("red"))))
        assertTrue(picksAlert(RecordKind.vomit, none, mapOf("color" to listOf("darkRed"))))
        assertTrue(picksAlert(RecordKind.vomit, mapOf("contents" to listOf("kibble")), mapOf("contents" to listOf("kibble", "foreign"))))
        assertTrue(picksAlert(RecordKind.vomit, none, mapOf("count" to listOf("3"))))
        assertFalse(picksAlert(RecordKind.vomit, none, mapOf("count" to listOf("2"), "color" to listOf("yellow"))))
        assertFalse(picksAlert(RecordKind.vomit, mapOf("color" to listOf("red")), none)) // 풀 때는 안 뜸
        assertFalse(picksAlert(RecordKind.vomit, mapOf("color" to listOf("red")), mapOf("color" to listOf("red"), "count" to listOf("2"))))
        assertTrue(picksAlert(RecordKind.stool, none, mapOf("color" to listOf("black"))))
        assertFalse(picksAlert(RecordKind.stool, none, mapOf("color" to listOf("pale"), "signs" to listOf("blood")))) // 회백색·피는 안 띄움
        assertTrue(picksAlert(RecordKind.urine, none, mapOf("signs" to listOf("straining"))))
        assertFalse(picksAlert(RecordKind.urine, none, mapOf("color" to listOf("pinkRed"), "signs" to listOf("pain"))))
    }
}
