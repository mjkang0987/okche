import SwiftData
import XCTest
@testable import Okche

/// 다과 규칙(plan.md 7-4b). 안드로이드 SnackRulesTest와 같은 경우.
@MainActor
final class SnackRulesTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul = TimeZone(identifier: "Asia/Seoul")!
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
    private var seoulCalendar: Calendar {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c
    }
    private func records() throws -> [Record] { try context.fetch(FetchDescriptor<Record>()) }
    private func snacks() throws -> [Snack] { try context.fetch(FetchDescriptor<Snack>()) }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testAddEditRemoveKeepsNameForOldRecords() throws {
        let pet = UUID()
        let salmon = try SnackRules.add(name: " 연어 퓨레 ", kind: .lickable, okDuringFoodChange: false, now: at(2026, 10, 1, 9), in: context)
        let chicken = try SnackRules.add(name: "동결건조 닭가슴살", kind: .freezeDried, okDuringFoodChange: true, now: at(2026, 10, 2, 9), in: context)
        XCTAssertEqual(salmon.name, "연어 퓨레")
        let record = try SnackRules.record(snackId: salmon.id, amount: " 반 개 ", occurredAt: at(2026, 10, 20, 16), editing: nil, petId: pet,
                                           timeZone: seoul, in: context)
        XCTAssertEqual(record.data, #"{"amount":"반 개","snackId":"\#(salmon.id.uuidString.lowercased())"}"#)
        XCTAssertEqual(QuickRecord.value(record), "연어 퓨레 · 반 개")
        try SnackRules.update(salmon, name: "연어 퓨레 츄르", kind: .wet, okDuringFoodChange: true, in: context)
        XCTAssertEqual(salmon.kind, .wet)
        try SnackRules.remove(salmon, in: context)
        XCTAssertEqual(SnackRules.listed(try snacks()).map(\.id), [chicken.id]) // 목록에서 빠짐
        XCTAssertEqual(QuickRecord.value(record), "연어 퓨레 츄르 · 반 개") // 지난 기록 이름은 남음
        // 고치기: 양 비우면 칸 없음, 시간을 바꾸면 day도
        try SnackRules.record(snackId: chicken.id, amount: "  ", occurredAt: at(2026, 10, 21, 5), editing: record, petId: pet, timeZone: seoul, in: context)
        XCTAssertEqual(record.data, #"{"snackId":"\#(chicken.id.uuidString.lowercased())"}"#)
        XCTAssertEqual(record.day, "2026-10-20")
        XCTAssertEqual(try records().count, 1)
    }

    func testListDetailCountsThisMonthForPet() throws {
        let kong = UUID(), bori = UUID()
        let salmon = try SnackRules.add(name: "연어 퓨레", kind: .lickable, okDuringFoodChange: false, in: context)
        let ok = try SnackRules.add(name: "저알러지 처방 간식", kind: .prescription, okDuringFoodChange: true, in: context)
        for (pet, date) in [(kong, at(2026, 10, 3, 9)), (kong, at(2026, 10, 20, 9)), (bori, at(2026, 10, 20, 9)), (kong, at(2026, 9, 30, 9))] {
            try SnackRules.record(snackId: salmon.id, amount: "", occurredAt: date, editing: nil, petId: pet, timeZone: seoul, in: context)
        }
        let deleted = try SnackRules.record(snackId: salmon.id, amount: "", occurredAt: at(2026, 10, 21, 9), editing: nil, petId: kong, timeZone: seoul, in: context)
        deleted.deletedAt = .now
        let now = at(2026, 10, 25, 9)
        XCTAssertEqual(SnackRules.listDetail(salmon, petId: kong, records: try records(), now: now, calendar: seoulCalendar), "짜 먹는 간식 · 이번 달 2번")
        XCTAssertEqual(SnackRules.listDetail(ok, petId: kong, records: try records(), now: now, calendar: seoulCalendar), "처방 간식 · 수라 바꾸기 중에도 괜찮아요")
    }

    func testPickOrderRecentFirstThenAdded() throws {
        let pet = UUID()
        let a = try SnackRules.add(name: "가", kind: .jerky, okDuringFoodChange: false, now: at(2026, 10, 1, 9), in: context)
        let b = try SnackRules.add(name: "나", kind: .jerky, okDuringFoodChange: false, now: at(2026, 10, 2, 9), in: context)
        let c = try SnackRules.add(name: "다", kind: .jerky, okDuringFoodChange: false, now: at(2026, 10, 3, 9), in: context)
        try SnackRules.record(snackId: c.id, amount: "", occurredAt: at(2026, 10, 24, 9), editing: nil, petId: pet, timeZone: seoul, in: context)
        try SnackRules.record(snackId: b.id, amount: "", occurredAt: at(2026, 10, 25, 9), editing: nil, petId: pet, timeZone: seoul, in: context)
        try SnackRules.record(snackId: a.id, amount: "", occurredAt: at(2026, 10, 25, 10), editing: nil, petId: UUID(), timeZone: seoul, in: context)
        let order = SnackRules.pickOrder(try snacks(), petId: pet, records: try records())
        XCTAssertEqual(order.map(\.snack.id), [b.id, c.id, a.id])
        XCTAssertEqual(order.map(\.lastDay), ["2026-10-25", "2026-10-24", nil])
        XCTAssertEqual(SnackRules.lastGiven("2026-10-25", today: "2026-10-25"), "오늘")
        XCTAssertEqual(SnackRules.lastGiven("2026-10-24", today: "2026-10-25"), "어제")
        XCTAssertEqual(SnackRules.lastGiven("2026-10-02", today: "2026-10-25"), "10월 2일")
    }

    func testFoodChangePeriod() throws {
        let pet = UUID()
        XCTAssertFalse(SnackRules.inFoodChange(petId: pet, day: "2026-10-20", changes: []))
        let change = try FoodRules.save(petId: pet, name: "처방식", startedOn: "2026-10-01", method: .gradual, in: context)
        let changes = try FoodRules.allChanges(in: context)
        XCTAssertTrue(SnackRules.inFoodChange(petId: pet, day: "2026-10-14", changes: changes)) // 14일째까지
        XCTAssertFalse(SnackRules.inFoodChange(petId: pet, day: "2026-10-15", changes: changes))
        try FoodRules.finished(change, today: "2026-10-10", in: context)
        XCTAssertTrue(SnackRules.inFoodChange(petId: pet, day: "2026-10-09", changes: changes)) // 다 바꾼 날 전날까지
        XCTAssertFalse(SnackRules.inFoodChange(petId: pet, day: "2026-10-10", changes: changes))
    }
}
