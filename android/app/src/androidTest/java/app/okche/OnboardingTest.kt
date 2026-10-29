package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasStateDescription
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
import app.okche.store.Pet
import app.okche.store.Species
import app.okche.store.setOnboardingDone
import kotlinx.coroutines.runBlocking
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.withId
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** 첫 실행: 시작하기 → 아이 등록 → 탭, 둘러보기. 앱 저장소를 비우고 시작한다. */
class OnboardingTest {
    @get:Rule val rule = createEmptyComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before fun clearStore() = runBlocking {
        OkcheDatabase.get(context).clearAllTables()
        context.setOnboardingDone(false)
    }

    @After fun close() {
        scenario?.close()
    }

    private fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    /** 빈 저장소로 열어 시작하기가 뜰 때까지 기다린다(첫 화면은 저장소를 읽은 뒤 정해져 Compose 유휴 판단에 안 잡힘). */
    private fun start() {
        launch()
        waitFor(hasText("아이 등록하고 시작하기"))
    }

    private fun startRegister() {
        start()
        rule.onNodeWithText("아이 등록하고 시작하기").performScrollTo().performClick()
    }

    private val tabRecord = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText("일기"))

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }

    @Test fun registerThenTabs() {
        startRegister()
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput("콩이")
        rule.onNodeWithText("모시기").performScrollTo().performClick()
        waitFor(tabRecord)
        assertEquals(listOf("콩이"), runBlocking { OkcheDatabase.get(context).dao().pets().map { it.name } })
    }

    @Test fun blankNameShowsErrorAndStays() {
        startRegister()
        rule.onNodeWithText("모시기").performScrollTo().performClick()
        waitFor(hasText("이름을 넣어 주세요"))
        rule.onNode(tabRecord).assertDoesNotExist()
        assertEquals(0, runBlocking { OkcheDatabase.get(context).dao().pets().size })
        // 칸을 고치면 그 칸의 오류는 바로 사라진다.
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput("콩")
        rule.onNodeWithText("이름을 넣어 주세요").assertDoesNotExist()
    }

    /** 고른 생일은 '생일 지우기'로 안 고름으로 되돌린다. 날짜 창은 기본 창이라 Espresso로 '확인'(오늘)을 누른다. */
    @Test fun birthDateCanBeCleared() {
        startRegister()
        rule.onNodeWithContentDescription("생일 지우기").assertDoesNotExist()
        rule.onNodeWithText("날짜를 골라 주세요").performScrollTo().performClick()
        onView(withId(android.R.id.button1)).inRoot(isDialog()).perform(click())
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN))
        rule.onNodeWithText(today).assertExists()
        rule.onNodeWithContentDescription("생일 지우기").performClick()
        rule.onNodeWithText("날짜를 골라 주세요").assertExists()
        rule.onNodeWithText(today).assertDoesNotExist()
        rule.onNodeWithContentDescription("생일 지우기").assertDoesNotExist()
    }

    @Test fun closeGoesBackToStart() {
        startRegister()
        rule.onNodeWithContentDescription("닫기").performClick()
        rule.onNodeWithText("아이 등록하고 시작하기").assertExists()
    }

    @Test fun browseIsRememberedAfterRelaunch() {
        start()
        rule.onNodeWithText("먼저 둘러볼게요").performScrollTo().performClick()
        waitFor(tabRecord)
        scenario?.close()
        launch()
        waitFor(tabRecord)
        rule.onNodeWithText("아이 등록하고 시작하기").assertDoesNotExist()
    }

    /** 아이가 있으면 '둘러보기'를 안 눌렀어도 시작하기 없이 탭. */
    @Test fun existingPetSkipsStart() {
        runBlocking { OkcheDatabase.get(context).dao().insert(Pet(name = "콩이", species = Species.cat, breed = "샴", furColor = "치즈")) }
        launch()
        waitFor(tabRecord)
        rule.onNodeWithText("아이 등록하고 시작하기").assertDoesNotExist()
    }

    /** 인슐린 줄 칸의 오류는 줄 아래 글자로 보이고, 칸에도 읽기용 오류가 붙는다. */
    @Test fun insulinRowErrorsAreOnFields() {
        startRegister()
        rule.onNode(hasSetTextAction().and(hasText("이름"))).performTextInput("콩이")
        rule.onNode(isToggleable().and(hasText("당뇨가 있어요"))).performScrollTo().performClick()
        rule.onNodeWithText("모시기").performScrollTo().performClick()
        waitFor(SemanticsMatcher.expectValue(SemanticsProperties.Error, "양을 넣어 주세요").and(hasContentDescription("첫 번째 양 (단위)")))
        rule.onNode(SemanticsMatcher.expectValue(SemanticsProperties.Error, "양을 넣어 주세요").and(hasContentDescription("두 번째 양 (단위)")))
            .assertExists()
        assertEquals(2, rule.onAllNodes(hasText("양을 넣어 주세요")).fetchSemanticsNodes().size)
    }

    /** 선택 칩(성별)은 다시 누르면 안 고름, 필수 칩(주사기)은 그대로. */
    @Test fun optionalChipDeselects() {
        startRegister()
        rule.onNodeWithText("여아").performScrollTo().performClick().assertIsSelected()
        rule.onNodeWithText("여아").performClick().assertIsNotSelected()
        rule.onNode(isToggleable().and(hasText("당뇨가 있어요"))).performScrollTo().performClick()
        rule.onNodeWithText("잘 모르겠어요").performScrollTo().assertIsSelected().performClick().assertIsSelected()
    }

    @Test fun diabetesSwitchShowsInsulin() {
        startRegister()
        rule.onNodeWithText("인슐린").assertDoesNotExist()
        rule.onNode(isToggleable().and(hasText("당뇨가 있어요"))).performScrollTo().performClick()
        rule.onNodeWithText("인슐린").assertExists()
        // 이름표를 숨긴 시간 칸: 이름은 contentDescription, 값은 stateDescription.
        rule.onNode(hasContentDescription("첫 번째 시간").and(hasStateDescription("오전 8:00"))).assertExists()
        rule.onNode(hasContentDescription("두 번째 시간").and(hasStateDescription("오후 8:00"))).assertExists()
    }

    @Test fun speciesChangeSwitchesBreedList() {
        startRegister()
        rule.onNodeWithText("코리안숏헤어").assertExists()
        rule.onNodeWithText("강아지").performClick()
        rule.onNodeWithText("코리안숏헤어").assertDoesNotExist()
        rule.onNodeWithText("말티즈").performClick()
        rule.onNodeWithText("진돗개").assertExists()
        rule.onNodeWithText("흰색").assertExists()
    }
}
