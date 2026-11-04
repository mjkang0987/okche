import SwiftData
import XCTest
@testable import Okche

/// 약방 규칙(plan.md 8-1). 안드로이드 YakbangTest와 같은 경우.
@MainActor
final class YakbangTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let t0 = Date(timeIntervalSince1970: 1_790_000_000)

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    private func form(_ name: String, form: MedForm = .pill, dose: String = "1", stock: String = "") -> MedRules.Form {
        var f = MedRules.Form()
        f.name = name
        f.form = form
        f.dose = dose
        f.stock = stock
        return f
    }

    private func medRecord(_ med: Medication, _ status: String, _ amount: Double, unit: String? = nil, at: Date) -> Record {
        let data = try! JSONSerialization.data(withJSONObject: ["medicationId": med.id.uuidString.lowercased(), "status": status,
                                                                "amount": amount, "unit": unit ?? med.unit.rawValue], options: [.sortedKeys])
        let record = Record(petId: UUID(), kind: .med, occurredAt: at, data: String(decoding: data, as: UTF8.self))
        context.insert(record)
        return record
    }

    func testUnitsFollowFormAndNumbers() {
        XCTAssertEqual(MedForm.allCases.map { MedRules.unitTitle(MedRules.unit($0)) }, ["알", "포", "ml", "방울", "ml"])
        XCTAssertEqual(MedRules.number(1), "1")
        XCTAssertEqual(MedRules.number(0.5), "0.5")
        XCTAssertEqual(MedRules.number(24.0), "24")
    }

    func testErrors() {
        XCTAssertEqual(MedRules.errors(form("  ", dose: "")), [.name: "약 이름을 넣어 주세요", .dose: "한 번에 줄 양을 넣어 주세요"])
        XCTAssertEqual(MedRules.errors(form("약", dose: "0")), [.dose: "0보다 큰 숫자로 넣어 주세요"])
        XCTAssertEqual(MedRules.errors(form("약", dose: "0,5", stock: "-1")), [.stock: "0 이상 숫자로 넣어 주세요"])
        var long = form("프레드니솔론")
        long.shortName = "일곱글자짧은이"
        XCTAssertEqual(MedRules.errors(long), [.shortName: "6자까지 넣어 주세요"])
        XCTAssertTrue(MedRules.errors(form("약", dose: "0.5", stock: "0")).isEmpty)
    }

    func testRemainingSubtractsGivenAndVomitedSameUnitAfterCount() throws {
        var f = form(" 프레드니솔론 ", stock: "24")
        f.shortName = "  "
        let med = try MedRules.add(f, now: t0, in: context)
        XCTAssertEqual(med.name, "프레드니솔론")
        XCTAssertNil(med.shortName)
        XCTAssertEqual(med.unit, .pill)
        XCTAssertEqual(med.stockCountedAt, t0)
        _ = medRecord(med, "given", 1, at: t0.addingTimeInterval(60))
        _ = medRecord(med, "vomited", 0.5, at: t0.addingTimeInterval(120)) // 토했어도 약은 씀
        _ = medRecord(med, "skipped", 1, at: t0.addingTimeInterval(180))
        _ = medRecord(med, "given", 5, unit: "ml", at: t0.addingTimeInterval(240)) // 단위가 다르면 안 뺌
        _ = medRecord(med, "given", 3, at: t0.addingTimeInterval(-60)) // 적기 전
        let deleted = medRecord(med, "given", 2, at: t0.addingTimeInterval(300))
        deleted.deletedAt = .now // 지우면 돌아옴
        let records = try context.fetch(FetchDescriptor<Record>())
        XCTAssertEqual(MedRules.remaining(med, records: records), 22.5)
        XCTAssertEqual(MedRules.detail(med, records: records), "알약 · 1알씩 · 남은 22.5알")
        let syrup = try MedRules.add(form("위장약 시럽", form: .liquid), in: context)
        XCTAssertEqual(MedRules.detail(syrup, records: records), "물약 · 1ml씩 · 남은 양 안 적음")
    }

    func testUpdateOnlyWhenChangedAndStockResets() throws {
        let med = try MedRules.add(form("유산균", form: .powder, stock: "30"), now: t0, in: context)
        _ = medRecord(med, "given", 1, at: t0.addingTimeInterval(60))
        let records = try context.fetch(FetchDescriptor<Record>())
        let original = MedRules.Form(med, records: records)
        XCTAssertEqual(original.stock, "29") // 계산한 지금 남은 양
        XCTAssertFalse(try MedRules.update(med, from: original, to: original, now: t0.addingTimeInterval(500), in: context))
        XCTAssertEqual(med.updatedAt, t0)
        var memo = original
        memo.memo = "아침에"
        XCTAssertTrue(try MedRules.update(med, from: original, to: memo, now: t0.addingTimeInterval(600), in: context))
        XCTAssertEqual(med.stockAmount, 30) // 남은 양 칸을 안 바꾸면 그대로
        XCTAssertEqual(med.stockCountedAt, t0)
        var stock = memo
        stock.stock = "10"
        stock.form = .liquid
        try MedRules.update(med, from: memo, to: stock, now: t0.addingTimeInterval(700), in: context)
        XCTAssertEqual(med.stockAmount, 10)
        XCTAssertEqual(med.stockCountedAt, t0.addingTimeInterval(700))
        XCTAssertEqual(med.unit, .ml) // 모양을 바꾸면 단위도
        var cleared = stock
        cleared.stock = ""
        try MedRules.update(med, from: stock, to: cleared, in: context)
        XCTAssertNil(med.stockAmount)
        XCTAssertNil(med.stockCountedAt)
    }

    func testFormChangeRecountsFromShownStock() throws {
        let med = try MedRules.add(form("약", stock: "30"), now: t0, in: context)
        _ = medRecord(med, "given", 10, at: t0.addingTimeInterval(60))
        let original = MedRules.Form(med, records: try context.fetch(FetchDescriptor<Record>()))
        var liquid = original
        liquid.form = .liquid // 모양만 바꿈
        try MedRules.update(med, from: original, to: liquid, now: t0.addingTimeInterval(500), in: context)
        XCTAssertEqual(med.stockAmount, 20) // 보이던 지금 남은 양으로 다시 셈(옛 단위 기록이 되돌아오지 않게)
        XCTAssertEqual(med.stockCountedAt, t0.addingTimeInterval(500))
        XCTAssertEqual(MedRules.remaining(med, records: try context.fetch(FetchDescriptor<Record>())), 20)
    }

    func testRemoveBlockedWhileTakingAndGrouped() throws {
        let kong = Pet(name: "콩이", species: .cat, breed: "", furColor: "")
        kong.createdAt = t0
        let bori = Pet(name: "보리", species: .dog, breed: "", furColor: "")
        bori.createdAt = t0.addingTimeInterval(10)
        context.insert(kong)
        context.insert(bori)
        let syrup = try MedRules.add(form("위장약 시럽", form: .liquid), in: context)
        var sup = form("유산균", form: .powder)
        sup.category = .supplement
        try MedRules.add(sup, in: context)
        let open = MedSchedule(petId: bori.id, medicationId: syrup.id, amount: 1, unit: .ml, frequency: .daily, activeFrom: t0)
        let closed = MedSchedule(petId: kong.id, medicationId: syrup.id, amount: 1, unit: .ml, frequency: .daily, activeFrom: t0)
        closed.activeUntil = t0.addingTimeInterval(100)
        context.insert(open)
        context.insert(closed)
        let schedules = try context.fetch(FetchDescriptor<MedSchedule>())
        let pets = [kong, bori]
        XCTAssertEqual(MedRules.takers(syrup, schedules: schedules, pets: pets), ["보리"])
        XCTAssertEqual(MedRules.blockedMessage(syrup, takers: ["보리"]),
                       "위장약 시럽은 보리가 먹고 있는 약이에요. 먼저 탕약에서 이 약을 '그만 먹이기'로 바꿔 주세요.")
        XCTAssertTrue(MedRules.blockedMessage(syrup, takers: ["콩이", "보리"]).hasPrefix("위장약 시럽은 콩이·보리가 먹고 있는"))
        open.activeUntil = t0.addingTimeInterval(200) // 그만 먹이면 뺄 수 있음
        XCTAssertTrue(MedRules.takers(syrup, schedules: schedules, pets: pets).isEmpty)
        XCTAssertEqual(MedRules.grouped(try context.fetch(FetchDescriptor<Medication>())).map(\.title), ["약", "보조제"])
        try MedRules.remove(syrup, in: context)
        XCTAssertEqual(MedRules.grouped(try context.fetch(FetchDescriptor<Medication>())).map(\.title), ["보조제"]) // 빈 묶음 숨김
        XCTAssertEqual(syrup.name, "위장약 시럽") // 이름은 남음
    }
}
