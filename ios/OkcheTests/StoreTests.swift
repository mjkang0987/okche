import SwiftData
import XCTest
@testable import Okche

final class StoreTests: XCTestCase {
    // MARK: 고정 ID (1-5)

    /// 안드로이드와 같은 값이어야 백업을 합쳐도 한 줄. 기대값은 Java `UUID.nameUUIDFromBytes(이름.toByteArray(UTF_8))` 출력.
    func testFixedIDMatchesJavaNameUUIDFromBytes() {
        XCTAssertEqual(UUID.setting.uuidString.lowercased(), "c0236f43-0deb-3ae8-a64f-633d62651603")
        XCTAssertEqual(UUID.feederRecord(slotKey: "abc|2026-10-28|08:00").uuidString.lowercased(),
                       "92c60769-4d57-30ba-9ba1-6cf8ccd959d9")
        XCTAssertEqual(UUID(fixedName: "옥체").uuidString.lowercased(), "075f430b-623d-35eb-97d9-51fe7a58e9df")
    }

    // MARK: 하루 기준 6시 (1-2)

    private func date(_ tz: String, _ y: Int, _ mo: Int, _ d: Int, _ h: Int, _ mi: Int = 0, later: Bool = false) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: tz)!
        let date = calendar.date(from: DateComponents(year: y, month: mo, day: d, hour: h, minute: mi))!
        // 서머타임이 끝나 같은 벽시계 시각이 두 번 오는 날, 두 번째 시각.
        return later ? date.addingTimeInterval(3600) : date
    }

    func testDayStartsAtSixLocal() {
        let seoul = TimeZone(identifier: "Asia/Seoul")!
        XCTAssertEqual(OkcheDay.of(date("Asia/Seoul", 2026, 10, 28, 5, 59), in: seoul), "2026-10-27")
        XCTAssertEqual(OkcheDay.of(date("Asia/Seoul", 2026, 10, 28, 6, 0), in: seoul), "2026-10-28")
        XCTAssertEqual(OkcheDay.of(date("Asia/Seoul", 2026, 10, 28, 0, 0), in: seoul), "2026-10-27")
        XCTAssertEqual(OkcheDay.of(date("Asia/Seoul", 2026, 10, 28, 23, 59), in: seoul), "2026-10-28")
        // 달·해 넘김
        XCTAssertEqual(OkcheDay.of(date("Asia/Seoul", 2027, 1, 1, 2, 0), in: seoul), "2026-12-31")
    }

    /// 같은 순간도 기기 시간대에 따라 날짜가 다르다(현지 시각 기준).
    func testDayFollowsTimeZone() {
        let instant = date("Asia/Seoul", 2026, 10, 28, 10, 0) // UTC 01:00
        XCTAssertEqual(OkcheDay.of(instant, in: TimeZone(identifier: "Asia/Seoul")!), "2026-10-28")
        XCTAssertEqual(OkcheDay.of(instant, in: TimeZone(identifier: "UTC")!), "2026-10-27")
    }

    /// 서머타임 날에도 시간을 빼지 않고 벽시계 6시로 나눈다.
    func testDayOnDaylightSavingDays() {
        let ny = TimeZone(identifier: "America/New_York")!
        // 2026-03-08 02:00 → 03:00 (23시간짜리 날)
        XCTAssertEqual(OkcheDay.of(date("America/New_York", 2026, 3, 8, 3, 30), in: ny), "2026-03-07")
        XCTAssertEqual(OkcheDay.of(date("America/New_York", 2026, 3, 8, 6, 0), in: ny), "2026-03-08")
        // 전날 같은 시각(02:30)이 없는 날의 새벽
        XCTAssertEqual(OkcheDay.of(date("America/New_York", 2026, 3, 9, 2, 30), in: ny), "2026-03-08")
        // 2026-11-01 02:00 → 01:00 (01:30이 두 번)
        XCTAssertEqual(OkcheDay.of(date("America/New_York", 2026, 11, 1, 1, 30, later: true), in: ny), "2026-10-31")
        XCTAssertEqual(OkcheDay.of(date("America/New_York", 2026, 11, 1, 5, 59), in: ny), "2026-10-31")
        XCTAssertEqual(OkcheDay.of(date("America/New_York", 2026, 11, 1, 6, 0), in: ny), "2026-11-01")
    }

    func testRecordDayIsSetOnCreateAndTimeChange() {
        let seoul = TimeZone(identifier: "Asia/Seoul")!
        let created = Date(timeIntervalSince1970: 0)
        let record = Record(now: created, petId: nil, kind: .vomit, occurredAt: date("Asia/Seoul", 2026, 10, 28, 2, 0), timeZone: seoul)
        XCTAssertEqual(record.day, "2026-10-27")
        XCTAssertEqual(record.kind, "vomit")

        // 시간 바꾸기: 그때 기기 시간대로 다시 정하고 고친 때를 바꾼다.
        let edited = Date(timeIntervalSince1970: 100)
        record.setOccurredAt(date("Asia/Seoul", 2026, 10, 28, 7, 0), timeZone: seoul, now: edited)
        XCTAssertEqual(record.day, "2026-10-28")
        XCTAssertEqual(record.updatedAt, edited)
        XCTAssertEqual(record.createdAt, created)
    }

    // MARK: 저장소

    @MainActor
    func testAllModelsRoundTrip() throws {
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        let now = date("Asia/Seoul", 2026, 10, 28, 9, 0)

        let pet = Pet(now: now, name: "콩이", species: .cat, birthDate: "2015-04-01", hasDiabetes: true)
        let med = Medication(now: now, name: "프레드니솔론", category: .medicine, form: .pill, doseAmount: 0.5, unit: .pill)
        let schedule = MedSchedule(now: now, petId: pet.id, medicationId: med.id, amount: 0.5, unit: .pill,
                                   frequency: .weekdays, times: ["08:00", "20:00"], weekdays: [1, 3, 5], activeFrom: now)
        let insulin = InsulinPlan(now: now, petId: pet.id, syringe: .u40,
                                  slots: [InsulinSlot(time: "08:00", units: 2), InsulinSlot(time: "20:00", units: 1.5)], activeFrom: now)
        let food = Food(now: now, name: "처방 사료")
        let meal = MealPlan(now: now, petId: pet.id, mode: .feeder, slots: [MealSlot(name: "아침", time: "08:00")], activeFrom: now)
        let photo = Photo(now: now, takenAt: now, localDate: "2026-10-28", kind: .body, bodyView: .top, bodyScore: 3, fileExtension: "heic")
        let models: [any PersistentModel] = [
            pet, RestPeriod(now: now, petId: pet.id, startedAt: now),
            Record(now: now, petId: pet.id, kind: .glucose, occurredAt: now, data: #"{"mgdl":180}"#),
            med, schedule, insulin, food,
            FoodChange(now: now, petId: pet.id, foodId: food.id, startedOn: "2026-10-28", method: .gradual),
            Snack(now: now, name: "츄르", kind: .lickable), meal, photo,
            PhotoPet(now: now, photoId: photo.id, petId: pet.id), Setting(now: now),
        ]
        models.forEach { context.insert($0) }
        try context.save()

        // 새 문맥에서 읽어 실제로 저장된 값인지 본다.
        let fresh = ModelContext(container)
        for type in OkcheStore.models {
            XCTAssertEqual(try count(type, in: fresh), 1, "\(type)")
        }
        let readPet = try XCTUnwrap(fresh.fetch(FetchDescriptor<Pet>()).first)
        XCTAssertEqual(readPet.species, .cat)
        XCTAssertEqual(readPet.createdAt, now)
        XCTAssertNil(readPet.deletedAt)
        let readSchedule = try XCTUnwrap(fresh.fetch(FetchDescriptor<MedSchedule>()).first)
        XCTAssertEqual(readSchedule.seriesId, readSchedule.id)
        XCTAssertEqual(readSchedule.times, ["08:00", "20:00"])
        XCTAssertEqual(readSchedule.weekdays, [1, 3, 5])
        XCTAssertEqual(readSchedule.frequency, .weekdays)
        XCTAssertTrue(readSchedule.shiftOnLate)
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<InsulinPlan>()).first?.slots.map(\.units), [2, 1.5])
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<MealPlan>()).first?.slots, [MealSlot(name: "아침", time: "08:00")])
        let readPhoto = try XCTUnwrap(fresh.fetch(FetchDescriptor<Photo>()).first)
        XCTAssertEqual(readPhoto.file, "\(photo.id.uuidString.lowercased()).heic")
        XCTAssertEqual(readPhoto.bodyView, .top)
        let setting = try XCTUnwrap(fresh.fetch(FetchDescriptor<Setting>()).first)
        XCTAssertEqual(setting.id, .setting)
        XCTAssertEqual(setting.weightReminder, WeightReminder(weekday: 7, time: "10:00"))
        XCTAssertEqual(setting.lowMedDays, 3)
    }

    /// 실록 집계의 기본 검색(아이 + 종류 + 날짜)이 저장소에서 된다.
    @MainActor
    func testFetchRecordsByPetKindAndDay() throws {
        // 컨테이너를 변수로 붙잡아 둬야 한다 — 바로 버리면 문맥이 해제된 저장소를 써서 죽는다.
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        let seoul = TimeZone(identifier: "Asia/Seoul")!
        let pet = UUID()
        context.insert(Record(petId: pet, kind: .vomit, occurredAt: date("Asia/Seoul", 2026, 10, 28, 2, 0), timeZone: seoul))
        context.insert(Record(petId: pet, kind: .vomit, occurredAt: date("Asia/Seoul", 2026, 10, 28, 7, 0), timeZone: seoul))
        context.insert(Record(petId: pet, kind: .stool, occurredAt: date("Asia/Seoul", 2026, 10, 28, 7, 0), timeZone: seoul))
        context.insert(Record(petId: nil, kind: .vomit, occurredAt: date("Asia/Seoul", 2026, 10, 28, 7, 0), timeZone: seoul))
        try context.save()

        let kind = RecordKind.vomit.rawValue
        let day = "2026-10-28"
        let found = try context.fetch(FetchDescriptor<Record>(predicate: #Predicate {
            $0.petId == pet && $0.kind == kind && $0.day == day && $0.deletedAt == nil
        }))
        XCTAssertEqual(found.count, 1)
    }

    /// 같은 ID를 두 번 넣으면(두 기기가 각자 만든 설정) 한 줄로 합쳐진다.
    @MainActor
    func testSameIDIsOneRow() throws {
        // 컨테이너를 변수로 붙잡아 둬야 한다 — 바로 버리면 문맥이 해제된 저장소를 써서 죽는다.
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        context.insert(Setting())
        try context.save()
        context.insert(Setting(lowMedDays: 5))
        try context.save()
        let settings = try context.fetch(FetchDescriptor<Setting>())
        XCTAssertEqual(settings.count, 1)
        XCTAssertEqual(settings.first?.lowMedDays, 5)
    }

    private func count(_ type: any PersistentModel.Type, in context: ModelContext) throws -> Int {
        func count<T: PersistentModel>(_: T.Type) throws -> Int { try context.fetchCount(FetchDescriptor<T>()) }
        return try count(type)
    }
}
