package app.okche

import android.app.UiAutomation
import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.sp
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.setOnboardingDone
import app.okche.ui.theme.OkcheColor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource

class RootTabsTest {
    // 아이가 없어도 시작하기 대신 탭이 뜨게 '먼저 둘러볼게요'를 누른 상태로 연다.
    @get:Rule(order = 0) val browsed = object : ExternalResource() {
        override fun before() = runBlocking { InstrumentationRegistry.getInstrumentation().targetContext.setOnboardingDone(true) }
    }

    @get:Rule(order = 1) val rule = createAndroidComposeRule<MainActivity>()

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

    /** 시작 화면은 DB·설정을 비동기로 읽고 정해서 Compose가 기다려 주지 않는다 — 탭 화면이 뜰 때까지 기다린다. */
    @Before fun waitForTabs() {
        rule.waitUntil(5_000) { rule.onAllNodesWithTag("tabTitle", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    }

    /** 탭 5개를 차례로 눌러 화면 제목이 바뀌고, 머리말이 제목+쉬운 말 한 덩어리 heading으로 읽히는지. */
    @Test fun tabsSwitchAndHeaderIsOneHeading() {
        rule.onNode(heading.and(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(
            AnnotatedString("승정원일기"), AnnotatedString("매일 기록"),
        )))).assertExists()
        // 탭 이름, 제목, 쉬운 말이 있는지(문안은 날짜)
        for ((tab, title, hasSubtitle) in listOf(
            Triple("문안", "문안", true), Triple("실록", "실록", false), Triple("어진첩", "어진첩", true),
            Triple("더보기", "더보기", false), Triple("일기", "승정원일기", true),
        )) {
            rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
                .and(SemanticsMatcher("label $tab") { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == tab } == true }))
                .performClick().assertIsSelected()
            rule.onNode(heading.and(SemanticsMatcher("title $title") { n ->
                val texts = n.config.getOrNull(SemanticsProperties.Text).orEmpty()
                texts.firstOrNull()?.text == title && (texts.size == 2) == hasSubtitle
            })).assertExists()
        }
    }

    /** 제목 줄 높이가 토큰 값(28/36)인지 — 고운바탕 기본 줄 높이(40.5)로 커지지 않게. */
    @Test fun titleLineHeightMatchesToken() {
        val height = rule.onNodeWithTag("tabTitle", useUnmergedTree = true).getBoundsInRoot().let { it.bottom - it.top }
        // 기기 글자 크기 설정을 반영한 36sp(안드로이드 14+ 큰 글자는 덜 커짐)
        val expected = with(rule.density) { 36.sp.toDp() }
        assertEquals(expected.value, height.value, 0.5f)
    }

    /** 기기가 다크 모드여도 상태바 아이콘이 어두운 색(밝은 바탕용)인지. */
    @Test fun systemBarsStayLight() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.run("cmd uimode night yes")
        try {
            // 시스템이 다크 모드로 바뀌며 화면을 스스로 다시 만든다. 여기서 recreate()를 또 부르면 겹쳐서
            // 가끔 이미 닫힌 화면을 건드린다(DESTROYED) — 다시 만들어진 화면이 다크 설정으로 뜰 때까지 기다린다.
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val deadline = SystemClock.uptimeMillis() + 5_000
            var night = false
            while (!night && SystemClock.uptimeMillis() < deadline) {
                instrumentation.waitForIdleSync()
                runCatching {
                    rule.activityRule.scenario.onActivity {
                        night = it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
                    }
                }
                if (!night) SystemClock.sleep(100)
            }
            rule.activityRule.scenario.onActivity { activity ->
                // 다크 모드가 실제로 켜졌는지 먼저 확인(안 켜졌으면 이 테스트는 아무것도 확인하지 못함).
                assertEquals(Configuration.UI_MODE_NIGHT_YES, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertTrue(controller.isAppearanceLightStatusBars)
                assertTrue(controller.isAppearanceLightNavigationBars)
            }
        } finally {
            automation.run("cmd uimode night no")
        }
    }

    /** 고운바탕이 리소스로 들어갔는지, 라이선스가 assets에 있는지, 시작 화면 색이 토큰과 같은지. */
    @Test fun fontLicenseAndWindowColor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(ResourcesCompat.getFont(context, R.font.gowun_batang_bold))
        context.assets.open("OFL-GowunBatang.txt").use { assertNotNull(it.read()) }
        assertEquals(OkcheColor.Hanji.toArgb(), context.getColor(R.color.hanji))
    }

    /** 셸 명령이 끝날 때까지 기다린다(출력을 다 읽어야 끝남). */
    private fun UiAutomation.run(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(executeShellCommand(command)).use { it.readBytes() }
    }
}
