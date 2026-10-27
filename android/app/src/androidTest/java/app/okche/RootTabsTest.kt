package app.okche

import android.content.res.Configuration
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.ui.theme.OkcheColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test

class RootTabsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private val heading = SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

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
        val fontScale = rule.activity.resources.configuration.fontScale
        assumeTrue("기기 글자 크기 1.0에서만", fontScale == 1f)
        assertEquals(36f, height.value, 0.5f)
    }

    /** 기기가 다크 모드여도 상태바 아이콘이 어두운 색(밝은 바탕용)인지. */
    @Test fun systemBarsStayLight() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.executeShellCommand("cmd uimode night yes").close()
        try {
            rule.activityRule.scenario.recreate()
            rule.activityRule.scenario.onActivity { activity ->
                // 다크 모드가 실제로 켜졌는지 먼저 확인(안 켜졌으면 이 테스트는 아무것도 확인하지 못함).
                assertEquals(Configuration.UI_MODE_NIGHT_YES, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                assertTrue(controller.isAppearanceLightStatusBars)
                assertTrue(controller.isAppearanceLightNavigationBars)
            }
        } finally {
            automation.executeShellCommand("cmd uimode night no").close()
        }
    }

    /** 고운바탕이 리소스로 들어갔는지, 라이선스가 assets에 있는지, 시작 화면 색이 토큰과 같은지. */
    @Test fun fontLicenseAndWindowColor() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertNotNull(ResourcesCompat.getFont(context, R.font.gowun_batang_bold))
        context.assets.open("OFL-GowunBatang.txt").use { assertNotNull(it.read()) }
        assertEquals(OkcheColor.Hanji.toArgb(), context.getColor(R.color.hanji))
    }
}
