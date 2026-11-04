package app.okche

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import app.okche.store.OkcheDatabase
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.Species
import app.okche.ui.main.mainDayTitle
import app.okche.ui.main.mainDetail
import app.okche.ui.main.mainPill
import app.okche.ui.main.mainTodayRecords
import app.okche.ui.main.plainWord
import app.okche.ui.pet.PetForm
import app.okche.ui.pet.registerPet
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** 문안 탭 규칙(plan.md 7-0). iOS MainCardTests와 같은 경우. */
class MainCardTest {
    @Test fun pillBirthdayFamilyDayAndOverlap() {
        assertEquals("오늘 생일", mainPill("2019-10-25", false, null, "2026-10-25"))
        assertNull(mainPill("2019-10-25", true, null, "2026-10-25")) // 어림잡은 생일
        assertNull(mainPill("2019-10-24", false, null, "2026-10-25"))
        assertEquals("가족 된 날", mainPill(null, false, "2023-10-25", "2026-10-25"))
        assertNull(mainPill(null, false, "2026-10-25", "2026-10-25")) // 가족 된 그날
        assertEquals("오늘 생일", mainPill("2019-10-25", false, "2023-10-25", "2026-10-25"))
        // 어림 생일이면 겹쳐도 가족 된 날
        assertEquals("가족 된 날", mainPill("2019-10-25", true, "2023-10-25", "2026-10-25"))
    }

    @Test fun pillFeb29() {
        assertEquals("오늘 생일", mainPill("2020-02-29", false, null, "2026-02-28")) // 평년
        assertNull(mainPill("2020-02-29", false, null, "2028-02-28")) // 윤년은 2/29에
        assertEquals("오늘 생일", mainPill("2020-02-29", false, null, "2028-02-29"))
        assertNull(mainPill("2020-02-29", false, null, "2026-03-01"))
    }

    @Test fun detailLineAndDayTitle() {
        val pet = Pet(name = "콩이", species = Species.cat, breed = "코리안숏헤어", furColor = "", birthDate = "2019-03-01")
        assertEquals("고양이 · 코리안숏헤어 · 7살", mainDetail(pet, LocalDate.of(2026, 10, 25)))
        assertEquals("강아지", mainDetail(Pet(name = "보리", species = Species.dog, breed = "", furColor = ""))) // 품종·생일 없으면 뺌
        assertEquals("10월 25일 일요일", mainDayTitle("2026-10-25"))
    }

    @Test fun todayRecordsFiltersAndSorts() {
        val kong = "kong"
        fun record(pet: String?, kind: RecordKind, at: Long, day: String = "2026-10-25", deleted: Long? = null) =
            Record(petId = pet, kind = kind, occurredAt = at, day = day, deletedAt = deleted)
        val late = record(kong, RecordKind.urine, 940)
        val early = record(kong, RecordKind.meal, 720)
        val rows = mainTodayRecords(
            listOf(
                late, early,
                record(kong, RecordKind.stool, 559, day = "2026-10-24"), // 6시 전 = 어제
                record("bori", RecordKind.vomit, 800), // 다른 아이
                record(null, RecordKind.vomit, 800), // 누군지 모름
                record(kong, RecordKind.weight, 800), // 옥체는 그 단계에서
                record(kong, RecordKind.stool, 800, deleted = 1),
            ),
            kong, "2026-10-25",
        )
        assertEquals(listOf(early.id, late.id), rows.map { it.id })
        assertEquals(listOf("식사", "소변"), rows.mapNotNull { plainWord(it.kind) })
        assertNull(plainWord(RecordKind.vomit))
    }
}

/** 문안: 기록 없음 안내 → '기록하기'로 일기 → 바로 기록 → 문안 오늘 기록 줄(소피 → 고치기 시트, 수라 → 수라 화면) (7-0). */
class MainFlowTest {
    @get:Rule val rule = createEmptyComposeRule()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var scenario: ActivityScenario<MainActivity>? = null

    @After fun closeScenario() {
        scenario?.close()
    }

    private fun waitFor(matcher: SemanticsMatcher) = rule.waitUntil(5_000) { rule.onAllNodes(matcher).fetchSemanticsNodes().isNotEmpty() }
    private fun tab(name: String) = SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab).and(hasText(name))

    @Test fun todayRecords() {
        runBlocking {
            OkcheDatabase.get(context).clearAllTables()
            registerPet(OkcheDatabase.get(context).dao(), PetForm(name = "콩이"))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitFor(tab("문안"))
        rule.onNode(tab("문안")).performClick()
        waitFor(hasText("콩이 아기씨, 오늘도 강녕하시옵소서"))
        waitFor(hasText("아직 오늘 남긴 기록이 없어요."))
        rule.onNode(hasText("기록하기")).performScrollTo().performClick()
        waitFor(hasContentDescription("소피 보통 · 바로 기록"))
        rule.onNode(hasContentDescription("소피 보통 · 바로 기록")).performScrollTo().performClick()
        waitFor(hasText("오늘 일기 전체"))
        rule.onNode(hasContentDescription("수라 다 먹음 · 바로 기록")).performScrollTo().performClick()
        rule.waitForIdle()
        rule.onNode(tab("문안")).performClick()
        waitFor(hasContentDescription("소피 보통 · 고치기", substring = true))
        rule.onNode(hasContentDescription("소피 보통 · 고치기", substring = true)).performScrollTo().performClick()
        waitFor(hasText("콩이 · 소피"))
        rule.onNode(hasContentDescription("닫기")).performClick()
        waitFor(hasContentDescription("수라 다 먹음 · 고치기", substring = true))
        rule.onNode(hasContentDescription("수라 다 먹음 · 고치기", substring = true)).performScrollTo().performClick()
        waitFor(hasText("지금 먹은 것"))
        rule.onNode(hasContentDescription("뒤로")).performClick()
        waitFor(hasText("콩이 아기씨, 오늘도 강녕하시옵소서"))
    }
}
