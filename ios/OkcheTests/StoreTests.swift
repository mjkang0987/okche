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

    /// 선택지 저장 값이 문서·안드로이드(`StoreTest.choiceValuesMatchDoc`)와 글자 하나까지 같은지.
    func testChoiceValuesMatchDoc() {
        func names<T: CaseIterable & RawRepresentable>(_: T.Type) -> String where T.RawValue == String {
            T.allCases.map(\.rawValue).joined(separator: " ")
        }
        XCTAssertEqual(names(Species.self), "cat dog")
        XCTAssertEqual(names(RecordKind.self), "vomit stool urine meal snack weight med glucose insulin")
        XCTAssertEqual(names(MedCategory.self), "medicine supplement")
        XCTAssertEqual(names(MedForm.self), "pill powder liquid topical injection")
        XCTAssertEqual(names(DoseUnit.self), "pill ml pack drop")
        XCTAssertEqual(names(MedFrequency.self), "daily everyNDays weekdays everyNHours asNeeded")
        XCTAssertEqual(names(HowTo.self), "plain inFood inTreat syringe")
        XCTAssertEqual(names(Syringe.self), "u40 u100 unknown")
        XCTAssertEqual(names(FoodChangeMethod.self), "gradual atOnce")
        XCTAssertEqual(names(SnackKind.self), "lickable freezeDried jerky wet prescription human")
        XCTAssertEqual(names(MealMode.self), "direct feeder")
        XCTAssertEqual(names(PhotoKind.self), "normal body")
        XCTAssertEqual(names(BodyView.self), "top side")
        XCTAssertEqual(names(CelebrateTone.self), "joseon plain")
    }

    // MARK: 하루 기준 6시 (1-2)

    private func date(_ tz: String, _ y: Int, _ mo: Int, _ d: Int, _ h: Int, _ mi: Int = 0) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: tz)!
        return calendar.date(from: DateComponents(year: y, month: mo, day: d, hour: h, minute: mi))!
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
        // 2026-11-01 02:00 → 01:00 (25시간짜리 날)
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

        let pet = Pet(now: now, name: "콩이", species: .cat, breed: "코리안 숏헤어", furColor: "치즈", birthDate: "2015-04-01", hasDiabetes: true)
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
        XCTAssertEqual(readPet.furColor, "치즈")
        XCTAssertNil(readPet.familyDate)
        XCTAssertEqual(readPet.createdAt, now)
        XCTAssertNil(readPet.deletedAt)
        let readSchedule = try XCTUnwrap(fresh.fetch(FetchDescriptor<MedSchedule>()).first)
        XCTAssertEqual(readSchedule.seriesId, readSchedule.id)
        XCTAssertEqual(readSchedule.times, ["08:00", "20:00"])
        XCTAssertEqual(readSchedule.weekdays, [1, 3, 5])
        XCTAssertEqual(readSchedule.frequency, .weekdays)
        XCTAssertTrue(readSchedule.shiftOnLate)
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<InsulinPlan>()).first?.slots.map(\.units), [2, 1.5])
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<InsulinPlan>()).first?.syringe, .u40)
        let readRecord = try XCTUnwrap(fresh.fetch(FetchDescriptor<Record>()).first)
        XCTAssertEqual(readRecord.day, "2026-10-28")
        XCTAssertEqual(readRecord.data, #"{"mgdl":180}"#)
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<Snack>()).first?.kind, .lickable)
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<FoodChange>()).first?.method, .gradual)
        XCTAssertEqual(try fresh.fetch(FetchDescriptor<MealPlan>()).first?.slots, [MealSlot(name: "아침", time: "08:00")])
        let readPhoto = try XCTUnwrap(fresh.fetch(FetchDescriptor<Photo>()).first)
        XCTAssertEqual(readPhoto.file, "\(photo.id.uuidString.lowercased()).heic")
        XCTAssertEqual(readPhoto.bodyView, .top)
        let setting = try XCTUnwrap(fresh.fetch(FetchDescriptor<Setting>()).first)
        XCTAssertEqual(setting.id, .setting)
        XCTAssertEqual(setting.weightReminder, WeightReminder(weekday: 7, time: "10:00"))
        XCTAssertEqual(setting.lowMedDays, 3)
        XCTAssertFalse(setting.quietHours.enabled)
        XCTAssertTrue(setting.notify.lastYearToday)
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
        let deleted = Record(petId: pet, kind: .vomit, occurredAt: date("Asia/Seoul", 2026, 10, 28, 8, 0), timeZone: seoul)
        deleted.deletedAt = .now
        context.insert(deleted)
        try context.save()

        let kind = RecordKind.vomit.rawValue
        let day = "2026-10-28"
        let found = try context.fetch(FetchDescriptor<Record>(predicate: #Predicate {
            $0.petId == pet && $0.kind == kind && $0.day == day && $0.deletedAt == nil
        }))
        XCTAssertEqual(found.count, 1)
    }

    /// 설정은 있으면 그대로 읽고 없을 때만 만든다 — 고른 값을 처음 값으로 덮어쓰지 않는다.
    @MainActor
    func testSettingLoadOrCreateKeepsExisting() throws {
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        try Setting.loadOrCreate(in: context).lowMedDays = 5
        try context.save()
        // 앱을 다시 켤 때처럼 새 문맥에서 저장된 줄을 찾는다.
        let fresh = ModelContext(container)
        XCTAssertEqual(try Setting.loadOrCreate(in: fresh).lowMedDays, 5)
        try fresh.save()
        XCTAssertEqual(try fresh.fetchCount(FetchDescriptor<Setting>()), 1)
    }

    /// 같은 id를 다시 넣으면 SwiftData가 기존 줄을 덮어쓴다(지운 줄도 되살아남) — 고정 ID 줄을 있는지 먼저 보고 넣어야 하는 이유.
    /// 이 동작이 바뀌면(예: 오류를 냄) 위 규칙을 다시 본다.
    @MainActor
    func testReinsertingSameIDOverwrites() throws {
        let container = try OkcheStore.container(inMemory: true)
        let context = container.mainContext
        let id = UUID.feederRecord(slotKey: "plan|2026-10-28|08:00")
        let first = Record(id: id, petId: nil, kind: .meal, occurredAt: .now, auto: true)
        first.deletedAt = .now
        context.insert(first)
        try context.save()
        context.insert(Record(id: id, petId: nil, kind: .meal, occurredAt: .now, auto: true))
        try context.save()
        let rows = try context.fetch(FetchDescriptor<Record>())
        XCTAssertEqual(rows.count, 1)
        XCTAssertNil(rows.first?.deletedAt)
    }

    private func count(_ type: any PersistentModel.Type, in context: ModelContext) throws -> Int {
        func count<T: PersistentModel>(_: T.Type) throws -> Int { try context.fetchCount(FetchDescriptor<T>()) }
        return try count(type)
    }
}
