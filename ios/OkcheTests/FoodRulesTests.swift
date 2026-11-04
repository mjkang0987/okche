import SwiftData
import XCTest
@testable import Okche

/// 사료 바꾸기 규칙(plan.md 7-4a). 안드로이드 FoodRulesTest와 같은 경우.
@MainActor
final class FoodRulesTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul = TimeZone(identifier: "Asia/Seoul")!
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int, _ min: Int = 0) -> Date {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = seoul
        return c.date(from: DateComponents(year: y, month: m, day: d, hour: h, minute: min))!
    }
    private func changes() throws -> [FoodChange] { try FoodRules.allChanges(in: context) }
    private func foods() throws -> [Food] { try context.fetch(FetchDescriptor<Food>()) }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    func testSameNameFoodIsReusedAndCurrentIsLatest() throws {
        let pet = UUID()
        let a = try FoodRules.save(petId: pet, name: " 건사료 ", startedOn: "2026-10-01", method: .gradual, now: at(2026, 10, 1, 9), in: context)
        let b = try FoodRules.save(petId: pet, name: "가수분해 처방식", startedOn: "2026-10-14", method: .atOnce, now: at(2026, 10, 14, 9), in: context)
        let c = try FoodRules.save(petId: pet, name: "건사료", startedOn: "2026-10-14", method: .gradual, now: at(2026, 10, 14, 10), in: context)
        XCTAssertEqual(try foods().count, 2) // 앞뒤 빈칸 뺀 같은 이름은 한 사료
        XCTAssertEqual(a.foodId, c.foodId)
        XCTAssertEqual(try foods().first { $0.id == a.foodId }?.name, "건사료")
        // 같은 날 둘이면 늦게 만든 것
        XCTAssertEqual(FoodRules.change(at: "2026-10-20", petId: pet, in: try changes())?.id, c.id)
        XCTAssertEqual(FoodRules.change(at: "2026-10-05", petId: pet, in: try changes())?.id, a.id) // 그날 먹던 것
        XCTAssertNil(FoodRules.change(at: "2026-09-30", petId: pet, in: try changes()))
        XCTAssertEqual(FoodRules.line(b, food: try foods().first { $0.id == b.foodId }), "가수분해 처방식 · 10/14부터")
    }

    func testPreviousNamesSkipCurrentAndDuplicates() throws {
        let pet = UUID()
        try FoodRules.save(petId: pet, name: "건사료", startedOn: "2026-09-01", method: .gradual, now: at(2026, 9, 1, 9), in: context)
        try FoodRules.save(petId: pet, name: "연어 캔", startedOn: "2026-09-10", method: .gradual, now: at(2026, 9, 10, 9), in: context)
        try FoodRules.save(petId: pet, name: "건사료", startedOn: "2026-09-20", method: .gradual, now: at(2026, 9, 20, 9), in: context)
        try FoodRules.save(petId: pet, name: "가수분해 처방식", startedOn: "2026-10-14", method: .gradual, now: at(2026, 10, 14, 9), in: context)
        try FoodRules.save(petId: UUID(), name: "다른 아이 사료", startedOn: "2026-10-01", method: .gradual, in: context)
        XCTAssertEqual(FoodRules.previousNames(petId: pet, today: "2026-10-20", changes: try changes(), foods: try foods()), ["건사료", "연어 캔"])
    }

    func testCheckSteps() throws {
        let change = try FoodRules.save(petId: UUID(), name: "처방식", startedOn: "2026-10-01", method: .gradual, in: context)
        XCTAssertNil(FoodRules.check(change, today: "2026-10-06")) // 6일째
        XCTAssertEqual(FoodRules.check(change, today: "2026-10-07")?.step, 7)
        XCTAssertEqual(FoodRules.check(change, today: "2026-10-09")?.day, 9) // 다음 단계 전날까지
        try FoodRules.notYet(change, step: 7, in: context)
        XCTAssertNil(FoodRules.check(change, today: "2026-10-09"))
        XCTAssertEqual(FoodRules.check(change, today: "2026-10-10")?.step, 10)
        XCTAssertEqual(FoodRules.check(change, today: "2026-10-14")?.step, 14)
        XCTAssertNil(FoodRules.check(change, today: "2026-10-15")) // 14일까지 답 없으면 끝
        XCTAssertEqual(FoodRules.laterNote(step: 7), "아직이면 10일·12일·14일째에 한 번 더 물어볼게요.")
        XCTAssertNil(FoodRules.laterNote(step: 14))
        try FoodRules.finished(change, today: "2026-10-12", in: context)
        XCTAssertEqual(change.finishedOn, "2026-10-12")
        XCTAssertNil(FoodRules.check(change, today: "2026-10-12"))
    }

    func testMealRecordsCarryFoodOfThatDay() throws {
        let pet = UUID()
        let old = try FoodRules.save(petId: pet, name: "건사료", startedOn: "2026-10-01", method: .gradual, in: context)
        let new = try FoodRules.save(petId: pet, name: "처방식", startedOn: "2026-10-14", method: .gradual, in: context)
        let row = QuickRecord.rows.first { $0.kind == .meal }!
        let before = try QuickRecord.save(row, value: "all", petId: pet, now: at(2026, 10, 14, 5), timeZone: seoul, in: context) // 6시 전 = 13일
        let after = try MealRules.pickNow("some", petId: pet, now: at(2026, 10, 14, 8), timeZone: seoul, in: context)
        XCTAssertEqual(MealRules.json(before)["foodId"] as? String, old.foodId.uuidString.lowercased())
        XCTAssertEqual(MealRules.json(after)["foodId"] as? String, new.foodId.uuidString.lowercased())
        try MealRules.setResult("all", on: after, in: context)
        XCTAssertEqual(MealRules.json(after)["foodId"] as? String, new.foodId.uuidString.lowercased()) // 고쳐도 그대로
        let noFood = try QuickRecord.save(row, value: "all", petId: nil, now: at(2026, 10, 14, 9), timeZone: seoul, in: context)
        XCTAssertNil(MealRules.json(noFood)["foodId"])
        try QuickRecord.assign(noFood, to: pet, in: context) // 누구였는지 정하면 그 아이가 그날 먹던 사료
        XCTAssertEqual(MealRules.json(noFood)["foodId"] as? String, new.foodId.uuidString.lowercased())
        XCTAssertEqual(MealRules.json(noFood)["result"] as? String, "all")
    }
}
