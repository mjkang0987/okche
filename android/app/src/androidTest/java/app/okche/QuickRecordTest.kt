package app.okche

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import org.junit.Rule
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
        deleteRecord(dao, second.copy(slotKey = "slot"), at(2026, 10, 25, 9, 5))
        // 지우면 선택 칸 비움
        assertEquals(listOf<String?>("{}", null), db.query("SELECT data, slot_key FROM record WHERE id = '${second.id}'", null).use { c -> c.moveToFirst(); listOf(c.getString(0), c.getString(1)) })
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

/** 누군지 몰라요로 바로 기록 → 방금 남긴 기록 → 기록 목록에서 누구였는지 정하기 → 지우기 (7-1). iOS testQuickRecordAssignAndDelete와 같은 흐름. */
class QuickRecordFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test fun quickRecordAssignAndDelete() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val unknown = hasText("누군지 몰라요").and(isSelectable())
        waitFor(unknown)
        rule.onNode(hasText("콩이").and(isSelectable())).assertIsSelected() // 지금 보는 아이가 먼저 골라져 있다
        rule.onNode(unknown).performClick()
        rule.onNode(hasContentDescription("구토 사료 · 바로 기록")).performClick()
        waitFor(hasText("아뢰었사옵니다"))

        rule.onNode(hasText("오늘 일기 전체")).performScrollTo().performClick()
        waitFor(hasText("누군지 모름 · 구토"))
        rule.onNode(hasText("구토 1번")).assertExists()
        rule.onNode(hasText("누구였는지 정하기")).performClick()
        waitFor(hasText("누구였나요?"))
        rule.onNode(hasText("콩이").and(isSelectable()).and(hasAnyAncestor(hasContentDescription("이 기록의 아이")))).assertIsSelected() // 지금 보는 아이가 먼저
        rule.onNode(hasText("정하기").and(hasClickAction())).performClick()
        waitFor(hasText("콩이 · 구토"))

        rule.onNode(hasContentDescription("콩이 구토 기록 지우기", substring = true)).performClick()
        waitFor(hasText("이 기록을 지울까요?"))
        rule.onNode(hasText("지우기").and(hasClickAction())).performClick()
        waitFor(hasText("이날 남긴 기록이 없어요."))
        rule.onNode(hasContentDescription("뒤로")).performClick()
        waitFor(unknown)
        rule.onNode(unknown).assertIsSelected() // 목록을 다녀와도 '누군지 몰라요' 그대로
        rule.onAllNodes(hasText("방금 남긴 기록")).assertCountEquals(0) // 오늘 기록이 없으면 숨김
    }
}
