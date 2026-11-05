package app.okche

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.notify.OPEN_MED_PET_ID
import app.okche.notify.medAlarmIntent
import app.okche.notify.medAlerts
import app.okche.notify.rescheduleMedAlarms
import app.okche.store.DoseUnit
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDatabase
import app.okche.store.OkcheDay
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.store.setNotifyAsked
import app.okche.ui.med.notifyAskOn
import app.okche.ui.med.recordSlot
import app.okche.ui.med.remainingSlots
import app.okche.ui.record.shiftDay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** 탕약 알림을 휴대폰에 걸기·지우기, 허락 안내 창, 알림 눌러 시트 열기(plan.md 8-4b). 기기 저장소를 비우고 씀. */
class MedAlarmsTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = OkcheDatabase.get(context).dao()
    private var scenario: ActivityScenario<MainActivity>? = null
    private val kong = Pet(name = "콩이", species = Species.cat, breed = "", furColor = "")
    private val pill = Medication(name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill)

    private fun armed(id: String) = PendingIntent.getBroadcast(context, id.hashCode(), medAlarmIntent(context, id), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE)
    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    /** 콩이 + 프레드니솔론 매일 [time](몇 분 전 0 — 울릴 시각 = 차례). */
    private suspend fun seed(time: String): MedSchedule {
        dao.insert(kong)
        dao.insert(pill)
        val s = MedSchedule(
            petId = kong.id, medicationId = pill.id, amount = 1.0, unit = DoseUnit.pill, frequency = MedFrequency.daily, times = listOf(time),
            reminder = true, leadMinutes = 0, activeFrom = System.currentTimeMillis() - 86_400_000L,
        )
        dao.insert(s)
        return s
    }

    private suspend fun expected() = medAlerts(dao.medSchedules(), dao.medRecords(), dao.pets(), dao.restPeriods(), dao.medications(), true, System.currentTimeMillis())

    @Before fun clear() = runBlocking {
        OkcheDatabase.get(context).clearAllTables()
        dao.loadOrCreateSetting()
        Unit
    }

    @After fun cleanUp() = runBlocking {
        scenario?.close()
        notifyAskOn = false
        OkcheDatabase.get(context).clearAllTables()
        rescheduleMedAlarms(context) // 걸어 둔 알림도 지움
    }

    @Test fun armsFirstAndDropsRecordedSlot() = runBlocking {
        // 두 시간 뒤 차례 — 오늘(6시 기준) 아니면 내일 차례.
        val time = Instant.ofEpochMilli(System.currentTimeMillis() + 2 * 3_600_000L).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("HH:mm"))
        val s = seed(time)
        rescheduleMedAlarms(context)
        val first = expected().first()
        assertNotNull(armed(first.id))
        // 그 차례를 기록하고 다시 걸면 그 알림은 없어지고 다음 날 차례는 남음.
        val now = System.currentTimeMillis()
        val today = OkcheDay.of(now)
        val slot = (0L..1L).flatMap { remainingSlots(listOf(s), emptyList(), kong.id, shiftDay(today, it)) }.first { it.due > now }
        recordSlot(dao, slot)
        rescheduleMedAlarms(context)
        assertNull(armed(first.id))
        val next = expected().first()
        assertTrue(next.fireAt > first.fireAt)
        assertNotNull(armed(next.id))
    }

    @Test fun medTimeOffArmsNothing() = runBlocking {
        seed("21:00")
        rescheduleMedAlarms(context)
        val ids = expected().map { it.id }
        assertTrue(ids.isNotEmpty())
        assertNotNull(armed(ids.first()))
        val setting = dao.loadOrCreateSetting()
        dao.update(setting.copy(notify = setting.notify.copy(medTime = false)))
        rescheduleMedAlarms(context)
        for (id in ids) assertNull(id, armed(id))
    }

    /** 알림 켠 약을 처음 저장하면 안내 창 → '나중에' → 탕약 목록. 다음 저장엔 안 뜸(안드로이드 13 이상, 허락 전). */
    @Test fun askOnceAfterSave() {
        assumeTrue(Build.VERSION.SDK_INT >= 33)
        runBlocking {
            dao.insert(kong)
            dao.insert(pill)
            context.setNotifyAsked(false)
        }
        notifyAskOn = true
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("더보기"))
        rule.onNode(tab("더보기")).performClick()
        waitFor(hasText("약 알림", substring = true))
        rule.onNode(hasText("약 알림", substring = true)).performScrollTo().performClick()
        repeat(2) { round ->
            waitFor(hasText("+ 약 추가"))
            rule.onNode(hasText("+ 약 추가")).performClick()
            waitFor(hasText("프레드니솔론", substring = true).and(hasClickAction()))
            rule.onNode(hasText("프레드니솔론", substring = true).and(hasClickAction())).performScrollTo().performClick()
            rule.onNode(hasText("저장하기").and(hasClickAction())).performClick()
            if (round == 0) {
                waitFor(hasText("약 먹일 시간을 알려 드릴까요?"))
                rule.onNode(hasText("나중에 휴대폰 설정에서도 켤 수 있어요.")).assertExists()
                rule.onNode(hasText("나중에").and(hasClickAction())).performClick()
            }
            waitFor(hasText("먹고 있는 약"))
            rule.onAllNodes(hasText("약 먹일 시간을 알려 드릴까요?")).assertCountEquals(0)
        }
    }

    /** 알림을 누른 것처럼 아이 id를 실어 열면 그 아이의 오늘 먹일 약 시트. */
    @Test fun openFromNotificationShowsSheet() {
        runBlocking { seed("08:00") }
        val intent = Intent(context, MainActivity::class.java).putExtra(OPEN_MED_PET_ID, kong.id)
        scenario = ActivityScenario.launch(intent)
        waitFor(hasText("콩이 · 탕약 오늘 먹일 약"))
        rule.onNode(hasText("다음 차례")).assertExists()
    }
}
