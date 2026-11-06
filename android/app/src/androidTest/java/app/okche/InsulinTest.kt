package app.okche

import android.view.View
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isSelectable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.InsulinPlan
import app.okche.store.InsulinSlot
import app.okche.store.OkcheDatabase
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.diabetes.insulinData
import app.okche.ui.diabetes.insulinHandled
import app.okche.ui.diabetes.insulinNext
import app.okche.ui.diabetes.insulinPlanAt
import app.okche.ui.diabetes.insulinSlotAt
import app.okche.ui.diabetes.insulinTwice
import app.okche.ui.diabetes.insulinUnitsError
import app.okche.ui.diabetes.insulinUnitsNumber
import app.okche.ui.diabetes.insulinValue
import app.okche.ui.diabetes.saveInsulin
import app.okche.ui.main.mainTodayRecords
import app.okche.ui.med.clockText
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
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 인슐린 규칙(plan.md 9-2): 차례·구간, 처리한 상태, 바로 기록 내용, 값 글자, 이미 맞은 경고, 맞은 양 숫자. iOS InsulinRulesTests와 같은 경우. */
class InsulinRulesTest {
    private lateinit var db: OkcheDatabase
    private val dao get() = db.dao()
    private val seoul = ZoneId.of("Asia/Seoul")
    private fun at(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 11, d, h, min).atZone(seoul).toInstant().toEpochMilli()
    private val plan = InsulinPlan(id = "3f2a8c1e-0000-4000-8000-00000000abcd", petId = "kong", name = "글라진 인슐린",
        slots = listOf(InsulinSlot("08:00", 2.0), InsulinSlot("20:00", 1.5)), activeFrom = at(1, 0))
    private fun insulin(status: String, occurredAt: Long, petId: String = "kong", units: Double = 2.0, planId: String? = plan.id) =
        Record(petId = petId, kind = RecordKind.insulin, occurredAt = occurredAt, data = insulinData(planId, units, status, null))

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, OkcheDatabase::class.java).build()
    }

    @After fun close() = db.close()

    @Test fun windowAndSlot() {
        val morning = insulinSlotAt(plan, at(6, 9), seoul)!!
        assertEquals("08:00", morning.time)
        assertEquals(2.0, morning.units, 0.0)
        assertEquals(at(6, 7) to at(6, 19), morning.start to morning.end)
        assertEquals(at(6, 8), morning.occurrence)
        assertEquals("${plan.id}|2026-11-06|08:00", morning.key)
        assertEquals(at(6, 20), insulinNext(morning))
        assertEquals("오후 8:00", clockText(insulinNext(morning), seoul))
        // 다음 날 새벽 2시 = 전날 20:00 차례(19:00~07:00) — 날은 차례 시각의 6시 기준 날.
        val evening = insulinSlotAt(plan, at(7, 2), seoul)!!
        assertEquals("20:00", evening.time)
        assertEquals(1.5, evening.units, 0.0)
        assertEquals(at(6, 19) to at(7, 7), evening.start to evening.end)
        assertEquals("${plan.id}|2026-11-06|20:00", evening.key)
        assertEquals("08:00", insulinSlotAt(plan, at(7, 7), seoul)!!.time) // 07:00부터 아침 차례
        assertEquals("20:00", insulinSlotAt(plan, at(7, 6, 59), seoul)!!.time)
        assertNull(insulinSlotAt(plan.copy(slots = emptyList()), at(6, 9), seoul))
    }

    @Test fun handled() {
        val slot = insulinSlotAt(plan, at(6, 9), seoul)!!
        val full = insulin("full", at(6, 8, 2))
        assertEquals(full.id, insulinHandled("kong", listOf(full), slot)?.id)
        val skipped = insulin("skipped", at(6, 8, 2), units = 0.0)
        assertEquals(skipped.id, insulinHandled("kong", listOf(skipped), slot)?.id)
        // 다른 기기에서 온 기록(그냥 다른 기록) — 가장 늦은 것.
        val other = insulin("partial", at(6, 8, 30)).copy(createdAt = at(6, 10))
        assertEquals(other.id, insulinHandled("kong", listOf(full, other), slot)?.id)
        // 그날 계획을 바꿔도(새 계획 id) 옛 계획 기록으로 처리한 상태 그대로.
        val changed = plan.copy(id = "9b1e0000-0000-4000-8000-000000000001", slots = listOf(InsulinSlot("08:00", 3.0), InsulinSlot("20:00", 3.0)), activeFrom = at(6, 8, 10))
        assertEquals(full.id, insulinHandled("kong", listOf(full), insulinSlotAt(changed, at(6, 9), seoul)!!)?.id)
        assertNull(insulinHandled("kong", listOf(insulin("full", at(6, 6, 59))), slot)) // 구간 밖
        assertNull(insulinHandled("kong", listOf(full.copy(deletedAt = at(6, 9))), slot)) // 지운 기록
        assertNull(insulinHandled("kong", listOf(insulin("full", at(6, 8), petId = "bori")), slot)) // 다른 아이
    }

    @Test fun quickAndSheetData() = runBlocking {
        val now = at(6, 8, 2)
        val full = saveInsulin(dao, "kong", listOf(plan), 2.0, "full", null, now, now = now, zone = seoul)
        assertEquals("""{"planId":"${plan.id}","status":"full","units":2}""", full.data)
        assertEquals("${plan.id}|2026-11-06|08:00", full.slotKey)
        assertEquals(now, full.occurredAt)
        assertEquals("2026-11-06", full.day)
        val skipped = saveInsulin(dao, "kong", listOf(plan), 2.0, "skipped", null, now, now = now, zone = seoul)
        assertEquals("""{"planId":"${plan.id}","status":"skipped","units":0}""", skipped.data)
        // 시트: 밥·일부만, 새벽 2시면 전날 저녁 차례.
        val sheet = saveInsulin(dao, "kong", listOf(plan), 1.5, "partial", "none", at(7, 2), now = at(7, 3), zone = seoul)
        assertEquals("""{"mealBefore":"none","planId":"${plan.id}","status":"partial","units":1.5}""", sheet.data)
        assertEquals("${plan.id}|2026-11-06|20:00", sheet.slotKey)
        assertEquals("2026-11-06", sheet.day)
        // 계획 없음 → planId·slotKey 없음.
        val noPlan = saveInsulin(dao, "kong", emptyList(), 2.0, "full", null, now, now = now, zone = seoul)
        assertEquals("""{"status":"full","units":2}""", noPlan.data)
        assertNull(noPlan.slotKey)
        // 고치기: 시간을 바꾸면 그 시각 구간의 차례로.
        val edited = saveInsulin(dao, "kong", listOf(plan), 2.0, "full", "all", at(6, 20, 5), editing = full, now = at(6, 21), zone = seoul)
        assertEquals(full.id, edited.id)
        assertEquals("${plan.id}|2026-11-06|20:00", edited.slotKey)
        assertEquals("2단위 맞음 · 밥 다 먹음", insulinValue(dao.record(full.id)!!))
    }

    /** 그때 쓰던 계획: 오늘 양을 바꿔 A가 닫히고 B가 열려도 어제 기록 고치기는 A 그대로, 뒤늦게 남긴 어제 기록은 A로. */
    @Test fun planInForceAtTheTime() = runBlocking {
        val change = at(6, 10)
        val a = plan.copy(activeUntil = change)
        val b = plan.copy(id = "9b1e0000-0000-4000-8000-000000000002", slots = listOf(InsulinSlot("08:00", 3.0), InsulinSlot("20:00", 3.0)), activeFrom = change)
        val plans = listOf(a, b)
        assertEquals(a.id, insulinPlanAt(plans, "kong", at(5, 9))?.id)
        assertEquals(a.id, insulinPlanAt(plans, "kong", change - 1)?.id)
        assertEquals(b.id, insulinPlanAt(plans, "kong", change)?.id)
        assertNull(insulinPlanAt(plans, "kong", at(1, 0) - 1)) // 계획 전
        assertNull(insulinPlanAt(plans, "bori", at(6, 11)))
        assertNull(insulinPlanAt(listOf(b.copy(deletedAt = change)), "kong", at(6, 11)))
        // 같은 activeFrom이면 늦은 updatedAt.
        val later = b.copy(id = "9b1e0000-0000-4000-8000-000000000003", updatedAt = b.updatedAt + 1)
        assertEquals(later.id, insulinPlanAt(listOf(b, later), "kong", at(6, 11))?.id)

        val now = at(6, 11)
        val yesterday = saveInsulin(dao, "kong", listOf(a), 2.0, "full", null, at(5, 8, 2), now = at(5, 8, 2), zone = seoul)
        // 시간 그대로 고치기 → A의 planId·slotKey 그대로.
        val edited = saveInsulin(dao, "kong", plans, 1.5, "partial", null, yesterday.occurredAt, editing = yesterday, now = now, zone = seoul)
        assertEquals("""{"planId":"${a.id}","status":"partial","units":1.5}""", edited.data)
        assertEquals(yesterday.slotKey, edited.slotKey)
        // 어제 안에서 시간을 바꿔도 그때 계획 A의 차례.
        val moved = saveInsulin(dao, "kong", plans, 1.5, "partial", null, at(5, 20, 5), editing = edited, now = now, zone = seoul)
        assertEquals("${a.id}|2026-11-05|20:00", moved.slotKey)
        // 뒤늦게 남긴 어제 저녁 기록 → A, 오늘 지금 기록 → B.
        val backdated = saveInsulin(dao, "kong", plans, 2.0, "full", null, at(5, 20, 1), now = now, zone = seoul)
        assertEquals("""{"planId":"${a.id}","status":"full","units":2}""", backdated.data)
        assertEquals("${a.id}|2026-11-05|20:00", backdated.slotKey)
        assertEquals("${b.id}|2026-11-06|08:00", saveInsulin(dao, "kong", plans, 3.0, "full", null, now, now = now, zone = seoul).slotKey)
    }

    @Test fun valueText() {
        val r = insulin("full", at(6, 8))
        assertEquals("2단위 맞음", insulinValue(r))
        assertEquals("1.5단위 일부만 · 밥 안 먹음", insulinValue(r.copy(data = insulinData(plan.id, 1.5, "partial", "none"))))
        assertEquals("건너뜀 · 밥 남김", insulinValue(r.copy(data = insulinData(plan.id, 2.0, "skipped", "some"))))
        assertEquals("건너뜀", recordValue(r.copy(data = insulinData(plan.id, 2.0, "skipped", null))))
        assertEquals("2단위 맞음", recordValue(r)) // 이름 '인슐린'은 붙이지 않음(앞에서 한 번)
        assertEquals("", insulinValue(r.copy(data = "{}")))
        assertEquals(listOf(r.id), mainTodayRecords(listOf(r), "kong", r.day).map { it.id }) // 문안 오늘 기록에도
    }

    @Test fun twiceWarning() {
        val given = insulin("full", at(6, 8, 2))
        assertEquals(given.id, insulinTwice("kong", plan, listOf(given), at(6, 8, 30), null, seoul)?.id)
        assertNull(insulinTwice("kong", plan, listOf(given), at(6, 8, 30), given.id, seoul)) // 고치는 그 기록은 빼고
        assertNull(insulinTwice("kong", plan, listOf(insulin("skipped", at(6, 8, 2), units = 0.0)), at(6, 8, 30), null, seoul)) // 건너뜀만
        assertNull(insulinTwice("kong", plan, listOf(given), at(6, 19, 30), null, seoul)) // 다른 구간
        assertNull(insulinTwice("kong", null, listOf(given), at(6, 8, 30), null, seoul)) // 계획 없음
    }

    @Test fun unitsNumber() {
        assertNull(insulinUnitsNumber("0"))
        assertEquals(0.5, insulinUnitsNumber("0.5")!!, 0.0)
        assertEquals(100.0, insulinUnitsNumber("100")!!, 0.0)
        assertNull(insulinUnitsNumber("100.5"))
        assertNull(insulinUnitsNumber("-1"))
        assertNull(insulinUnitsNumber("abc"))
        assertEquals(2.0, insulinUnitsNumber(" 2 ")!!, 0.0)
        assertNull(insulinUnitsError(""))
        assertEquals("0보다 크고 100 이하인 숫자로 넣어 주세요", insulinUnitsError("0"))
        assertNull(insulinUnitsError("1.5"))
    }
}

/** 일기 인슐린 줄·시트(9-2): 맞음 → 처리한 상태·알림 → 되돌리기 → 지우기 → 버튼이 돌아옴, ⋯ → 시트 → 시간을 맞은 시간대로 → 경고. */
class InsulinFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null
    private val give = hasContentDescription("2단위 맞음 · 바로 기록", substring = true)

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    private fun setPicker(block: (View) -> Unit) = object : ViewAction {
        override fun getConstraints() = isAssignableFrom(View::class.java)
        override fun getDescription() = "고르기 값 바꾸기"
        override fun perform(uiController: UiController, view: View) = block(view)
    }

    /** 처리한 뒤 ⋯은 새 기록 시트 — 열자마자 이미 맞은 경고(보드와 같음). 처리한 칩은 고치기 시트(경고 없음). */
    @Test fun moreOpensNewSheetWithWarning() {
        val plan = InsulinPlan(petId = "", slots = listOf(InsulinSlot("08:00", 2.0), InsulinSlot("20:00", 2.0)), activeFrom = System.currentTimeMillis() - 86_400_000)
        val slot = insulinSlotAt(plan, System.currentTimeMillis())!!
        val given = maxOf(slot.start, System.currentTimeMillis() - 60_000)
        runBlocking {
            val dao = OkcheDatabase.get(context).dao()
            OkcheDatabase.get(context).clearAllTables()
            registerPet(dao, PetForm(name = "콩이"))
            val pet = dao.pets().single()
            dao.update(pet.copy(hasDiabetes = true))
            dao.insert(plan.copy(petId = pet.id))
            dao.insert(Record(petId = pet.id, kind = RecordKind.insulin, occurredAt = given, data = insulinData(plan.id, 2.0, "full", null)))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        val chip = hasContentDescription("에 2단위 맞음 · 눌러서 고치기", substring = true)
        waitFor(chip)
        val warning = hasText("에 이미 맞은 기록이 있어요", substring = true)
        rule.onNode(hasContentDescription("인슐린 자세히 남기기")).performScrollTo().performClick()
        waitFor(hasText("콩이 · 인슐린"))
        waitFor(warning)
        rule.onNode(hasText("지금 · ", substring = true)).assertExists() // 새 기록(지금 시각)
        rule.onNode(hasContentDescription("닫기")).performClick()
        rule.waitUntil(5_000) { rule.onAllNodes(hasText("콩이 · 인슐린")).fetchSemanticsNodes().isEmpty() }
        rule.onNode(chip).performScrollTo().performClick()
        waitFor(hasText("콩이 · 인슐린"))
        rule.onAllNodes(warning).fetchSemanticsNodes().let { assertEquals(0, it.size) } // 고치는 그 기록은 빼고
    }

    @Test fun giveUndoThenSheetWarning() {
        val zone = ZoneId.systemDefault()
        val plan = InsulinPlan(petId = "", slots = listOf(InsulinSlot("08:00", 2.0), InsulinSlot("20:00", 2.0)), activeFrom = System.currentTimeMillis() - 86_400_000)
        // 앞 구간(지금 구간 시작 − 12시간)의 차례에 맞은 기록 — 시트 시간을 그 구간으로 바꾸면 경고.
        val slot = insulinSlotAt(plan, System.currentTimeMillis())!!
        val before = slot.occurrence - 12 * 3_600_000L
        val pick = Instant.ofEpochMilli(before + 30 * 60_000L).atZone(zone).toLocalDateTime()
        runBlocking {
            val dao = OkcheDatabase.get(context).dao()
            OkcheDatabase.get(context).clearAllTables()
            registerPet(dao, PetForm(name = "콩이"))
            val pet = dao.pets().single()
            dao.update(pet.copy(hasDiabetes = true))
            dao.insert(plan.copy(petId = pet.id))
            dao.insert(Record(petId = pet.id, kind = RecordKind.insulin, occurredAt = before, data = insulinData(plan.id, 2.0, "full", null)))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(hasText("누군지 몰라요").and(isSelectable()))
        waitFor(give)
        rule.onNode(give).performScrollTo().performClick()
        waitFor(hasContentDescription("에 2단위 맞음 · 눌러서 고치기", substring = true))
        val handled = SemanticsMatcher("✓ HH:mm 맞음") { n ->
            n.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.any { Regex("✓ \\d\\d:\\d\\d 맞음").matches(it.text) } == true
        }
        rule.onNode(handled).assertExists()
        rule.onAllNodes(give).fetchSemanticsNodes().let { assertEquals(0, it.size) } // 맞음 버튼은 사라짐
        rule.onNode(hasText("다음\n", substring = true)).assertExists()
        waitFor(hasText("인슐린 2단위 맞음 · ", substring = true)) // 탭 바 위 알림
        waitFor(hasText("콩이 · 인슐린 2단위 맞음", substring = true)) // 방금 남긴 기록 — 이름 한 번
        rule.onNode(hasText("아뢰었사옵니다")).assertExists()
        rule.onNode(hasText("되돌리기").and(hasClickAction())).performClick()
        waitFor(hasText("이 기록을 지울까요?"))
        rule.onNode(hasText("지우기").and(hasClickAction())).performClick()
        waitFor(hasText("기록을 지웠어요"))
        waitFor(give) // 버튼이 돌아옴

        // 맨 아래 안내까지 내려 ⋯가 되돌리기 알림에 가리지 않게.
        rule.onNode(hasText("누르면 바로 기록돼요.", substring = true)).performScrollTo()
        rule.onNode(hasContentDescription("인슐린 자세히 남기기")).performClick()
        waitFor(hasText("콩이 · 인슐린"))
        rule.onNode(hasText("인슐린 · 정해 둔 양 2단위")).assertExists()
        val warning = hasText("에 이미 맞은 기록이 있어요", substring = true)
        rule.onAllNodes(warning).fetchSemanticsNodes().let { assertEquals(0, it.size) }
        rule.onNode(hasText("시간 바꾸기")).performClick()
        onView(isAssignableFrom(DatePicker::class.java)).inRoot(isDialog()).perform(setPicker { (it as DatePicker).updateDate(pick.year, pick.monthValue - 1, pick.dayOfMonth) })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        onView(isAssignableFrom(TimePicker::class.java)).inRoot(isDialog()).perform(setPicker { (it as TimePicker).hour = pick.hour; it.minute = pick.minute })
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        val clock = clockText(before, zone)
        waitFor(hasText("이 시간대($clock 차례)에 이미 맞은 기록이 있어요", substring = true))
    }
}
