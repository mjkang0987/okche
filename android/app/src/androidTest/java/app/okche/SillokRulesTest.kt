package app.okche

import app.okche.store.DoseUnit
import app.okche.store.Food
import app.okche.store.FoodChange
import app.okche.store.FoodChangeMethod
import app.okche.store.MealMode
import app.okche.store.MealPlan
import app.okche.store.MealSlot
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.RestPeriod
import app.okche.store.Species
import app.okche.ui.sillok.SillokRange
import app.okche.ui.sillok.StoolBand
import app.okche.ui.sillok.barTop
import app.okche.ui.sillok.changeMarks
import app.okche.ui.sillok.compareText
import app.okche.ui.sillok.dailyVomits
import app.okche.ui.sillok.looseStoolCount
import app.okche.ui.sillok.mealFraction
import app.okche.ui.sillok.perDayText
import app.okche.ui.sillok.periodHeadline
import app.okche.ui.sillok.periodText
import app.okche.ui.sillok.petRecords
import app.okche.ui.sillok.sillokChanges
import app.okche.ui.sillok.sillokPeriod
import app.okche.ui.sillok.stoolShares
import app.okche.ui.sillok.urineSummary
import app.okche.ui.sillok.vomitChartLabel
import app.okche.ui.sillok.vomitCount
import app.okche.ui.sillok.weightAxis
import app.okche.ui.sillok.weightPoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/** 실록 화면 규칙(plan.md 10-1). 순수 함수, 오늘 = 10/25. iOS SillokRulesTests와 같은 경우. */
class SillokRulesTest {
    private fun at(m: Int, d: Int, h: Int, min: Int = 0) = LocalDateTime.of(2026, m, d, h, min).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private val pet = Pet(name = "콩이", species = Species.cat, breed = "", furColor = "", createdAt = at(1, 1, 9))
    private val p30 = sillokPeriod("2026-10-25", 30)
    private fun rec(kind: RecordKind, m: Int, d: Int, h: Int = 12, data: String = "{}", slotKey: String? = null, petId: String? = pet.id) =
        Record(petId = petId, kind = kind, occurredAt = at(m, d, h), data = data, slotKey = slotKey)

    @Test fun periods() {
        assertEquals("9/26 – 10/25", periodText(p30))
        assertEquals("2026-09-26", p30.start)
        assertEquals(30, p30.dayList.size)
        assertEquals("최근 30일", periodHeadline(p30, SillokRange.month))
        val back = sillokPeriod("2026-10-25", 30, back = 1)
        assertEquals("8/27 – 9/25", periodText(back))
        assertEquals("30일", periodHeadline(back, SillokRange.month))
        assertEquals("8/27 – 9/25", periodText(p30.previous()))
        assertEquals("10/19 – 10/25", periodText(sillokPeriod("2026-10-25", 7)))
        // 6시 전 기록은 전날(1-2) — 10/26 새벽 2시는 10/25 기간 안.
        assertEquals(1, petRecords(listOf(rec(RecordKind.vomit, 10, 26, 2)), pet.id, p30).size)
        assertEquals(0, petRecords(listOf(rec(RecordKind.vomit, 10, 26, 7)), pet.id, p30).size)
        assertEquals(0, petRecords(listOf(rec(RecordKind.vomit, 10, 20, petId = null)), pet.id, p30).size) // 누군지 모름은 빠짐(1-3)
    }

    @Test fun countsAndCompare() {
        val rs = listOf(
            rec(RecordKind.vomit, 10, 3), rec(RecordKind.vomit, 10, 3, 18), rec(RecordKind.vomit, 10, 20),
            rec(RecordKind.stool, 10, 1, data = """{"shape":"soft"}"""), rec(RecordKind.stool, 10, 2, data = """{"shape":"watery"}"""),
            rec(RecordKind.stool, 10, 4, data = """{"shape":"normal"}"""), rec(RecordKind.stool, 10, 5, data = """{"shape":"loose"}"""),
        )
        assertEquals(3, vomitCount(rs))
        assertEquals(3, looseStoolCount(rs))
        assertEquals("2회 줄었어요", compareText(3, 5, true, SillokRange.month))
        assertEquals("1회 늘었어요", compareText(3, 2, true, SillokRange.month))
        assertEquals("직전 30일과 같아요", compareText(3, 3, true, SillokRange.month))
        assertEquals("직전 1주와 같아요", compareText(0, 0, true, SillokRange.week))
        assertNull(compareText(3, 0, false, SillokRange.month))
        val daily = dailyVomits(rs, p30)
        assertEquals(2, daily[p30.dayList.indexOf("2026-10-03")])
        assertEquals(3, daily.sum())
        assertEquals(2, barTop(daily))
        assertEquals(4, barTop(listOf(0, 3)))
        assertEquals(2, barTop(emptyList()))
        assertEquals(
            "구토 횟수 일자별 그래프 · 좌우로 넘겨 보기 · 9/26부터 10/25까지 3번",
            vomitChartLabel(p30, 3, emptyList()),
        )
    }

    @Test fun mealFractionUsesSlotsAndRest() {
        val plan = MealPlan(petId = pet.id, mode = MealMode.direct, slots = listOf(MealSlot("아침", "08:00"), MealSlot("저녁", "20:00")), activeFrom = at(10, 20, 9))
        // 10/20 9시부터: 10/20 저녁 + 21~25일 2끼씩 = 11끼.
        val key = { day: String, t: String -> "${plan.id}|$day|$t" }
        val records = listOf(
            rec(RecordKind.meal, 10, 21, 8, """{"result":"all"}""", key("2026-10-21", "08:00")),
            rec(RecordKind.meal, 10, 21, 8, """{"result":"all"}""", key("2026-10-21", "08:00")), // 한 차례 둘이어도 하나
            rec(RecordKind.meal, 10, 21, 20, """{"result":"some"}""", key("2026-10-21", "20:00")),
            rec(RecordKind.meal, 10, 22, 8, """{"result":"all"}"""), // 차례 없는 추가 식사
        )
        val late = at(10, 25, 21)
        assertEquals(1 to 11, mealFraction(records, listOf(plan), emptyList(), pet.id, p30, late))
        // 오늘 저녁(20:00)이 아직 안 왔으면 분모에서 빠짐.
        assertEquals(1 to 10, mealFraction(records, listOf(plan), emptyList(), pet.id, p30, at(10, 25, 12)))
        // 휴양(10/23 0시 ~ 10/24 0시)이면 그 안 끼니 2개 뺌.
        val rest = RestPeriod(petId = pet.id, startedAt = at(10, 23, 0), endedAt = at(10, 24, 0))
        assertEquals(1 to 9, mealFraction(records, listOf(plan), listOf(rest), pet.id, p30, late))
        assertNull(mealFraction(records, emptyList(), emptyList(), pet.id, p30, late))
    }

    @Test fun stoolAndUrine() {
        val stools = listOf("hard", "normal", "soft").map { rec(RecordKind.stool, 10, 10, data = """{"shape":"$it"}""") }
        val shares = stoolShares(stools)!!
        assertEquals(100, shares.sumOf { it.second })
        assertEquals(listOf(StoolBand.hard to 34, StoolBand.normal to 33, StoolBand.soft to 33, StoolBand.watery to 0), shares)
        assertNull(stoolShares(listOf(rec(RecordKind.vomit, 10, 10))))
        val urine = listOf(
            rec(RecordKind.urine, 10, 10, data = """{"amount":"little","color":"pinkRed","signs":["straining","frequent"]}"""),
            rec(RecordKind.urine, 10, 11, data = """{"amount":"normal","color":"paleYellow","signs":[]}"""),
            rec(RecordKind.urine, 10, 12, data = """{"amount":"little"}"""),
        )
        val u = urineSummary(urine, 30)!!
        assertEquals("0.1", perDayText(u.perDay))
        assertEquals(listOf(2, 1, 1), listOf(u.little, u.pinkRed, u.straining))
        assertEquals("3.2", perDayText(96 / 30.0))
        assertNull(urineSummary(emptyList(), 30))
    }

    @Test fun weight() {
        val ws = listOf(rec(RecordKind.weight, 10, 20, data = """{"grams":4600}"""), rec(RecordKind.weight, 10, 1, data = """{"grams":4800}"""))
        assertEquals(listOf(4800L, 4600L), weightPoints(ws).map { it.second })
        assertEquals(listOf("1주", "2주", "3주", "4주", "5주"), weightAxis(p30))
        assertEquals(listOf("10/19", "10/20", "10/21", "10/22", "10/23", "10/24", "10/25"), weightAxis(sillokPeriod("2026-10-25", 7)))
    }

    @Test fun changes() {
        val food = Food(name = "가수분해 처방식")
        val fc = FoodChange(petId = pet.id, foodId = food.id, startedOn = "2026-10-14", method = FoodChangeMethod.gradual)
        val old = FoodChange(petId = pet.id, foodId = food.id, startedOn = "2026-09-01", method = FoodChangeMethod.gradual) // 기간 밖
        val med = Medication(name = "프레드니솔론", category = MedCategory.medicine, form = MedForm.pill, doseAmount = 1.0, unit = DoseUnit.pill)
        val first = MedSchedule(petId = pet.id, medicationId = med.id, amount = 1.0, unit = DoseUnit.pill, frequency = MedFrequency.daily,
            times = listOf("08:00"), activeFrom = at(10, 20, 9), activeUntil = at(10, 22, 9), createdAt = at(10, 20, 9))
        // 늦게 줬을 때만 바뀐 줄은 선 없음, 양이 바뀐 줄은 '바꿈', 마지막 줄 끝은 '그만'.
        val lateOnly = first.copy(id = "b", seriesId = first.id, shiftOnLate = false, activeFrom = at(10, 22, 9), activeUntil = at(10, 23, 9), createdAt = at(10, 22, 9))
        val more = first.copy(id = "c", seriesId = first.id, amount = 2.0, activeFrom = at(10, 23, 9), activeUntil = at(10, 24, 9), createdAt = at(10, 23, 9))
        val list = sillokChanges(pet, listOf(fc, old), listOf(food), listOf(first, lateOnly, more), listOf(med), p30)
        assertEquals(
            listOf("10/14 사료 → 가수분해 처방식", "10/20 탕약 → 프레드니솔론 · 시작", "10/23 탕약 → 프레드니솔론 · 바꿈", "10/24 탕약 → 프레드니솔론 · 그만"),
            list.map { it.text },
        )
        assertEquals("10/14 사료 바꿈", list.first().spoken)
        // 같은 날 사료와 약이면 금색 선 하나, 이름표 '사료·탕약'.
        val sameDay = sillokChanges(pet, listOf(fc.copy(startedOn = "2026-10-20")), listOf(food), listOf(first), listOf(med), p30)
        assertEquals(mapOf("2026-10-20" to "사료·탕약", "2026-10-22" to "탕약"), changeMarks(sameDay))
        // 추억으로 끝난 줄 끝은 '그만'으로 안 셈(6-4).
        val kept = pet.copy(memorializedAt = at(10, 22, 9))
        assertEquals(listOf("10/20 탕약 → 프레드니솔론 · 시작"), sillokChanges(kept, emptyList(), emptyList(), listOf(first), listOf(med), p30).map { it.text })
    }
}
