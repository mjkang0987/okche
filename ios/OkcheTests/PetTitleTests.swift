import SwiftData
import XCTest
@testable import Okche

/// 호칭·나이 표기·지금 보는 아이·휴양 바로 저장(plan.md 6-3). 안드로이드 PetSealTest와 같은 경우.
@MainActor
final class PetTitleTests: XCTestCase {
    private let seoul: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()
    private func day(_ y: Int, _ m: Int, _ d: Int, hour: Int = 9) -> Date {
        seoul.date(from: DateComponents(year: y, month: m, day: d, hour: hour))!
    }

    func testHonorificTurnsMamaOnFirstBirthday() {
        XCTAssertEqual(PetTitle.honorific("2025-11-03", today: day(2026, 11, 2), calendar: seoul), "아기씨") // 전날
        XCTAssertEqual(PetTitle.honorific("2025-11-03", today: day(2026, 11, 3, hour: 0), calendar: seoul), "마마") // 당일 0시
        XCTAssertEqual(PetTitle.honorific("2019-10-25", today: day(2026, 11, 3), calendar: seoul), "마마")
        XCTAssertEqual(PetTitle.honorific(nil, today: day(2026, 11, 3), calendar: seoul), "아기씨") // 생일 없음
    }

    func testLeapDayBirthTurnsMamaOnFeb28() {
        XCTAssertEqual(PetTitle.honorific("2024-02-29", today: day(2025, 2, 27), calendar: seoul), "아기씨")
        XCTAssertEqual(PetTitle.honorific("2024-02-29", today: day(2025, 2, 28), calendar: seoul), "마마")
        XCTAssertEqual(PetTitle.age("2024-02-29", today: day(2025, 2, 28), calendar: seoul), "1살")
    }

    func testAgeInMonthsUnderOneYear() {
        XCTAssertEqual(PetTitle.age("2026-10-20", today: day(2026, 11, 3), calendar: seoul), "0개월")
        XCTAssertEqual(PetTitle.age("2025-12-03", today: day(2026, 11, 2), calendar: seoul), "10개월")
        XCTAssertEqual(PetTitle.age("2025-11-04", today: day(2026, 11, 3), calendar: seoul), "11개월")
        XCTAssertEqual(PetTitle.age("2025-11-03", today: day(2026, 11, 3), calendar: seoul), "1살")
        XCTAssertEqual(PetTitle.age("2026-01-31", today: day(2026, 2, 28), calendar: seoul), "1개월") // 달 끝
        XCTAssertNil(PetTitle.age(nil, today: day(2026, 11, 3), calendar: seoul))
    }

    func testObjectParticle() {
        XCTAssertEqual(PetTitle.withObjectParticle("콩이"), "콩이를")
        XCTAssertEqual(PetTitle.withObjectParticle("똘똘"), "똘똘을")
        XCTAssertEqual(PetTitle.withObjectParticle("Coco"), "Coco을(를)")
    }

    /// 추억으로 간직한 아이는 호칭 없이 이름만, 목록 맨 뒤(6-4).
    func testMemorialNameAndOrder() throws {
        let container = try OkcheStore.container(inMemory: true)
        let first = Pet(now: day(2026, 10, 1), name: "두부", species: .cat, breed: "코리안숏헤어", furColor: "치즈")
        let second = Pet(now: day(2026, 10, 2), name: "콩이", species: .cat, breed: "코리안숏헤어", furColor: "치즈")
        container.mainContext.insert(first)
        container.mainContext.insert(second)
        first.memorializedAt = day(2026, 10, 20)
        XCTAssertEqual(PetTitle.name(first, today: day(2026, 11, 3), calendar: seoul), "두부")
        XCTAssertEqual(PetTitle.name(second, today: day(2026, 11, 3), calendar: seoul), "콩이 아기씨")
        XCTAssertEqual(PetTitle.ordered([first, second]).map(\.name), ["콩이", "두부"])
    }

    func testCurrentPet() throws {
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        let first = Pet(now: day(2026, 10, 1), name: "콩이", species: .cat, breed: "코리안숏헤어", furColor: "치즈")
        let second = Pet(now: day(2026, 10, 2), name: "보리", species: .cat, breed: "코리안숏헤어", furColor: "치즈")
        context.insert(first)
        context.insert(second)
        let pets = [first, second]
        XCTAssertNil(PetTitle.current([], lastPetId: ""))
        XCTAssertEqual(PetTitle.current(pets, lastPetId: "").map(\.name), "콩이") // 없으면 가장 먼저 모신 아이
        XCTAssertEqual(PetTitle.current(pets, lastPetId: second.id.uuidString).map(\.name), "보리")
        XCTAssertEqual(PetTitle.current(pets, lastPetId: UUID().uuidString).map(\.name), "콩이") // 지운 아이
    }

    func testSetRestingSavesRightAway() throws {
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        let petId = UUID()
        let open = { try context.fetch(FetchDescriptor<RestPeriod>(predicate: #Predicate { $0.endedAt == nil })) }
        try RestPeriod.setResting(true, petId: petId, now: day(2026, 11, 1), in: context)
        try RestPeriod.setResting(true, petId: petId, now: day(2026, 11, 2), in: context) // 이미 열림 → 그대로
        XCTAssertEqual(try open().map(\.startedAt), [day(2026, 11, 1)])
        XCTAssertFalse(context.hasChanges)
        try RestPeriod.setResting(false, petId: petId, now: day(2026, 11, 3), in: context)
        XCTAssertEqual(try open().count, 0)
        XCTAssertEqual(try context.fetch(FetchDescriptor<RestPeriod>()).map(\.endedAt), [day(2026, 11, 3)])
    }
}
