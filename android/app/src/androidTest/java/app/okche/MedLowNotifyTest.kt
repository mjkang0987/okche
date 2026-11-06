package app.okche

import android.content.Intent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.notify.MedAlertKind
import app.okche.notify.OPEN_MED_ID
import app.okche.notify.lowMedAlerts
import app.okche.notify.medAlerts
import app.okche.store.DoseUnit
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.QuietHours
import app.okche.store.Species
import app.okche.ui.med.showNotifyOff
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import app.okche.ui.weighInText
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 남은 약 알림·알림 꺼짐 띠 규칙(plan.md 8-4c). 순수 함수, 하루 1알. */
class MedLowNotifyTest {
    private fun at(d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, 10, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val kong = Pet(name = "콩이", species = Species.cat, breed = "", furColor = "", createdAt = at(1, 9))
    private fun med(stock: Double?) = Medication(
        name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill,
        stockAmount = stock, stockCountedAt = stock?.let { at(20, 9) },
    )
    private fun row(med: Medication, freq: MedFrequency = MedFrequency.daily, reminder: Boolean = true) = MedSchedule(
        petId = kong.id, medicationId = med.id, amount = 1.0, unit = DoseUnit.pill, frequency = freq, times = listOf("21:00"),
        reminder = reminder, activeFrom = at(20, 9),
    )
    private fun low(med: Medication, now: Long, on: Boolean = true, quiet: QuietHours? = null, sent: Set<String> = emptySet()) =
        lowMedAlerts(listOf(med), listOf(row(med)), emptyList(), on, 3, quiet, sent, now)

    @Test fun belowThresholdFiresToday10OrTomorrow() {
        val m = med(2.0) // 2일분
        val before = low(m, at(27, 8))
        assertEquals(listOf(at(27, 10)), before.map { it.fireAt })
        val a = before.single()
        assertEquals(listOf("low.${m.id}", MedAlertKind.low, "약방", "프레드니솔론이 2일분 남았어요", m.id), listOf(a.id, a.kind, a.title, a.body, a.medId))
        assertEquals(listOf(at(28, 10)), low(m, at(27, 12)).map { it.fireAt })
        // 내일로 밀리면 하루 적게, 하루분도 안 남으면 '거의 다 떨어졌어요'.
        assertEquals("프레드니솔론이 1일분 남았어요", low(m, at(27, 12)).single().body)
        assertEquals("프레드니솔론이 거의 다 떨어졌어요", low(med(1.0), at(27, 12)).single().body)
    }

    @Test fun projectedDayUsesThreshold() {
        val list = low(med(5.0), at(27, 12)) // 5일분 → 2일 뒤 3일분
        assertEquals(listOf(at(29, 10)), list.map { it.fireAt })
        assertEquals("프레드니솔론이 3일분 남았어요", list.single().body)
        assertTrue(low(med(10.0), at(27, 12)).isEmpty()) // 7일 뒤는 72시간 밖
    }

    @Test fun sentQuietOffAndNoStock() {
        val m = med(2.0)
        assertTrue(low(m, at(27, 8), sent = setOf("${m.id}|${m.stockCountedAt}")).isEmpty())
        // 남은 양을 다시 적으면(stockCountedAt 바뀜) 다시 보냄.
        assertEquals(1, low(m.copy(stockCountedAt = at(26, 9)), at(27, 8), sent = setOf("${m.id}|${m.stockCountedAt}")).size)
        assertTrue(low(m, at(27, 8), quiet = QuietHours(true, "22:00", "11:00")).isEmpty()) // 자정을 넘는 쉬는 시간
        assertTrue(low(m, at(27, 8), quiet = QuietHours(true, "09:00", "12:00")).isEmpty())
        assertEquals(1, low(m, at(27, 8), quiet = QuietHours(false, "22:00", "11:00")).size)
        assertEquals(1, low(m, at(27, 8), quiet = QuietHours(true, "22:00", "07:00")).size)
        assertTrue(low(m, at(27, 8), on = false).isEmpty())
        assertTrue(low(med(null), at(27, 8)).isEmpty())
        assertTrue(low(m.copy(deletedAt = at(26, 9)), at(27, 8)).isEmpty())
    }

    @Test fun medLimitShrinksByLowAlerts() {
        val m = med(2.0)
        val now = at(27, 8)
        val hourly = row(m, MedFrequency.everyNHours).copy(times = emptyList(), everyHours = 1, firstAt = at(27, 8, 30), leadMinutes = 0)
        val lows = lowMedAlerts(listOf(m), listOf(hourly), emptyList(), true, 3, null, emptySet(), now)
        assertEquals(1, lows.size) // 하루 24알 → 0일분
        val meds = medAlerts(listOf(hourly), emptyList(), listOf(kong), emptyList(), listOf(m), true, now, limit = 63 - lows.size)
        assertEquals(62, meds.count { it.kind == MedAlertKind.due })
        assertEquals(64, lows.size + meds.size)
    }

    @Test fun notifyOffBand() {
        val m = med(null)
        assertTrue(showNotifyOff(listOf(row(m)), enabled = false))
        assertFalse(showNotifyOff(listOf(row(m)), enabled = true))
        assertFalse(showNotifyOff(listOf(row(m, reminder = false)), enabled = false))
        assertFalse(showNotifyOff(listOf(row(m, MedFrequency.asNeeded)), enabled = false))
        assertFalse(showNotifyOff(listOf(row(m).copy(activeUntil = at(26, 9))), enabled = false))
        assertFalse(showNotifyOff(listOf(row(m).copy(deletedAt = at(26, 9))), enabled = false))
    }

    @Test fun weighInLabel() {
        assertEquals("매주 일요일 오전 10시", weighInText(7, "10:00"))
        assertEquals("매주 월요일 오후 7시 30분", weighInText(1, "19:30"))
    }
}

/** 더보기 → 기별 → 탕약 시간 끄기(저장소에 저장), 남은 약 알림 누름 → 약방의 그 약 고치기(8-4c). */
class NotifyFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun toggleMedTimeSaves() {
        val dao = OkcheDatabase.get(context).dao()
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(dao, PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("알림 설정", substring = true))
        rule.onNode(hasText("알림 설정", substring = true)).performScrollTo().performClick()
        waitFor(hasText("탕약 시간"))
        rule.onNode(hasText("매주 일요일 오전 10시")).assertExists()
        rule.onNode(hasText("탕약 시간")).performClick()
        rule.waitUntil(5_000) { runBlocking { !dao.loadOrCreateSetting().notify.medTime } }
        // 밤 알림 쉬기는 처음에 켜짐(22:00~08:00) — 끄면 시작·끝 칸이 숨음.
        val start = SemanticsMatcher.expectValue(SemanticsProperties.ContentDescription, listOf("시작"))
        waitFor(start)
        rule.onNode(hasText("밤에는 알림 쉬기")).performScrollTo().performClick()
        rule.waitUntil(5_000) { runBlocking { !dao.loadOrCreateSetting().quietHours.enabled } }
        rule.onNode(start).assertDoesNotExist()
    }

    @Test fun lowMedTapOpensMedEdit() {
        val dao = OkcheDatabase.get(context).dao()
        val m = Medication(name = "위장약", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill, stockAmount = 2.0)
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(dao, PetForm(name = "콩이"))
            dao.insert(m)
        }
        scenario = ActivityScenario.launch(Intent(context, MainActivity::class.java).putExtra(OPEN_MED_ID, m.id))
        waitFor(hasText("약 고치기"))
    }
}
