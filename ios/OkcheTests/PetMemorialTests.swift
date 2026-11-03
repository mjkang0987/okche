import SwiftData
import XCTest
@testable import Okche

/// 추억으로 간직하기·되돌리기·아이 지우기·추억 화면 숫자(plan.md 6-4). 안드로이드 PetMemorialTest와 같은 경우.
@MainActor
final class PetMemorialTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()
    private func at(_ y: Int, _ m: Int, _ d: Int, _ h: Int = 9) -> Date { seoul.date(from: DateComponents(year: y, month: m, day: d, hour: h))! }
    private var created: Date { at(2026, 10, 1) }
    private var kept: Date { at(2026, 10, 20) }
    private var back: Date { at(2026, 11, 1) }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    private func all<T: PersistentModel>(_: T.Type) throws -> [T] { try context.fetch(FetchDescriptor<T>()) }

    private func pet(_ name: String = "콩이") -> Pet {
        let pet = Pet(now: created, name: name, species: .cat, breed: "코리안숏헤어", furColor: "치즈")
        context.insert(pet)
        return pet
    }

    private func med(_ petId: UUID, until: Date? = nil) -> MedSchedule {
        let med = MedSchedule(now: created, petId: petId, medicationId: UUID(), amount: 1, unit: .pill, frequency: .daily,
                              times: ["08:00"], activeFrom: created, activeUntil: until)
        context.insert(med)
        return med
    }

    func testMemorializeClosesOpenPlansAtSameTime() throws {
        let kong = pet()
        let openMed = med(kong.id)
        let closedMed = med(kong.id, until: at(2026, 10, 5))
        context.insert(InsulinPlan(now: created, petId: kong.id, slots: [InsulinSlot(time: "08:00", units: 2)], activeFrom: created))
        context.insert(MealPlan(now: created, petId: kong.id, mode: .direct, slots: [MealSlot(name: "아침", time: "08:00")], activeFrom: created))
        context.insert(RestPeriod(now: created, petId: kong.id, startedAt: created))
        let other = pet("보리")
        let otherMed = med(other.id)
        try context.save()

        try PetLifecycle.memorialize(kong, now: kept, in: context)
        XCTAssertEqual(kong.memorializedAt, kept)
        XCTAssertEqual(openMed.activeUntil, kept)
        XCTAssertEqual(closedMed.activeUntil, at(2026, 10, 5)) // 이미 닫힌 줄은 그대로
        XCTAssertEqual(try all(InsulinPlan.self).map(\.activeUntil), [kept])
        XCTAssertEqual(try all(MealPlan.self).map(\.activeUntil), [kept])
        XCTAssertEqual(try all(RestPeriod.self).map(\.endedAt), [kept])
        XCTAssertNil(otherMed.activeUntil) // 다른 아이는 그대로
    }

    func testRestoreReopensOnlyRowsClosedWhenKept() throws {
        let kong = pet()
        let openMed = med(kong.id)
        _ = med(kong.id, until: at(2026, 10, 5))
        context.insert(InsulinPlan(now: created, petId: kong.id, name: "란투스", slots: [InsulinSlot(time: "08:00", units: 2)], activeFrom: created))
        context.insert(RestPeriod(now: created, petId: kong.id, startedAt: created))
        try context.save()
        try PetLifecycle.memorialize(kong, now: kept, in: context)

        try PetLifecycle.restore(kong, now: back, in: context)
        XCTAssertNil(kong.memorializedAt)
        let meds = try all(MedSchedule.self)
        let reopened = meds.filter { $0.activeUntil == nil }
        XCTAssertEqual(reopened.count, 1)
        XCTAssertEqual(reopened.first?.seriesId, openMed.seriesId) // 약 주기 seriesId 그대로
        XCTAssertEqual(reopened.first?.activeFrom, back)
        XCTAssertEqual(meds.count, 3)
        let plans = try all(InsulinPlan.self).filter { $0.activeUntil == nil }
        XCTAssertEqual(plans.map(\.name), ["란투스"])
        XCTAssertEqual(try RestPeriod.open(for: kong.id, in: context).count, 0) // 휴양은 다시 안 염
    }

    func testDeleteMarksEverythingAndKeepsSharedPhotos() throws {
        let kong = pet()
        let bori = pet("보리")
        context.insert(Record(now: created, petId: kong.id, kind: .vomit, occurredAt: created))
        let boriRecord = Record(now: created, petId: bori.id, kind: .vomit, occurredAt: created)
        context.insert(boriRecord)
        _ = med(kong.id)
        context.insert(FoodChange(now: created, petId: kong.id, foodId: UUID(), startedOn: "2026-10-01", method: .gradual))
        let alone = Photo(now: created, takenAt: created, localDate: "2026-10-01", fileExtension: "jpg")
        let shared = Photo(now: created, takenAt: created, localDate: "2026-10-01", fileExtension: "jpg")
        context.insert(alone)
        context.insert(shared)
        context.insert(PhotoPet(now: created, photoId: alone.id, petId: kong.id))
        context.insert(PhotoPet(now: created, photoId: shared.id, petId: kong.id))
        context.insert(PhotoPet(now: created, photoId: shared.id, petId: bori.id))
        try context.save()

        try PetLifecycle.delete(kong, now: kept, in: context)
        XCTAssertEqual(kong.deletedAt, kept)
        XCTAssertEqual(try all(Record.self).filter { $0.petId == kong.id }.map(\.deletedAt), [kept])
        XCTAssertNil(boriRecord.deletedAt)
        XCTAssertEqual(try all(MedSchedule.self).map(\.deletedAt), [kept])
        XCTAssertEqual(try all(FoodChange.self).map(\.deletedAt), [kept])
        XCTAssertEqual(alone.deletedAt, kept) // 남은 아이가 없는 사진은 지움
        XCTAssertNil(shared.deletedAt) // 보리와 함께 찍힌 사진은 남김
        XCTAssertEqual(try all(PhotoPet.self).filter { $0.deletedAt == nil }.map(\.petId), [bori.id])
    }

    func testMemorialStats() throws {
        let kong = pet()
        let today = at(2026, 11, 3)
        XCTAssertEqual(try PetLifecycle.stats(kong, today: today, calendar: seoul, in: context),
                       .init(days: nil, first: nil, last: nil)) // 가족 된 날·기록 모두 없음
        context.insert(Record(now: created, petId: kong.id, kind: .vomit, occurredAt: at(2026, 10, 25)))
        context.insert(Record(now: created, petId: kong.id, kind: .stool, occurredAt: at(2026, 3, 14)))
        let deleted = Record(now: created, petId: kong.id, kind: .stool, occurredAt: at(2025, 1, 1))
        deleted.deletedAt = created
        context.insert(deleted)
        // 가족 된 날이 없으면 첫 기록 날부터(첫날 = 1일)
        XCTAssertEqual(try PetLifecycle.stats(kong, today: today, calendar: seoul, in: context),
                       .init(days: 235, first: "2026년 3월 14일", last: "2026년 10월 25일"))
        kong.familyDate = "2023-05-26"
        XCTAssertEqual(try PetLifecycle.stats(kong, today: today, calendar: seoul, in: context).days, 1258)
    }
}
