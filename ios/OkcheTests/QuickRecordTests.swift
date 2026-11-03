import SwiftData
import XCTest
@testable import Okche

/// 바로 기록·방금 남긴 기록·기록 목록 규칙(plan.md 7-1). 안드로이드 QuickRecordTest와 같은 경우.
@MainActor
final class QuickRecordTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul = TimeZone(identifier: "Asia/Seoul")!
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
    private func row(_ kind: RecordKind) -> QuickRecord.Row { QuickRecord.rows.first { $0.kind == kind }! }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testSaveStoresValueAndSixAmDay() throws {
        let petId = UUID()
        let vomit = try QuickRecord.save(row(.vomit), value: "kibble", petId: petId, now: at(2026, 10, 25, 5, 59), timeZone: seoul, in: context)
        XCTAssertEqual(vomit.day, "2026-10-24") // 6시 전은 전날
        XCTAssertEqual(vomit.data, #"{"contents":["kibble"]}"#)
        XCTAssertEqual(QuickRecord.value(vomit), "사료")
        let stool = try QuickRecord.save(row(.stool), value: "normal", petId: nil, now: at(2026, 10, 25, 6), timeZone: seoul, in: context)
        XCTAssertEqual(stool.day, "2026-10-25")
        XCTAssertNil(stool.petId) // 누군지 모름
        XCTAssertEqual(stool.data, #"{"shape":"normal"}"#)
        XCTAssertEqual(QuickRecord.value(stool), "정상")
        let meal = try QuickRecord.save(row(.meal), value: "some", petId: petId, now: at(2026, 10, 25, 8), timeZone: seoul, in: context)
        XCTAssertEqual(QuickRecord.value(meal), "남김")
    }

    func testLatestTodaySkipsDeletedAndOtherDays() throws {
        _ = try QuickRecord.save(row(.urine), value: "little", petId: nil, now: at(2026, 10, 24, 22), timeZone: seoul, in: context)
        let first = try QuickRecord.save(row(.stool), value: "hard", petId: nil, now: at(2026, 10, 25, 7), timeZone: seoul, in: context)
        let second = try QuickRecord.save(row(.vomit), value: "digested", petId: nil, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        XCTAssertEqual(try QuickRecord.latest(today: "2026-10-25", in: context)?.id, second.id)
        try QuickRecord.delete(second, now: at(2026, 10, 25, 9, 5), in: context)
        XCTAssertEqual(second.data, "{}") // 지우면 data 비움
        XCTAssertEqual(try QuickRecord.latest(today: "2026-10-25", in: context)?.id, first.id)
        XCTAssertNil(try QuickRecord.latest(today: "2026-10-26", in: context)) // 날 바뀜
    }

    func testDayRecordsFilterSummaryAndAssign() throws {
        let kong = UUID(), bori = UUID()
        _ = try QuickRecord.save(row(.stool), value: "normal", petId: kong, now: at(2026, 10, 25, 9), timeZone: seoul, in: context)
        _ = try QuickRecord.save(row(.stool), value: "soft", petId: bori, now: at(2026, 10, 25, 7), timeZone: seoul, in: context)
        let unknown = try QuickRecord.save(row(.vomit), value: "kibble", petId: nil, now: at(2026, 10, 26, 2), timeZone: seoul, in: context)
        let all = try QuickRecord.records(day: "2026-10-25", petId: nil, in: context)
        XCTAssertEqual(all.count, 3) // 02:14 같은 새벽 기록은 전날
        XCTAssertEqual(all.first?.id, unknown.id) // 늦은 것 위
        XCTAssertEqual(try QuickRecord.records(day: "2026-10-25", petId: kong, in: context).count, 1)
        XCTAssertEqual(QuickRecord.summary(all), "구토 1번 · 매화 2번")
        XCTAssertNil(QuickRecord.summary([]))
        try QuickRecord.assign(unknown, to: bori, now: at(2026, 10, 26, 8), in: context)
        XCTAssertEqual(unknown.petId, bori)
        XCTAssertEqual(unknown.updatedAt, at(2026, 10, 26, 8))
    }

    func testDayShiftAndTitle() {
        XCTAssertEqual(QuickRecord.shift("2026-03-01", by: -1), "2026-02-28")
        XCTAssertEqual(QuickRecord.shift("2026-12-31", by: 1), "2027-01-01")
        XCTAssertEqual(QuickRecord.dayTitle("2026-10-25", today: "2026-10-25"), "오늘 · 10월 25일 (일)")
        XCTAssertEqual(QuickRecord.dayTitle("2026-10-24", today: "2026-10-25"), "10월 24일 (토)")
    }
}
