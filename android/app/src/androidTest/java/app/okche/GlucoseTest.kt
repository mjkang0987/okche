package app.okche

import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isNotSelected
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.InsulinPlan
import app.okche.store.InsulinSlot
import app.okche.store.OkcheDatabase
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.diabetes.GlucoseSign
import app.okche.ui.diabetes.GlucoseTiming
import app.okche.ui.diabetes.glucoseData
import app.okche.ui.diabetes.glucoseError
import app.okche.ui.diabetes.glucoseGuess
import app.okche.ui.diabetes.glucoseNumber
import app.okche.ui.diabetes.glucoseValue
import app.okche.ui.diabetes.insulinWindow
import app.okche.ui.diabetes.openInsulinPlan
import app.okche.ui.diabetes.saveGlucose
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import app.okche.ui.record.recordValue
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 혈당 규칙(plan.md 9-1): 숫자, 지금 차례 구간, 남기기 짐작, 값 글자, 저장. iOS GlucoseRulesTests와 같은 경우. */
class GlucoseRulesTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 11, d, h, min).atZone(seoul).toInstant().toEpochMilli()
    private fun plan(vararg times: String, from: Long = at(1, 0), updated: Long = from) =
        InsulinPlan(petId = "kong", slots = times.map { InsulinSlot(it, 2.0) }, activeFrom = from, createdAt = updated)
    private fun insulin(status: String, occurredAt: Long, petId: String = "kong") =
        Record(petId = petId, kind = RecordKind.insulin, occurredAt = occurredAt, data = """{"status":"$status"}""")

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun number() {
        assertNull(glucoseNumber("0"))
        assertEquals(1, glucoseNumber("1"))
        assertEquals(999, glucoseNumber("999"))
        assertNull(glucoseNumber("1000"))
        assertNull(glucoseNumber("1.5"))
        assertEquals(186, glucoseNumber(" 186 "))
        assertNull(glucoseNumber(""))
        assertNull(glucoseError("")) // 빈칸은 안내 없음
        assertEquals("1에서 999 사이로 넣어 주세요", glucoseError("1000"))
        assertNull(glucoseError(" 186 "))
    }

    @Test fun window() {
        val p = plan("08:00", "20:00")
        assertEquals(at(6, 7) to at(6, 19), insulinWindow(p, at(6, 7), seoul)) // 7:00부터 아침 차례
        assertEquals(at(5, 19) to at(6, 7), insulinWindow(p, at(6, 6, 59), seoul)) // 6:59는 아직 어제 저녁 차례
        assertEquals(at(6, 19) to at(7, 7), insulinWindow(p, at(6, 19), seoul))
        assertEquals(at(6, 19) to at(7, 7), insulinWindow(p, at(7, 1), seoul)) // 자정 넘김
        // 자정 가까운 차례: 00:30 하나면 23:30~다음 날 23:30.
        assertEquals(at(6, 23, 30) to at(7, 23, 30), insulinWindow(plan("00:30"), at(6, 23, 45), seoul))
        assertNull(insulinWindow(plan(), at(6, 8), seoul))
    }

    @Test fun openPlanLatest() {
        val old = plan("08:00", from = at(1, 0))
        val newer = plan("09:00", from = at(2, 0))
        val sameFromLater = plan("10:00", from = at(2, 0), updated = at(3, 0))
        val closed = plan("11:00", from = at(4, 0)).copy(activeUntil = at(5, 0))
        assertEquals(sameFromLater.id, openInsulinPlan(listOf(old, newer, sameFromLater, closed), "kong")?.id)
        assertNull(openInsulinPlan(listOf(old), "bori"))
    }

    @Test fun guess() {
        val plans = listOf(plan("08:00", "20:00"))
        val now = at(6, 9)
        assertEquals(GlucoseTiming.afterInsulin, glucoseGuess("kong", plans, listOf(insulin("full", at(6, 8))), now, seoul))
        assertEquals(GlucoseTiming.afterInsulin, glucoseGuess("kong", plans, listOf(insulin("partial", at(6, 7, 30))), now, seoul))
        assertNull(glucoseGuess("kong", plans, listOf(insulin("skipped", at(6, 8))), now, seoul)) // 건너뜀만 → 묻기
        assertNull(glucoseGuess("kong", plans, emptyList(), now, seoul))
        assertEquals(GlucoseTiming.other, glucoseGuess("kong", emptyList(), emptyList(), now, seoul)) // 계획 없음
        assertNull(glucoseGuess("kong", plans, listOf(insulin("full", at(6, 6, 59))), now, seoul)) // 구간 바로 밖
        assertNull(glucoseGuess("kong", plans, listOf(insulin("full", at(6, 8), petId = "bori")), now, seoul)) // 다른 아이
        assertNull(glucoseGuess("kong", plans, listOf(insulin("full", at(6, 8)).copy(deletedAt = at(6, 8, 5))), now, seoul)) // 지운 기록
    }

    @Test fun valueText() {
        val r = Record(petId = "kong", kind = RecordKind.glucose, occurredAt = at(6, 9),
            data = glucoseData(186, GlucoseTiming.afterInsulin, setOf(GlucoseSign.seizure, GlucoseSign.weak, GlucoseSign.wobbly)))
        assertEquals("186 · 맞고 난 뒤 · 기운 없음 · 비틀거림 · 경련", glucoseValue(r))
        assertEquals("186 · 맞기 전", recordValue(r.copy(data = glucoseData(186, GlucoseTiming.beforeInsulin, emptySet()))))
        assertEquals("90 · 그 밖", recordValue(r.copy(data = glucoseData(90, GlucoseTiming.other, emptySet()))))
        assertEquals("", glucoseValue(r.copy(data = "{}")))
    }

    @Test fun saveJsonAndEdit() = runBlocking {
        val saved = saveGlucose(dao, "kong", 186, GlucoseTiming.afterInsulin, setOf(GlucoseSign.wobbly), at(6, 5, 59), now = at(6, 6), zone = seoul)
        assertEquals("""{"mgdl":186,"signs":["wobbly"],"timing":"afterInsulin"}""", saved.data)
        assertEquals("2026-11-05", saved.day) // 6시 전은 전날
        assertEquals("""{"mgdl":90,"signs":[],"timing":"other"}""", glucoseData(90, GlucoseTiming.other, emptySet()))
        val edited = saveGlucose(dao, "kong", 120, GlucoseTiming.beforeInsulin, emptySet(), at(6, 7), editing = saved, now = at(6, 8), zone = seoul)
        assertEquals(saved.id, edited.id)
        assertEquals("2026-11-06", edited.day) // 시간을 바꾸면 날도
        assertEquals(at(6, 8), edited.updatedAt)
        assertEquals("120 · 맞기 전", glucoseValue(dao.record(saved.id)!!))
    }
}

/** 일기 혈당 줄·묻기 창·혈당 시트(9-1). iOS 화면 테스트와 같은 흐름. */
class GlucoseFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val field = hasContentDescription("혈당 수치 (mg/dL)")

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    /** 당뇨면 열린 인슐린 계획(08:00·20:00, 기록 없음 → 남기기는 묻기)과 함께. */
    private fun launch(diabetic: Boolean, names: List<String> = listOf("콩이")) {
        runBlocking {
            val dao = OkcheDatabase.get(context).dao()
            OkcheDatabase.get(context).clearAllTables()
            for (name in names) registerPet(dao, PetForm(name = name))
            if (diabetic) for (pet in dao.pets()) {
                dao.update(pet.copy(hasDiabetes = true))
                dao.insert(InsulinPlan(petId = pet.id, slots = listOf(InsulinSlot("08:00", 2.0), InsulinSlot("20:00", 2.0)), activeFrom = System.currentTimeMillis() - 86_400_000))
            }
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasText("누군지 몰라요").and(isSelectable()))
    }

    private fun typeAndLeave() {
        waitFor(field)
        rule.onNode(field).performTextInput("186")
        rule.onNode(hasContentDescription("혈당 남기기")).performClick()
        waitFor(hasText("혈당 186 · 언제 쟀나요?"))
    }

    @Test fun askThenSave() {
        launch(diabetic = true)
        typeAndLeave()
        rule.onNode(hasText("이번 인슐린 기록이 아직 없어요. 고르면 바로 남아요.")).assertExists()
        rule.onNode(hasText("맞고 난 뒤").and(hasClickAction())).performClick()
        waitFor(hasText("혈당 186 · 맞고 난 뒤")) // 탭 바 위 알림
        waitFor(hasText("콩이 · 혈당 186 · 맞고 난 뒤", substring = true)) // 방금 남긴 기록
        rule.onNode(hasText("아뢰었사옵니다")).assertExists()
        rule.onNode(field).assert(hasText("")) // 칸 비움
    }

    @Test fun cancelKeepsValue() {
        launch(diabetic = true)
        typeAndLeave()
        rule.onNode(hasText("그만두기").and(hasClickAction())).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("혈당 186 · 언제 쟀나요?")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(field).assert(hasText("186"))
        rule.onAllNodes(hasText("방금 남긴 기록")).assertCountEquals(0)
    }

    /** 다른 아이로 바꾸면 앞 아이 수치는 비움(그대로 남기면 남의 아이에 남음). */
    @Test fun petChangeClearsValue() {
        launch(diabetic = true, names = listOf("콩이", "보리"))
        waitFor(field)
        rule.onNode(field).performTextInput("186")
        rule.onNode(isSelectable().and(isNotSelected()).and(hasText("콩이").or(hasText("보리")))).performClick() // 안 고른 아이로
        rule.waitUntil(5_000) { rule.onAllNodes(field.and(hasText("186"))).fetchSemanticsNodes().isEmpty() }
        rule.onNode(field).assert(hasText(""))
    }

    @Test fun noRowForNonDiabeticOrUnknown() {
        launch(diabetic = false)
        rule.onAllNodes(field).assertCountEquals(0)
        scenario?.close()
        launch(diabetic = true)
        waitFor(field)
        rule.onNode(hasText("누군지 몰라요").and(isSelectable())).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(field).fetchSemanticsNodes().isEmpty() }
    }

    @Test fun sheetWarningAfterSign() {
        launch(diabetic = true)
        waitFor(field)
        rule.onNode(hasContentDescription("혈당 자세히 남기기")).performClick()
        waitFor(hasText("콩이 · 혈당"))
        val warning = hasText("저혈당일 수 있어요", substring = true)
        rule.onAllNodes(warning).assertCountEquals(0)
        rule.onNode(hasText("기록하기").and(hasClickAction())).assert(SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.Disabled))
        rule.onNode(hasText("비틀거리거나 떨어요")).performScrollTo().performClick()
        waitFor(warning)
    }
}
