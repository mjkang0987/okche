import Foundation
import XCTest
@testable import Okche

/// 실록 화면 규칙(plan.md 10-1). 오늘 = 10/25. 안드로이드 SillokRulesTest와 같은 경우.
@MainActor
final class SillokRulesTests: XCTestCase {
    private func at(_ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        Calendar.okcheDevice.date(from: DateComponents(year: 2026, month: m, day: d, hour: h, minute: min))!
    }
    private lazy var pet = Pet(now: at(1, 1, 9), name: "콩이", species: .cat, breed: "", furColor: "")
    private let p30 = SillokRules.period(today: "2026-10-25", days: 30)
    private func rec(_ kind: RecordKind, _ m: Int, _ d: Int, _ h: Int = 12, data: String = "{}", slotKey: String? = nil, petId: UUID?? = .none) -> Record {
        Record(petId: petId ?? pet.id, kind: kind, occurredAt: at(m, d, h), slotKey: slotKey, data: data)
    }

    func testPeriods() {
        XCTAssertEqual(SillokRules.text(p30), "9/26 – 10/25")
        XCTAssertEqual(p30.start, "2026-09-26")
        XCTAssertEqual(p30.dayList.count, 30)
        XCTAssertEqual(SillokRules.headline(p30, .month), "최근 30일")
        let back = SillokRules.period(today: "2026-10-25", days: 30, back: 1)
        XCTAssertEqual(SillokRules.text(back), "8/27 – 9/25")
        XCTAssertEqual(SillokRules.headline(back, .month), "30일")
        XCTAssertEqual(SillokRules.text(p30.previous), "8/27 – 9/25")
        XCTAssertEqual(SillokRules.text(SillokRules.period(today: "2026-10-25", days: 7)), "10/19 – 10/25")
        // 6시 전 기록은 전날(1-2) — 10/26 새벽 2시는 10/25 기간 안.
        XCTAssertEqual(SillokRules.records([rec(.vomit, 10, 26, 2)], petId: pet.id, in: p30).count, 1)
        XCTAssertEqual(SillokRules.records([rec(.vomit, 10, 26, 7)], petId: pet.id, in: p30).count, 0)
        XCTAssertEqual(SillokRules.records([rec(.vomit, 10, 20, petId: .some(nil))], petId: pet.id, in: p30).count, 0) // 누군지 모름은 빠짐(1-3)
    }

    func testCountsAndCompare() {
        let rs = [
            rec(.vomit, 10, 3), rec(.vomit, 10, 3, 18), rec(.vomit, 10, 20),
            rec(.stool, 10, 1, data: #"{"shape":"soft"}"#), rec(.stool, 10, 2, data: #"{"shape":"watery"}"#),
            rec(.stool, 10, 4, data: #"{"shape":"normal"}"#), rec(.stool, 10, 5, data: #"{"shape":"loose"}"#),
        ]
        XCTAssertEqual(SillokRules.vomitCount(rs), 3)
        XCTAssertEqual(SillokRules.looseStoolCount(rs), 3)
        XCTAssertEqual(SillokRules.compare(3, 5, beforeHasRecords: true, .month), "2회 줄었어요")
        XCTAssertEqual(SillokRules.compare(3, 2, beforeHasRecords: true, .month), "1회 늘었어요")
        XCTAssertEqual(SillokRules.compare(3, 3, beforeHasRecords: true, .month), "직전 30일과 같아요")
        XCTAssertEqual(SillokRules.compare(0, 0, beforeHasRecords: true, .week), "직전 1주와 같아요")
        XCTAssertNil(SillokRules.compare(3, 0, beforeHasRecords: false, .month))
        let daily = SillokRules.dailyVomits(rs, p30)
        XCTAssertEqual(daily[p30.dayList.firstIndex(of: "2026-10-03")!], 2)
        XCTAssertEqual(daily.reduce(0, +), 3)
        XCTAssertEqual(SillokRules.barTop(daily), 2)
        XCTAssertEqual(SillokRules.barTop([0, 3]), 4)
        XCTAssertEqual(SillokRules.barTop([]), 2)
        XCTAssertEqual(SillokRules.vomitChartLabel(p30, total: 3, changes: []), "구토 횟수 일자별 그래프 · 좌우로 넘겨 보기 · 9/26부터 10/25까지 3번")
    }

    func testMealFractionUsesSlotsAndRest() throws {
        let plan = MealPlan(petId: pet.id, mode: .direct, slots: [MealSlot(name: "아침", time: "08:00"), MealSlot(name: "저녁", time: "20:00")],
                            activeFrom: at(10, 20, 9))
        // 10/20 9시부터: 10/20 저녁 + 21~25일 2끼씩 = 11끼.
        func key(_ day: String, _ t: String) -> String { "\(plan.id.uuidString.lowercased())|\(day)|\(t)" }
        let records = [
            rec(.meal, 10, 21, 8, data: #"{"result":"all"}"#, slotKey: key("2026-10-21", "08:00")),
            rec(.meal, 10, 21, 8, data: #"{"result":"all"}"#, slotKey: key("2026-10-21", "08:00")), // 한 차례 둘이어도 하나
            rec(.meal, 10, 21, 20, data: #"{"result":"some"}"#, slotKey: key("2026-10-21", "20:00")),
            rec(.meal, 10, 22, 8, data: #"{"result":"all"}"#), // 차례 없는 추가 식사
        ]
        let late = at(10, 25, 21)
        let all = try XCTUnwrap(SillokRules.mealFraction(records, plans: [plan], rests: [], petId: pet.id, p30, now: late))
        XCTAssertEqual([all.eaten, all.total], [1, 11])
        // 오늘 저녁(20:00)이 아직 안 왔으면 분모에서 빠짐.
        let noon = try XCTUnwrap(SillokRules.mealFraction(records, plans: [plan], rests: [], petId: pet.id, p30, now: at(10, 25, 12)))
        XCTAssertEqual([noon.eaten, noon.total], [1, 10])
        // 휴양(10/23 0시 ~ 10/24 0시)이면 그 안 끼니 2개 뺌.
        let rest = RestPeriod(petId: pet.id, startedAt: at(10, 23, 0), endedAt: at(10, 24, 0))
        let rested = try XCTUnwrap(SillokRules.mealFraction(records, plans: [plan], rests: [rest], petId: pet.id, p30, now: late))
        XCTAssertEqual([rested.eaten, rested.total], [1, 9])
        XCTAssertNil(SillokRules.mealFraction(records, plans: [], rests: [], petId: pet.id, p30, now: late))
        // 10/25 저녁 끼니를 10/26 아침 7시에 남겨도(기록 날 10/26) 그 차례로 셈.
        let nextMorning = rec(.meal, 10, 26, 7, data: #"{"result":"all"}"#, slotKey: key("2026-10-25", "20:00"))
        let late2 = try XCTUnwrap(SillokRules.mealFraction(records + [nextMorning], plans: [plan], rests: [], petId: pet.id, p30, now: late))
        XCTAssertEqual([late2.eaten, late2.total], [2, 11])
    }

    func testStoolAndUrine() throws {
        let stools = ["hard", "normal", "soft"].map { rec(.stool, 10, 10, data: "{\"shape\":\"\($0)\"}") }
        let shares = try XCTUnwrap(SillokRules.stoolShares(stools))
        XCTAssertEqual(shares.map(\.percent).reduce(0, +), 100)
        XCTAssertEqual(shares.map(\.band), [.hard, .normal, .soft, .watery])
        XCTAssertEqual(shares.map(\.percent), [34, 33, 33, 0])
        XCTAssertNil(SillokRules.stoolShares([rec(.vomit, 10, 10)]))
        let urine = [
            rec(.urine, 10, 10, data: #"{"amount":"little","color":"pinkRed","signs":["straining","frequent"]}"#),
            rec(.urine, 10, 11, data: #"{"amount":"normal","color":"paleYellow","signs":[]}"#),
            rec(.urine, 10, 12, data: #"{"amount":"little"}"#),
        ]
        let u = try XCTUnwrap(SillokRules.urine(urine, days: 30))
        XCTAssertEqual(SillokRules.perDayText(u.perDay), "0.1")
        XCTAssertEqual([u.little, u.pinkRed, u.straining], [2, 1, 1])
        XCTAssertEqual(SillokRules.perDayText(96 / 30.0), "3.2")
        XCTAssertNil(SillokRules.urine([], days: 30))
        // 직전 기간에 소피 기록이 없어도 평균 0(다른 기록이 있을 때 '직전 30일 0.0번').
        XCTAssertEqual(SillokRules.perDayText(SillokRules.urinePerDay([rec(.vomit, 10, 10)], days: 30)), "0.0")
        XCTAssertEqual(SillokRules.perDayText(SillokRules.urinePerDay(urine, days: 30)), "0.1")
    }

    func testWeight() {
        let ws = [rec(.weight, 10, 20, data: #"{"grams":4600}"#), rec(.weight, 10, 1, data: #"{"grams":4800}"#)]
        XCTAssertEqual(SillokRules.weightPoints(ws).map(\.grams), [4800, 4600])
        XCTAssertEqual(SillokRules.kgNumber(4600), "4.6")
        XCTAssertEqual(SillokRules.weightAxis(p30), ["1주", "2주", "3주", "4주", "5주"])
        XCTAssertEqual(SillokRules.weightAxis(SillokRules.period(today: "2026-10-25", days: 7)),
                       ["10/19", "10/20", "10/21", "10/22", "10/23", "10/24", "10/25"])
    }

    func testChanges() {
        let food = Food(name: "가수분해 처방식")
        let fc = FoodChange(petId: pet.id, foodId: food.id, startedOn: "2026-10-14", method: .gradual)
        let old = FoodChange(petId: pet.id, foodId: food.id, startedOn: "2026-09-01", method: .gradual) // 기간 밖
        let med = Medication(name: "프레드니솔론", category: .medicine, form: .pill, doseAmount: 1, unit: .pill)
        let first = MedSchedule(now: at(10, 20, 9), petId: pet.id, medicationId: med.id, amount: 1, unit: .pill, frequency: .daily, times: ["08:00"],
                                activeFrom: at(10, 20, 9), activeUntil: at(10, 22, 9))
        // 늦게 줬을 때만 바뀐 줄은 선 없음, 양이 바뀐 줄은 '바꿈', 마지막 줄 끝은 '그만'.
        let lateOnly = MedSchedule(now: at(10, 22, 9), petId: pet.id, medicationId: med.id, seriesId: first.seriesId, amount: 1, unit: .pill,
                                   frequency: .daily, times: ["08:00"], shiftOnLate: false, activeFrom: at(10, 22, 9), activeUntil: at(10, 23, 9))
        let more = MedSchedule(now: at(10, 23, 9), petId: pet.id, medicationId: med.id, seriesId: first.seriesId, amount: 2, unit: .pill,
                               frequency: .daily, times: ["08:00"], activeFrom: at(10, 23, 9), activeUntil: at(10, 24, 9))
        let list = SillokRules.changes(pet: pet, foodChanges: [fc, old], foods: [food], schedules: [first, lateOnly, more], meds: [med], in: p30)
        XCTAssertEqual(list.map(\.text), ["10/14 사료 → 가수분해 처방식", "10/20 탕약 → 프레드니솔론 · 시작",
                                          "10/23 탕약 → 프레드니솔론 · 바꿈", "10/24 탕약 → 프레드니솔론 · 그만"])
        XCTAssertEqual(list.first?.spoken, "10/14 사료 바꿈")
        XCTAssertEqual(list.first?.label, "10/14 사료 바꿈, 가수분해 처방식") // 줄 읽기는 화살표 없이
        XCTAssertEqual(list[2].label, "10/23 탕약 바꿈, 프레드니솔론")
        // 같은 날 사료와 약이면 금색 선 하나, 이름표 '사료·탕약'.
        let sameDayFood = FoodChange(petId: pet.id, foodId: food.id, startedOn: "2026-10-20", method: .gradual)
        let sameDay = SillokRules.changes(pet: pet, foodChanges: [sameDayFood], foods: [food], schedules: [first], meds: [med], in: p30)
        XCTAssertEqual(SillokRules.marks(sameDay), ["2026-10-20": "사료·탕약", "2026-10-22": "탕약"])
        // 추억으로 끝난 줄 끝은 '그만'으로 안 셈(6-4).
        pet.memorializedAt = at(10, 22, 9)
        XCTAssertEqual(SillokRules.changes(pet: pet, foodChanges: [], foods: [], schedules: [first], meds: [med], in: p30).map(\.text),
                       ["10/20 탕약 → 프레드니솔론 · 시작"])
    }
}
