import SwiftData
import XCTest
@testable import Okche

/// 인슐린 줄·시트 규칙(plan.md 9-2). 안드로이드 InsulinRulesTest와 같은 경우.
@MainActor
final class InsulinRulesTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let pet = UUID()
    private func at(_ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h, minute: min))!
    }
    private func plan(_ times: [(String, Double)] = [("08:00", 2), ("20:00", 1.5)]) -> InsulinPlan {
        InsulinPlan(petId: pet, name: "글라진", slots: times.map { InsulinSlot(time: $0.0, units: $0.1) }, activeFrom: at(1, 9))
    }
    private func record(_ status: String, at time: Date, units: Double = 2, id: UUID = UUID()) -> Record {
        Record(id: id, petId: pet, kind: .insulin, occurredAt: time, data: "{\"status\":\"\(status)\",\"units\":\(units)}")
    }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testSlot() throws {
        let p = plan()
        let morning = try XCTUnwrap(InsulinRules.slot(p, at: at(27, 9)))
        XCTAssertEqual(morning.time, "08:00")
        XCTAssertEqual(morning.units, 2)
        XCTAssertEqual(morning.start, at(27, 7))
        XCTAssertEqual(morning.end, at(27, 19))
        XCTAssertEqual(InsulinRules.next(morning), at(27, 20))
        // 다음 날 02:00은 저녁 차례(19:00~07:00), 차례 날은 20:00의 날.
        let evening = try XCTUnwrap(InsulinRules.slot(p, at: at(28, 2)))
        XCTAssertEqual(evening.time, "20:00")
        XCTAssertEqual(evening.units, 1.5)
        XCTAssertEqual(evening.key, "\(p.id.uuidString.lowercased())|2026-10-27|20:00")
    }

    func testHandledAndTwice() throws {
        let p = plan()
        let slot = try XCTUnwrap(InsulinRules.slot(p, at: at(27, 9)))
        XCTAssertNil(InsulinRules.handled(petId: pet, records: [], slot: slot))
        let skipped = record("skipped", at: at(27, 8, 2), units: 0)
        XCTAssertEqual(InsulinRules.handled(petId: pet, records: [skipped], slot: slot)?.id, skipped.id) // 건너뜀도 처리함
        XCTAssertNil(InsulinRules.handled(petId: pet, records: [record("full", at: at(27, 6, 59))], slot: slot)) // 구간 밖
        // 이미 맞은 시간대: 다 맞음·일부만만, 고치는 기록 자신은 빼고.
        XCTAssertNil(InsulinRules.twice(petId: pet, records: [skipped], slot: slot, editing: nil))
        let full = record("full", at: at(27, 8, 2))
        XCTAssertEqual(InsulinRules.twice(petId: pet, records: [full], slot: slot, editing: nil)?.id, full.id)
        XCTAssertNil(InsulinRules.twice(petId: pet, records: [full], slot: slot, editing: full))
        XCTAssertNotNil(InsulinRules.twice(petId: pet, records: [record("partial", at: at(27, 10))], slot: slot, editing: nil))
    }

    func testSaveValueUnits() throws {
        let p = plan()
        context.insert(p)
        let r = try InsulinRules.save(petId: pet, plan: p, units: 2, status: .full, mealBefore: nil, at: at(27, 8, 2), in: context)
        XCTAssertEqual(r.data, "{\"planId\":\"\(p.id.uuidString.lowercased())\",\"status\":\"full\",\"units\":2}")
        XCTAssertEqual(r.slotKey, "\(p.id.uuidString.lowercased())|2026-10-27|08:00")
        XCTAssertEqual(InsulinRules.value(r), "2단위 맞음")
        try InsulinRules.save(petId: pet, plan: p, units: 1.5, status: .partial, mealBefore: "none", at: at(27, 8, 2), editing: r, in: context)
        XCTAssertEqual(QuickRecord.value(r), "1.5단위 일부만 · 밥 안 먹음")
        let s = try InsulinRules.save(petId: pet, plan: p, units: 2, status: .skipped, mealBefore: nil, at: at(27, 20, 5), in: context)
        XCTAssertEqual(InsulinRules.unitsValue(s), 0)
        XCTAssertEqual(InsulinRules.value(s), "건너뜀")
        XCTAssertTrue(MainCard.kinds.contains("insulin"))
        XCTAssertNil(InsulinRules.units("0"))
        XCTAssertEqual(InsulinRules.units("1.5"), 1.5)
        XCTAssertNil(InsulinRules.units("100.5"))
        XCTAssertEqual(InsulinRules.unitsError("0"), "0보다 크고 100 이하인 숫자로 넣어 주세요")
    }

    /// 양을 바꾼 뒤에도 어제 기록 고치기·지난 시각 새 기록은 그때 계획으로.
    func testPlanAtTime() throws {
        let a = plan()
        a.activeUntil = at(27, 12)
        let b = InsulinPlan(petId: pet, slots: [InsulinSlot(time: "08:00", units: 3), InsulinSlot(time: "20:00", units: 3)], activeFrom: at(27, 12))
        context.insert(a); context.insert(b)
        XCTAssertEqual(InsulinRules.planAt([a, b], petId: pet, at: at(26, 8))?.id, a.id)
        XCTAssertEqual(InsulinRules.planAt([a, b], petId: pet, at: at(27, 13))?.id, b.id)
        let old = try InsulinRules.save(petId: pet, plan: a, units: 2, status: .full, mealBefore: nil, at: at(26, 8, 2), now: at(26, 9), in: context)
        let key = old.slotKey
        // 시간을 안 바꾸고 밥만 고치면 계획·차례 그대로(지금 열린 계획 B가 넘어와도).
        try InsulinRules.save(petId: pet, plan: b, units: 2, status: .full, mealBefore: "none", at: at(26, 8, 2), editing: old, now: at(27, 14), in: context)
        XCTAssertEqual(old.slotKey, key)
        XCTAssertEqual(InsulinRules.planId(old), a.id)
        // 그 시간의 양이 없는 차례는 없음(안드로이드와 같음) — 08:00 하나면 저녁도 08:00 차례.
        XCTAssertEqual(InsulinRules.slot(plan([("08:00", 2)]), at: at(27, 20))?.time, "08:00")
    }
}
