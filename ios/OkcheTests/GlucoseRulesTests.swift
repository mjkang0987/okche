import SwiftData
import XCTest
@testable import Okche

/// 혈당 규칙(plan.md 9-1). 안드로이드 GlucoseRulesTest와 같은 경우.
@MainActor
final class GlucoseRulesTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let pet = UUID()
    private func at(_ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        Calendar.current.date(from: DateComponents(year: 2026, month: 10, day: d, hour: h, minute: min))!
    }
    private func plan(_ times: [String] = ["08:00", "20:00"], from: Date? = nil, until: Date? = nil) -> InsulinPlan {
        let p = InsulinPlan(petId: pet, slots: times.map { InsulinSlot(time: $0, units: 2) }, activeFrom: from ?? at(1, 9))
        p.activeUntil = until
        return p
    }
    private func insulin(_ status: String, at time: Date) -> Record {
        Record(petId: pet, kind: .insulin, occurredAt: time, data: "{\"status\":\"\(status)\",\"units\":2}")
    }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testNumber() {
        XCTAssertNil(GlucoseRules.number("0"))
        XCTAssertEqual(GlucoseRules.number("1"), 1)
        XCTAssertEqual(GlucoseRules.number("999"), 999)
        XCTAssertNil(GlucoseRules.number("1000"))
        XCTAssertNil(GlucoseRules.number("1.5"))
        XCTAssertEqual(GlucoseRules.number(" 186 "), 186)
        XCTAssertNil(GlucoseRules.number(""))
        XCTAssertNil(GlucoseRules.error(""))
        XCTAssertEqual(GlucoseRules.error("1000"), "1에서 999 사이로 넣어 주세요")
    }

    func testWindow() throws {
        let p = plan()
        // 08:00·20:00 → 07:00~19:00 아침, 19:00~다음 날 07:00 저녁.
        XCTAssertEqual(try XCTUnwrap(GlucoseRules.window(p, now: at(27, 7))).start, at(27, 7))
        XCTAssertEqual(try XCTUnwrap(GlucoseRules.window(p, now: at(27, 7))).end, at(27, 19))
        XCTAssertEqual(try XCTUnwrap(GlucoseRules.window(p, now: at(27, 6, 59))).start, at(26, 19))
        XCTAssertEqual(try XCTUnwrap(GlucoseRules.window(p, now: at(28, 1))).start, at(27, 19))
        XCTAssertEqual(try XCTUnwrap(GlucoseRules.window(p, now: at(28, 1))).end, at(28, 7))
        XCTAssertNil(GlucoseRules.window(plan([]), now: at(27, 7)))
        // 자정 넘은 차례 하나(00:30) — 23:45는 23:30~다음 날 23:30.
        XCTAssertEqual(try XCTUnwrap(GlucoseRules.window(plan(["00:30"]), now: at(27, 23, 45))).end, at(28, 23, 30))
    }

    func testGuess() {
        let p = plan()
        let now = at(27, 9)
        XCTAssertEqual(GlucoseRules.guess(petId: pet, plans: [p], insulinRecords: [insulin("full", at: at(27, 8))], now: now), .afterInsulin)
        XCTAssertEqual(GlucoseRules.guess(petId: pet, plans: [p], insulinRecords: [insulin("partial", at: at(27, 7, 30))], now: now), .afterInsulin)
        XCTAssertNil(GlucoseRules.guess(petId: pet, plans: [p], insulinRecords: [insulin("skipped", at: at(27, 8))], now: now))
        XCTAssertNil(GlucoseRules.guess(petId: pet, plans: [p], insulinRecords: [], now: now))
        XCTAssertNil(GlucoseRules.guess(petId: pet, plans: [p], insulinRecords: [insulin("full", at: at(27, 6, 59))], now: now)) // 구간 밖
        XCTAssertEqual(GlucoseRules.guess(petId: pet, plans: [], insulinRecords: [], now: now), .other)
        XCTAssertEqual(GlucoseRules.guess(petId: pet, plans: [plan(until: at(20, 9))], insulinRecords: [], now: now), .other) // 닫힌 계획뿐
    }

    func testSaveAndValue() throws {
        let r = try GlucoseRules.save(petId: pet, mgdl: 186, timing: .afterInsulin, signs: [.seizure, .wobbly], at: at(27, 7, 50), in: context)
        XCTAssertEqual(r.kind, RecordKind.glucose.rawValue)
        XCTAssertEqual(r.data, "{\"mgdl\":186,\"signs\":[\"wobbly\",\"seizure\"],\"timing\":\"afterInsulin\"}")
        XCTAssertEqual(GlucoseRules.value(r), "186 · 맞고 난 뒤 · 비틀거림 · 경련")
        XCTAssertEqual(QuickRecord.value(r), "186 · 맞고 난 뒤 · 비틀거림 · 경련")
        try GlucoseRules.save(petId: pet, mgdl: 90, timing: .beforeInsulin, signs: [], at: at(27, 7, 50), editing: r, in: context)
        XCTAssertEqual(GlucoseRules.value(r), "90 · 맞기 전")
        XCTAssertTrue(MainCard.kinds.contains("glucose"))
    }
}
