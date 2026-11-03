import SwiftData
import XCTest
@testable import Okche

/// 끼니 구간·끼니 시간 바꾸기·급식기 자동 기록(plan.md 7-3). 안드로이드 MealRulesTest와 같은 경우.
@MainActor
final class MealRulesTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul = TimeZone(identifier: "Asia/Seoul")!
    private let pet = UUID()
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
    private func plan(_ mode: MealMode, _ times: [String], from: Date) throws -> MealPlan {
        try MealRules.savePlan(petId: pet, mode: mode, slots: times.map { MealSlot(name: "", time: $0) }, now: from, in: context)
        return try XCTUnwrap(MealRules.openPlan(petId: pet, in: context))
    }
    private func meals() throws -> [Record] { try context.fetch(FetchDescriptor<Record>()).filter { $0.kind == "meal" } }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testNames() {
        XCTAssertEqual(MealRules.names(1), ["아침"])
        XCTAssertEqual(MealRules.names(2), ["아침", "저녁"])
        XCTAssertEqual(MealRules.names(4), ["아침", "점심", "저녁", "끼니 4"])
        XCTAssertEqual(MealRules.named([MealSlot(name: "", time: "18:00"), MealSlot(name: "", time: "08:00", grams: 40)]),
                       [MealSlot(name: "아침", time: "08:00", grams: 40), MealSlot(name: "저녁", time: "18:00")])
    }

    /// 구간: 자기 시간 1시간 전 ~ 다음 끼니 1시간 전. 08:00·18:00이면 07:00~17:00 아침, 17:00~다음 날 07:00 저녁.
    func testCurrentWindow() throws {
        let p = try plan(.direct, ["08:00", "18:00"], from: at(2026, 10, 1, 0))
        XCTAssertEqual(MealRules.current(p, now: at(2026, 10, 25, 7), timeZone: seoul)?.slot.name, "아침")
        XCTAssertEqual(MealRules.current(p, now: at(2026, 10, 25, 16, 59), timeZone: seoul)?.slot.name, "아침")
        let evening = MealRules.current(p, now: at(2026, 10, 25, 17), timeZone: seoul)
        XCTAssertEqual(evening?.slot.name, "저녁")
        XCTAssertEqual(evening?.key, "\(p.id.uuidString.lowercased())|2026-10-25|18:00")
        // 새벽 3시는 어제 저녁 차례(6시 기준 날도 어제)
        XCTAssertEqual(MealRules.current(p, now: at(2026, 10, 26, 3), timeZone: seoul)?.key, "\(p.id.uuidString.lowercased())|2026-10-25|18:00")
    }

    /// 일기 바로 기록: 지금 차례로, 이미 있으면 추가 식사(`slotKey` 없음), 끼니 없으면 없음.
    func testQuickMealGoesToSlotThenExtra() throws {
        let row = QuickRecord.rows[3]
        let none = try QuickRecord.save(row, value: "all", petId: pet, now: at(2026, 10, 25, 8), timeZone: seoul, in: context)
        XCTAssertNil(none.slotKey) // 끼니 없음
        _ = try plan(.direct, ["08:00", "18:00"], from: at(2026, 10, 25, 9))
        try context.fetch(FetchDescriptor<MealPlan>()).first!.slots[0].grams = 40
        let first = try QuickRecord.save(row, value: "some", petId: pet, now: at(2026, 10, 25, 9, 30), timeZone: seoul, in: context)
        XCTAssertNotNil(first.slotKey)
        XCTAssertEqual(first.data, #"{"givenGrams":40,"result":"some","slotName":"아침"}"#)
        let extra = try QuickRecord.save(row, value: "all", petId: pet, now: at(2026, 10, 25, 10), timeZone: seoul, in: context)
        XCTAssertNil(extra.slotKey) // 추가 식사
        XCTAssertEqual(QuickRecord.value(first), "남김")
    }

    /// 끼니 시간 바꾸기: 열린 계획을 닫고 새 줄, 모두 빼면 닫기만.
    func testSavePlanClosesAndReplaces() throws {
        let old = try plan(.direct, ["08:00"], from: at(2026, 10, 1, 9))
        let new = try plan(.feeder, ["07:00", "19:00"], from: at(2026, 10, 2, 9))
        XCTAssertEqual(old.activeUntil, at(2026, 10, 2, 9))
        XCTAssertEqual(new.mode, .feeder)
        XCTAssertEqual(new.slots.map(\.name), ["아침", "저녁"])
        try MealRules.savePlan(petId: pet, mode: .direct, slots: [], now: at(2026, 10, 3, 9), in: context)
        XCTAssertNil(try MealRules.openPlan(petId: pet, in: context))
    }

    /// 화면에서 고르기: 없으면 만들고 있으면 결과만, 남김이 아니면 남은 양 비움.
    func testPickAndLeftGrams() throws {
        let p = try plan(.direct, ["08:00"], from: at(2026, 10, 1, 9))
        let slot = try XCTUnwrap(MealRules.occurrences(p, day: "2026-10-25", timeZone: seoul).first)
        let record = try MealRules.pick("some", for: slot, petId: pet, now: at(2026, 10, 25, 8, 10), timeZone: seoul, in: context)
        try MealRules.setLeft(8, on: record, in: context)
        XCTAssertEqual(MealRules.json(record)["leftGrams"] as? Int, 8)
        let again = try MealRules.pick("all", for: slot, petId: pet, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        XCTAssertEqual(again.id, record.id)
        XCTAssertEqual(record.data, #"{"result":"all","slotName":"아침"}"#)
        XCTAssertEqual(try meals().count, 1)
    }

    /// 6시 전 끼니는 그날(6시 기준)의 마지막 차례 — 달력 다음 날 새벽.
    func testOccurrencesBeforeSixGoToNextCalendarDay() throws {
        let p = try plan(.direct, ["02:00", "08:00"], from: at(2026, 10, 1, 9))
        let list = MealRules.occurrences(p, day: "2026-10-25", timeZone: seoul)
        XCTAssertEqual(list.map(\.at), [at(2026, 10, 25, 8), at(2026, 10, 26, 2)])
        XCTAssertEqual(Set(list.map(\.day)), ["2026-10-25"])
    }

    /// 급식기: 빠진 날까지 채우고, 고정 ID라 한 번만, 지운 것은 되살리지 않고, 계획 전·지금 뒤 끼니는 안 만듦.
    func testFeederFill() throws {
        let p = try plan(.feeder, ["07:00", "19:00"], from: at(2026, 10, 24, 12))
        // 24일 19시, 25일 7·19시, 26일 7시(24일 7시는 계획 전, 26일 19시는 아직)
        XCTAssertEqual(try MealRules.fillFeeder(now: at(2026, 10, 26, 8), timeZone: seoul, in: context), 4)
        let made = try meals().sorted { $0.occurredAt < $1.occurredAt }
        XCTAssertEqual(made.map(\.occurredAt), [at(2026, 10, 24, 19), at(2026, 10, 25, 7), at(2026, 10, 25, 19), at(2026, 10, 26, 7)])
        let first = made[0]
        XCTAssertTrue(first.auto)
        XCTAssertEqual(first.updatedAt, at(2026, 10, 24, 19)) // 끼니 예정 시각(1-5)
        XCTAssertEqual(first.id, UUID.feederRecord(slotKey: "\(p.id.uuidString.lowercased())|2026-10-24|19:00"))
        XCTAssertEqual(first.data, #"{"result":"all","slotName":"저녁"}"#)
        XCTAssertEqual(try MealRules.fillFeeder(now: at(2026, 10, 26, 8), timeZone: seoul, in: context), 0) // 두 번 안 만듦
        try QuickRecord.delete(first, in: context)
        XCTAssertEqual(try MealRules.fillFeeder(now: at(2026, 10, 26, 8), timeZone: seoul, in: context), 0) // 지운 것 안 되살림
        // 계획을 닫으면 닫힌 때 뒤로는 안 만듦
        try MealRules.savePlan(petId: pet, mode: .direct, slots: [], now: at(2026, 10, 26, 10), in: context)
        XCTAssertEqual(try MealRules.fillFeeder(now: at(2026, 10, 27, 20), timeZone: seoul, in: context), 0)
    }
}
