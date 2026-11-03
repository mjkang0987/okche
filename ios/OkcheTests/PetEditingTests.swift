import SwiftData
import XCTest
@testable import Okche

/// 아이 정보 고치기·인슐린 설정 저장 규칙(plan.md 6-2). 안드로이드 PetEditTest와 같은 경우.
@MainActor
final class PetEditingTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()
    private var created: Date { seoul.date(from: DateComponents(year: 2026, month: 10, day: 28, hour: 9))! }
    private var now: Date { seoul.date(from: DateComponents(year: 2026, month: 10, day: 31, hour: 9))! }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    private func all<T: PersistentModel>(_: T.Type) throws -> [T] { try context.fetch(FetchDescriptor<T>()) }

    private func makePet(diabetes: Bool = false, approxAge: String? = nil) throws -> Pet {
        var form = PetRegistration()
        form.name = "콩이"
        if let approxAge {
            form.birthUnknown = true
            form.approxAge = approxAge
        }
        form.hasDiabetes = diabetes
        form.insulinSlots = [.init(time: "08:00", units: "2"), .init(time: "20:00", units: "2")]
        return try form.save(in: context, now: created, calendar: seoul)
    }

    private func editing(_ pet: Pet) -> PetEditing { PetEditing(pet: pet, context: context, today: now, calendar: seoul) }

    func testBlankNameChangesNothing() throws {
        let pet = try makePet()
        var form = editing(pet)
        form.name = "  "
        form.breed = "샴"
        XCTAssertThrowsError(try form.save(in: context, now: now, calendar: seoul)) { error in
            XCTAssertEqual((error as? PetRegistration.Invalid)?.errors[.name], "이름을 넣어 주세요")
        }
        XCTAssertEqual(pet.name, "콩이")
        XCTAssertEqual(pet.breed, "코리안숏헤어")
        XCTAssertEqual(pet.updatedAt, created)
    }

    func testSavesChangedFieldsOnly() throws {
        let pet = try makePet()
        var form = editing(pet)
        try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.updatedAt, created) // 아무것도 안 바꾸면 그대로

        form.name = " 보리 "
        form.species = .dog
        form.sex = .male
        form.familyDate = seoul.date(from: DateComponents(year: 2023, month: 5, day: 26, hour: 23))
        form.diagnosis = " 신부전 "
        try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.name, "보리")
        XCTAssertEqual(pet.species, .dog)
        XCTAssertEqual(pet.breed, "말티즈") // 종을 바꾸면 그 종의 첫째
        XCTAssertEqual(pet.furColor, "흰색")
        XCTAssertEqual(pet.sex, .male)
        XCTAssertEqual(pet.familyDate, "2023-05-26")
        XCTAssertEqual(pet.diagnosis, "신부전")
        XCTAssertEqual(pet.updatedAt, now)
    }

    func testFutureDatesAreErrors() throws {
        let pet = try makePet()
        var form = editing(pet)
        let tomorrow = seoul.date(byAdding: .day, value: 1, to: now)
        form.birthDate = tomorrow
        form.familyDate = tomorrow
        XCTAssertEqual(form.validate(today: now, calendar: seoul),
                       [.birth: "오늘보다 뒤 날짜는 넣을 수 없어요", .family: "오늘보다 뒤 날짜는 넣을 수 없어요"])
    }

    /// 어림잡은 생일은 나이를 안 고치면 그대로 — 저장할 때마다 오늘 쪽으로 밀리지 않는다.
    func testEstimatedBirthKeptUntilAgeChanges() throws {
        let pet = try makePet(approxAge: "3")
        XCTAssertEqual(pet.birthDate, "2023-10-28")
        var form = editing(pet)
        XCTAssertTrue(form.birthUnknown)
        XCTAssertEqual(form.approxAge, "3")
        form.name = "보리"
        try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.birthDate, "2023-10-28")
        XCTAssertTrue(pet.birthEstimated)

        form.approxAge = "5"
        try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.birthDate, "2021-10-31")

        // 생일을 알게 되면 어림잡음 표시를 끈다.
        var known = editing(pet)
        known.birthUnknown = false
        known.birthDate = seoul.date(from: DateComponents(year: 2021, month: 3, day: 1))
        try known.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.birthDate, "2021-03-01")
        XCTAssertFalse(pet.birthEstimated)
    }

    /// 30살로 어림잡은 아이가 한 해 지나 31살로 보여도, 나이를 안 고치면 그대로 저장된다.
    func testUnchangedEstimatedAgeOver30Saves() throws {
        let pet = try makePet(approxAge: "30")
        let nextYear = seoul.date(byAdding: .year, value: 1, to: now)!
        var form = PetEditing(pet: pet, context: context, today: nextYear, calendar: seoul)
        XCTAssertEqual(form.approxAge, "31")
        form.name = "보리"
        try form.save(in: context, now: nextYear, calendar: seoul)
        XCTAssertEqual(pet.birthDate, "1996-10-28")
        XCTAssertEqual(pet.name, "보리")
    }

    func testDiabetesOffClosesOpenPlans() throws {
        let pet = try makePet(diabetes: true)
        var form = editing(pet)
        form.hasDiabetes = false
        try form.save(in: context, now: now, calendar: seoul)
        XCTAssertFalse(pet.hasDiabetes)
        let plans = try all(InsulinPlan.self)
        XCTAssertEqual(plans.count, 1)
        XCTAssertEqual(plans[0].activeUntil, now)
    }

    func testDiabetesOnNeedsPlan() throws {
        let pet = try makePet()
        var form = editing(pet)
        form.hasDiabetes = true
        XCTAssertThrowsError(try form.save(in: context, now: now, calendar: seoul)) { error in
            XCTAssertEqual((error as? PetRegistration.Invalid)?.errors[.insulinPlan], "인슐린 시간과 양을 먼저 설정해 주세요")
        }
        XCTAssertFalse(pet.hasDiabetes)
    }

    func testRestPeriods() throws {
        let pet = try makePet()
        var form = editing(pet)
        form.resting = true
        try form.save(in: context, now: now, calendar: seoul)
        try editing(pet).save(in: context, now: now.addingTimeInterval(60), calendar: seoul) // 이미 휴양 중이면 새 줄 없음
        XCTAssertEqual(try all(RestPeriod.self).count, 1)
        XCTAssertTrue(editing(pet).resting)

        // 두 기기 백업을 합쳐 열린 기간이 둘이어도 끄면 모두 닫힌다.
        context.insert(RestPeriod(now: now, petId: pet.id, startedAt: now.addingTimeInterval(-3600)))
        let later = now.addingTimeInterval(3600)
        var off = editing(pet)
        off.resting = false
        try off.save(in: context, now: later, calendar: seoul)
        XCTAssertEqual(try all(RestPeriod.self).map(\.endedAt), [later, later])
        XCTAssertFalse(editing(pet).resting)
    }

    func testCompanionDaysCountsFirstDay() {
        XCTAssertEqual(PetEditing.companionDays("2026-10-31", today: now, calendar: seoul), 1)
        XCTAssertEqual(PetEditing.companionDays("2026-10-01", today: now, calendar: seoul), 31)
        XCTAssertNil(PetEditing.companionDays(nil, today: now, calendar: seoul))
        XCTAssertNil(PetEditing.companionDays("2026-11-01", today: now, calendar: seoul))
    }

    // MARK: 인슐린 설정

    func testInsulinSetupLoadsOpenPlan() throws {
        let pet = try makePet(diabetes: true)
        let setup = InsulinSetup(pet: pet, context: context)
        XCTAssertEqual(setup.slots.map(\.time), ["08:00", "20:00"])
        XCTAssertEqual(setup.slots.map(\.units), ["2", "2"])
        XCTAssertTrue(setup.ringDuringQuiet) // 아이 등록도 밤에도 울리기 켜짐
        XCTAssertEqual(setup.leadMinutes, 10)
    }

    func testChangingTimesMakesNewPlan() throws {
        let pet = try makePet(diabetes: true)
        var setup = InsulinSetup(pet: pet, context: context)
        setup.slots[1].time = "21:00"
        setup.slots[1].units = "2.5"
        try setup.save(in: context, now: now)
        let plans = try all(InsulinPlan.self).sorted { $0.activeFrom < $1.activeFrom }
        XCTAssertEqual(plans.count, 2)
        XCTAssertEqual(plans[0].activeUntil, now)
        XCTAssertEqual(plans[1].activeFrom, now)
        XCTAssertNil(plans[1].activeUntil)
        XCTAssertEqual(plans[1].slots, [InsulinSlot(time: "08:00", units: 2), InsulinSlot(time: "21:00", units: 2.5)])
    }

    func testChangingNameEditsSamePlan() throws {
        let pet = try makePet(diabetes: true)
        var setup = InsulinSetup(pet: pet, context: context)
        try setup.save(in: context, now: now) // 안 바뀌면 그대로
        XCTAssertEqual(try all(InsulinPlan.self).first?.updatedAt, created)
        setup.name = " 란투스 "
        setup.syringe = .u100
        setup.leadMinutes = 30
        setup.ringDuringQuiet = false
        try setup.save(in: context, now: now)
        let plans = try all(InsulinPlan.self)
        XCTAssertEqual(plans.count, 1)
        XCTAssertEqual(plans[0].name, "란투스")
        XCTAssertEqual(plans[0].syringe, .u100)
        XCTAssertEqual(plans[0].leadMinutes, 30)
        XCTAssertFalse(plans[0].ringDuringQuiet)
        XCTAssertEqual(plans[0].updatedAt, now)
        XCTAssertNil(plans[0].activeUntil)
    }

    func testTwoOpenPlansBothCloseOnChange() throws {
        let pet = try makePet(diabetes: true)
        context.insert(InsulinPlan(now: created, petId: pet.id, slots: [InsulinSlot(time: "09:00", units: 1)],
                                   activeFrom: created.addingTimeInterval(60)))
        var setup = InsulinSetup(pet: pet, context: context)
        XCTAssertEqual(setup.slots.map(\.time), ["09:00"]) // activeFrom이 늦은 것
        setup.slots[0].units = "1.5"
        try setup.save(in: context, now: now)
        let plans = try all(InsulinPlan.self)
        XCTAssertEqual(plans.filter { $0.activeUntil == nil }.count, 1)
        XCTAssertEqual(plans.filter { $0.activeUntil == now }.count, 2)
    }

    /// 열린 계획이 둘이고 이름만 바꾸면 고른 계획(늦은 activeFrom)만 고치고 둘 다 열린 채(데이터 7장).
    func testTwoOpenPlansNameOnlyEditsChosen() throws {
        let pet = try makePet(diabetes: true)
        context.insert(InsulinPlan(now: created, petId: pet.id, slots: [InsulinSlot(time: "09:00", units: 1)],
                                   activeFrom: created.addingTimeInterval(60)))
        var setup = InsulinSetup(pet: pet, context: context)
        setup.name = "란투스"
        try setup.save(in: context, now: now)
        let plans = try all(InsulinPlan.self)
        XCTAssertEqual(plans.filter { $0.activeUntil == nil }.count, 2)
        XCTAssertEqual(plans.filter { $0.name == "란투스" }.map(\.slots), [[InsulinSlot(time: "09:00", units: 1)]])
    }

    /// 저장된 양을 그대로 보여 줘서, 안 고치고 저장하면 새 계획이 안 생긴다.
    func testInsulinUnitsLoadExactly() throws {
        let pet = try makePet()
        context.insert(InsulinPlan(now: created, petId: pet.id,
                                   slots: [InsulinSlot(time: "08:00", units: 2), InsulinSlot(time: "20:00", units: 1.234567)],
                                   activeFrom: created))
        let setup = InsulinSetup(pet: pet, context: context)
        XCTAssertEqual(setup.slots.map(\.units), ["2", "1.234567"])
        try setup.save(in: context, now: now)
        XCTAssertEqual(try all(InsulinPlan.self).count, 1)
    }

    /// 이미 고른 종을 다시 골라도 품종·털색은 그대로.
    func testReselectingSameSpeciesKeepsBreed() throws {
        var form = editing(try makePet())
        form.breed = "페르시안"
        form.furColor = "고등어"
        form.species = .cat
        XCTAssertEqual(form.breed, "페르시안")
        XCTAssertEqual(form.furColor, "고등어")
    }

    func testInsulinSetupMakesPetDiabeticAndSetsNotify() throws {
        let pet = try makePet()
        var setup = InsulinSetup(pet: pet, context: context)
        XCTAssertTrue(setup.notify)
        setup.slots = [.init(time: "08:00", units: "")]
        XCTAssertThrowsError(try setup.save(in: context, now: now))
        XCTAssertFalse(pet.hasDiabetes)
        XCTAssertEqual(try all(InsulinPlan.self).count, 0)

        setup.slots[0].units = "3"
        setup.notify = false
        try setup.save(in: context, now: now)
        XCTAssertTrue(pet.hasDiabetes)
        XCTAssertEqual(pet.updatedAt, now)
        XCTAssertEqual(try all(InsulinPlan.self).count, 1)
        XCTAssertFalse(try Setting.loadOrCreate(in: context).notify.insulinTime)
        // 이제 당뇨를 켠 채 아이 정보를 저장할 수 있다.
        var form = editing(pet)
        XCTAssertTrue(form.hasDiabetes)
        form.name = "보리"
        try form.save(in: context, now: now, calendar: seoul)
    }
}
