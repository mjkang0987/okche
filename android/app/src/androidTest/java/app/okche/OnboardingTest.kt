package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.OkcheDatabase
import app.okche.store.setOnboardingDone
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 첫 실행: 시작하기 → 아이 등록 → 탭, 둘러보기. 앱 저장소를 비우고 시작한다. */
class OnboardingTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun freshStart() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            context.setOnboardingDone(false)
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        // 첫 화면은 저장소를 읽은 뒤 정해진다(Compose 유휴 판단에 안 잡힘).
        waitFor(hasText("아이 등록하고 시작하기"))
    }

    @After fun close() = scenario.close()

    private val tabRecord = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText("일기"))

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test fun registerThenTabs() {
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput("콩이")
        rule.onNodeWithText("모시기").performScrollTo().performClick()
        waitFor(tabRecord)
        assertEquals(listOf("콩이"), runBlocking { OkcheDatabase.get(context).dao().pets().map { it.name } })
    }

    @Test fun blankNameShowsErrorAndStays() {
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        rule.onNodeWithText("모시기").performScrollTo().performClick()
        waitFor(hasText("이름을 넣어 주세요"))
        rule.onNode(tabRecord).assertDoesNotExist()
        assertEquals(0, runBlocking { OkcheDatabase.get(context).dao().pets().size })
    }

    @Test fun closeGoesBackToStart() {
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        rule.onNodeWithContentDescription("닫기").performClick()
        rule.onNodeWithText("아이 등록하고 시작하기").assertExists()
    }

    @Test fun browseIsRememberedAfterRelaunch() {
        rule.onNodeWithText("먼저 둘러볼게요").performScrollTo().performClick()
        waitFor(tabRecord)
        scenario.close()
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tabRecord)
        rule.onNodeWithText("아이 등록하고 시작하기").assertDoesNotExist()
    }

    /** 선택 칩(성별)은 다시 누르면 안 고름, 필수 칩(주사기)은 그대로. */
    @Test fun optionalChipDeselects() {
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        rule.onNodeWithText("여아").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("여아").performClick().assertIsNotSelected()
        rule.onNode(isToggleable().and(hasText("당뇨가 있어요"))).performScrollTo().performClick()
        rule.onNodeWithText("잘 모르겠어요").performScrollTo().assertIsSelected().performClick().assertIsSelected()
    }

    @Test fun diabetesSwitchShowsInsulin() {
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        rule.onNodeWithText("인슐린").assertDoesNotExist()
        rule.onNode(isToggleable().and(hasText("당뇨가 있어요"))).performScrollTo().performClick()
        rule.onNodeWithText("인슐린").assertExists()
        rule.onNode(hasText("첫 번째 시간")).assertExists()
        rule.onNode(hasText("오전 8:00")).assertExists()
        rule.onNode(hasText("오후 8:00")).assertExists()
    }

    @Test fun speciesChangeSwitchesBreedList() {
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
        rule.onNodeWithText("코리안숏헤어").assertExists()
        rule.onNodeWithText("강아지").performClick()
        rule.onNodeWithText("코리안숏헤어").assertDoesNotExist()
        rule.onNodeWithText("말티즈").performClick()
        rule.onNodeWithText("진돗개").assertExists()
        rule.onNodeWithText("흰색").assertExists()
    }
}
