package app.okche

import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.AnnotatedString
import androidx.core.content.res.ResourcesCompat
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.ui.theme.OkcheColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
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
        for ((tab, title) in listOf("문안" to "문안", "실록" to "실록", "어진첩" to "어진첩", "더보기" to "더보기", "일기" to "승정원일기")) {
            rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab)
                .and(SemanticsMatcher("label $tab") { n -> n.config.getOrNull(SemanticsProperties.Text)?.any { it.text == tab } == true }))
                .performClick().assertIsSelected()
            rule.onNode(heading.and(SemanticsMatcher("title $title") { n ->
                n.config.getOrNull(SemanticsProperties.Text)?.first()?.text == title
            })).assertExists()
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
