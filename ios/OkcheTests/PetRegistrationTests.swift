import SwiftData
import XCTest
@testable import Okche

/// 아이 등록 저장 규칙(plan.md 6-1). 안드로이드 테스트와 같은 경우.
@MainActor
final class PetRegistrationTests: XCTestCase {
    private var container: ModelContainer!
    private var context: ModelContext { container.mainContext }
    private let seoul: Calendar = {
        var c = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return c
    }()
    private var now: Date { seoul.date(from: DateComponents(year: 2026, month: 10, day: 28, hour: 9))! }

    override func setUp() async throws {
        container = try OkcheStore.container(inMemory: true)
    }

    private func count<T: PersistentModel>(_: T.Type) throws -> Int { try context.fetchCount(FetchDescriptor<T>()) }

    func testBlankNameSavesNothing() throws {
        var form = PetRegistration()
        form.name = "   "
        form.weightKg = "4.6"
        XCTAssertThrowsError(try form.save(in: context, now: now, calendar: seoul)) { error in
            XCTAssertEqual((error as? PetRegistration.Invalid)?.errors[.name], "이름을 넣어 주세요")
        }
        XCTAssertEqual(try count(Pet.self), 0)
        XCTAssertEqual(try count(Record.self), 0)
    }

    func testSavesPetWithTrimmedFields() throws {
        var form = PetRegistration()
        form.name = " 콩이 "
        form.species = .dog
        form.sex = .female
        form.neutered = true
        form.birthDate = seoul.date(from: DateComponents(year: 2019, month: 10, day: 25, hour: 23, minute: 30))
        form.diagnosis = "  "
        let pet = try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.name, "콩이")
        XCTAssertEqual(pet.breed, "말티즈") // 종을 바꾸면 그 종의 첫째
        XCTAssertEqual(pet.furColor, "흰색")
        XCTAssertEqual(pet.sex, .female)
        XCTAssertEqual(pet.neutered, true)
        XCTAssertEqual(pet.birthDate, "2019-10-25") // 기기 달력 날짜(6시 규칙 아님)
        XCTAssertFalse(pet.birthEstimated)
        XCTAssertNil(pet.diagnosis)
        XCTAssertFalse(pet.hasDiabetes)
        XCTAssertEqual(try count(Record.self), 0)
        XCTAssertEqual(try count(InsulinPlan.self), 0)
    }

    func testApproxAgeBecomesBirthDate() throws {
        var form = PetRegistration()
        form.name = "보리"
        form.birthDate = seoul.date(from: DateComponents(year: 2020, month: 1, day: 1)) // 몰라요를 켜면 무시
        form.birthUnknown = true
        form.approxAge = "3"
        let pet = try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(pet.birthDate, "2023-10-28")
        XCTAssertTrue(pet.birthEstimated)
    }

    func testApproxAgeLeapDay() throws {
        var form = PetRegistration()
        form.name = "보리"
        form.birthUnknown = true
        form.approxAge = "1"
        let leap = seoul.date(from: DateComponents(year: 2028, month: 2, day: 29, hour: 9))!
        XCTAssertEqual(try form.save(in: context, now: leap, calendar: seoul).birthDate, "2027-02-28")
    }

    func testFieldErrors() {
        var form = PetRegistration()
        form.name = "콩이"
        form.birthDate = seoul.date(byAdding: .day, value: 1, to: now)
        form.weightKg = "0"
        XCTAssertEqual(form.validate(today: now, calendar: seoul),
                       [.birth: "오늘보다 뒤 날짜는 넣을 수 없어요", .weight: "0보다 크고 100 이하인 숫자로 넣어 주세요"])
        form.birthUnknown = true
        form.approxAge = "31"
        form.weightKg = "4,6" // 쉼표 소수점도 받는다
        XCTAssertEqual(form.validate(today: now, calendar: seoul), [.age: "0에서 30 사이로 넣어 주세요"])
    }

    /// 무한대·NaN은 숫자로 보지 않는다(SwiftData가 JSON으로 못 바꿔 멈춤).
    func testNumberRejectsNonFinite() {
        XCTAssertNil(PetRegistration.number("1e400"))
        XCTAssertNil(PetRegistration.number("nan"))
        XCTAssertNil(PetRegistration.number("inf"))
        XCTAssertEqual(PetRegistration.number("4,6"), 4.6)
        // 안드로이드 PetRegisterTest.numbersAreAsciiOnly와 같은 입력
        for text in ["5", "5.", ".5", "5,", "1,5"] { XCTAssertNotNil(PetRegistration.number(text), text) }
        for text in ["+5", "-1", "1e1", "0x1p3", "1.5f", "1.2.3", "５", "٥", " 5", ""] {
            XCTAssertNil(PetRegistration.number(text), text)
        }
        XCTAssertEqual(PetRegistration.wholeNumber("12"), 12)
        for text in ["+3", "-0", "３", "1.0", ""] { XCTAssertNil(PetRegistration.wholeNumber(text), text) }
    }

    func testTinyWeightAndEmptyAge() throws {
        var form = PetRegistration()
        form.name = "콩이"
        form.weightKg = "0.0004" // 0g이 된다
        XCTAssertEqual(form.validate(today: now, calendar: seoul)[.weight], "0보다 크고 100 이하인 숫자로 넣어 주세요")
        form.weightKg = ""
        form.birthUnknown = true // 나이를 비우면 날짜도 어림잡음도 없다
        let pet = try form.save(in: context, now: now, calendar: seoul)
        XCTAssertNil(pet.birthDate)
        XCTAssertFalse(pet.birthEstimated)
    }


    func testWeightBecomesGramsRecord() throws {
        var form = PetRegistration()
        form.name = "콩이"
        form.weightKg = "4.6255"
        let pet = try form.save(in: context, now: now, calendar: seoul)
        let record = try XCTUnwrap(context.fetch(FetchDescriptor<Record>()).first)
        XCTAssertEqual(record.petId, pet.id)
        XCTAssertEqual(record.kind, "weight")
        XCTAssertEqual(record.data, #"{"grams":4626}"#)
        XCTAssertEqual(record.occurredAt, now)
        XCTAssertEqual(record.day, "2026-10-28")
    }

    func testDiabetesSavesInsulinPlanSortedByTime() throws {
        var form = PetRegistration()
        form.name = "콩이"
        form.hasDiabetes = true
        form.insulinName = " 란투스 "
        form.insulinSlots = [.init(time: "20:00", units: "1.5"), .init(time: "08:00", units: "2")]
        form.syringe = .u40
        let pet = try form.save(in: context, now: now, calendar: seoul)
        XCTAssertTrue(pet.hasDiabetes)
        let plan = try XCTUnwrap(context.fetch(FetchDescriptor<InsulinPlan>()).first)
        XCTAssertEqual(plan.petId, pet.id)
        XCTAssertEqual(plan.name, "란투스")
        XCTAssertEqual(plan.syringe, .u40)
        XCTAssertEqual(plan.slots, [InsulinSlot(time: "08:00", units: 2), InsulinSlot(time: "20:00", units: 1.5)])
        XCTAssertEqual(plan.activeFrom, now)
    }

    func testInsulinErrors() throws {
        var form = PetRegistration()
        form.name = "콩이"
        form.hasDiabetes = true
        form.insulinSlots = [.init(time: "08:00", units: "2"), .init(time: "08:00", units: ""),
                             .init(time: "12:00", units: "0"), .init(time: "13:00", units: "1e400")]
        let ids = form.insulinSlots.map(\.id)
        let errors = form.validate(today: now, calendar: seoul)
        XCTAssertEqual(errors[.insulinTimes], "같은 시간이 두 번 있어요")
        XCTAssertNil(errors[.insulinUnits(ids[0])])
        XCTAssertEqual(errors[.insulinUnits(ids[1])], "양을 넣어 주세요")
        XCTAssertEqual(errors[.insulinUnits(ids[2])], "0보다 크고 100 이하인 숫자로 넣어 주세요")
        // 무한대는 숫자가 아니다(저장하면 SwiftData가 멈춤).
        XCTAssertEqual(errors[.insulinUnits(ids[3])], "0보다 크고 100 이하인 숫자로 넣어 주세요")
        XCTAssertThrowsError(try form.save(in: context, now: now, calendar: seoul))
        XCTAssertEqual(try count(Pet.self), 0)
        form.insulinSlots = []
        XCTAssertEqual(form.validate(today: now, calendar: seoul)[.insulinTimes], "시간을 하나 이상 넣어 주세요")
        // 당뇨를 끄면 인슐린 칸은 보지 않고 저장하지 않는다.
        form.hasDiabetes = false
        try form.save(in: context, now: now, calendar: seoul)
        XCTAssertEqual(try count(InsulinPlan.self), 0)
    }
}
