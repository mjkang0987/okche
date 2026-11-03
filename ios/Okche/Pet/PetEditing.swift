import Foundation
import SwiftData

/// 아이 정보 고치기의 입력과 저장(plan.md 6-2). 화면과 떼어 두어 테스트한다. 안드로이드 `PetEditSaver`와 같은 규칙.
struct PetEditing {
    let pet: Pet
    var name: String
    /// 종을 바꾸면 품종·털색을 그 종의 첫째로 되돌린다(아이 등록과 같음).
    var species: Species {
        didSet {
            guard species != oldValue else { return }
            breed = PetCatalog.breeds(species)[0]
            furColor = PetCatalog.furColors(species)[0].name
        }
    }
    var breed: String
    var furColor: String
    var sex: Sex?
    var neutered: Bool?
    var birthDate: Date?
    var birthUnknown: Bool
    /// '나이 (대략)'. 어림잡은 생일이면 열 때 오늘 기준 만 나이를 넣어 둔다.
    var approxAge: String
    var familyDate: Date?
    var diagnosis: String
    var hasDiabetes: Bool
    /// 휴양 중 · 관리 쉬기
    var resting: Bool
    /// 열 때 넣어 둔 나이. 이걸 안 고치면 어림잡은 생일을 그대로 둔다(저장할 때마다 생일이 밀리지 않게).
    private let loadedAge: String

    init(pet: Pet, context: ModelContext, today: Date = .now, calendar: Calendar = .okcheDevice) {
        self.pet = pet
        name = pet.name
        species = pet.species
        breed = pet.breed
        furColor = pet.furColor
        sex = pet.sex
        neutered = pet.neutered
        let birth = pet.birthDate.flatMap { PetRegistration.date($0, calendar) }
        birthDate = birth
        birthUnknown = pet.birthEstimated
        approxAge = pet.birthEstimated
            ? birth.flatMap { calendar.dateComponents([.year], from: $0, to: today).year }.map(String.init) ?? ""
            : ""
        loadedAge = approxAge
        familyDate = pet.familyDate.flatMap { PetRegistration.date($0, calendar) }
        diagnosis = pet.diagnosis ?? ""
        hasDiabetes = pet.hasDiabetes
        resting = !((try? RestPeriod.open(for: pet.id, in: context)) ?? []).isEmpty
    }

    typealias Field = PetRegistration.Field

    /// 어림잡은 생일이고 나이를 안 고침 → 저장된 생일을 그대로 둔다.
    private var keepsEstimatedBirth: Bool {
        pet.birthEstimated && PetRegistration.trimmed(approxAge) == PetRegistration.trimmed(loadedAge)
    }

    /// 칸마다 오류 글(저장소를 봐야 하는 '인슐린 계획 없음'은 `save`에서).
    func validate(today: Date = .now, calendar: Calendar = .okcheDevice) -> [Field: String] {
        var errors: [Field: String] = [:]
        if PetRegistration.trimmed(name) == nil { errors[.name] = "이름을 넣어 주세요" }
        if birthUnknown {
            // 안 고친 어림 나이는 생일을 그대로 두므로 검사하지 않는다(30살을 넘겨도 저장할 수 있게, 안드로이드와 같음).
            if !keepsEstimatedBirth { errors[.age] = PetRegistration.ageError(approxAge) }
        } else {
            errors[.birth] = PetRegistration.futureDayError(birthDate, today: today, calendar: calendar)
        }
        errors[.family] = PetRegistration.futureDayError(familyDate, today: today, calendar: calendar)
        return errors
    }

    /// 아이 칸·인슐린 계획 닫기·휴양 기간을 한 번에 저장한다. 오류가 있으면 아무것도 바꾸지 않는다.
    func save(in context: ModelContext, now: Date = .now, calendar: Calendar = .okcheDevice) throws {
        var errors = validate(today: now, calendar: calendar)
        let plans = try InsulinPlan.open(for: pet.id, in: context)
        if hasDiabetes && plans.isEmpty { errors[.insulinPlan] = "인슐린 시간과 양을 먼저 설정해 주세요" }
        guard errors.isEmpty else { throw PetRegistration.Invalid(errors: errors) }

        var birth = birthDate.map { PetRegistration.day($0, calendar) }
        var estimated = false
        if birthUnknown {
            if keepsEstimatedBirth {
                birth = pet.birthDate
            } else {
                birth = PetRegistration.trimmed(approxAge).flatMap(PetRegistration.wholeNumber)
                    .flatMap { calendar.date(byAdding: .year, value: -$0, to: now) }
                    .map { PetRegistration.day($0, calendar) }
            }
            // 나이를 비워 날짜가 없으면 어림잡음 표시도 하지 않는다(아이 등록과 같음).
            estimated = birth != nil
        }

        var changed = false
        func set<T: Equatable>(_ keyPath: ReferenceWritableKeyPath<Pet, T>, _ value: T) {
            guard pet[keyPath: keyPath] != value else { return }
            pet[keyPath: keyPath] = value
            changed = true
        }
        set(\.name, PetRegistration.trimmed(name)!)
        set(\.species, species)
        set(\.breed, breed)
        set(\.furColor, furColor)
        set(\.sex, sex)
        set(\.neutered, neutered)
        set(\.birthDate, birth)
        set(\.birthEstimated, estimated)
        set(\.familyDate, familyDate.map { PetRegistration.day($0, calendar) })
        set(\.diagnosis, PetRegistration.trimmed(diagnosis))
        set(\.hasDiabetes, hasDiabetes)
        if changed { pet.updatedAt = now }

        // 당뇨를 끄면 인슐린 계획을 끝낸다(기록은 그대로).
        if !hasDiabetes {
            for plan in plans {
                plan.activeUntil = now
                plan.updatedAt = now
            }
        }
        // 휴양: 켤 때는 열린 기간이 없을 때만 새로, 끌 때는 열린 기간을 모두 닫는다(데이터 2-1).
        let rests = try RestPeriod.open(for: pet.id, in: context)
        if resting && rests.isEmpty {
            context.insert(RestPeriod(now: now, petId: pet.id, startedAt: now))
        } else if !resting {
            for rest in rests {
                rest.endedAt = now
                rest.updatedAt = now
            }
        }
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }

    /// 동행 N일: 가족 된 날을 1일로 센다(기기 달력, 2026-10-31 사용자 확정 — 데이터 2장).
    static func companionDays(_ familyDate: String?, today: Date = .now, calendar: Calendar = .okcheDevice) -> Int? {
        guard let start = familyDate.flatMap({ PetRegistration.date($0, calendar) }),
              let days = calendar.dateComponents([.day], from: start, to: calendar.startOfDay(for: today)).day,
              days >= 0 else { return nil }
        return days + 1
    }
}

/// 인슐린 설정의 입력과 저장(plan.md 6-2). 안드로이드 `InsulinSetupSaver`와 같은 규칙.
struct InsulinSetup {
    let pet: Pet
    var name: String
    var syringe: Syringe
    var slots: [PetRegistration.InsulinSlotInput]
    /// 인슐린 시간 알림 — 설정 `notify.insulinTime`(모든 아이 공통)
    var notify: Bool
    /// 밤에도 울리기
    var ringDuringQuiet: Bool
    var leadMinutes: Int

    static let leadChoices: [(minutes: Int, title: String)] =
        [(0, "맞는 시간에"), (5, "5분 전"), (10, "10분 전"), (15, "15분 전"), (30, "30분 전"), (60, "1시간 전")]

    /// 열린 계획이 있으면 그 값, 없으면 아이 등록과 같은 처음 값(밤에도 울리기는 디자인 처음 값 켜짐).
    init(pet: Pet, context: ModelContext) {
        self.pet = pet
        let plan = (try? InsulinPlan.open(for: pet.id, in: context))?.first
        name = plan?.name ?? ""
        syringe = plan?.syringe ?? .unknown
        slots = plan.map { $0.slots.map { .init(time: $0.time, units: String(format: "%g", $0.units)) } }
            ?? [.init(time: "08:00"), .init(time: "20:00")]
        ringDuringQuiet = plan?.ringDuringQuiet ?? true
        leadMinutes = plan?.leadMinutes ?? 10
        // 설정 줄은 앱 시작 때 만들어 둔다(`OkcheApp`). 여기서는 읽기만.
        let id = UUID.setting
        notify = (try? context.fetch(FetchDescriptor<Setting>(predicate: #Predicate { $0.id == id })).first)?.notify.insulinTime ?? true
    }

    /// 계획 저장: 시간·양이 바뀌면 열린 줄을 닫고 새 줄, 나머지만 바뀌면 그 줄을 고침(데이터 7장·5장).
    /// 인슐린을 설정했으면 당뇨 아이로 둔다.
    func save(in context: ModelContext, now: Date = .now) throws {
        let errors = PetRegistration.insulinErrors(slots)
        guard errors.isEmpty else { throw PetRegistration.Invalid(errors: errors) }

        let plans = try InsulinPlan.open(for: pet.id, in: context)
        let newSlots = PetRegistration.slotsToSave(slots)
        let name = PetRegistration.trimmed(self.name)
        if let current = plans.first, current.slots.sorted(by: { $0.time < $1.time }) == newSlots {
            if current.name != name || current.syringe != syringe || current.ringDuringQuiet != ringDuringQuiet
                || current.leadMinutes != leadMinutes {
                current.name = name
                current.syringe = syringe
                current.ringDuringQuiet = ringDuringQuiet
                current.leadMinutes = leadMinutes
                current.updatedAt = now
            }
        } else {
            for plan in plans {
                plan.activeUntil = now
                plan.updatedAt = now
            }
            context.insert(InsulinPlan(now: now, petId: pet.id, name: name, syringe: syringe, slots: newSlots,
                                       ringDuringQuiet: ringDuringQuiet, leadMinutes: leadMinutes, activeFrom: now))
        }
        if !pet.hasDiabetes {
            pet.hasDiabetes = true
            pet.updatedAt = now
        }
        let setting = try Setting.loadOrCreate(in: context)
        if setting.notify.insulinTime != notify {
            setting.notify.insulinTime = notify
            setting.updatedAt = now
        }
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }
}

extension InsulinPlan {
    /// 그 아이의 열린 계획(`activeUntil` 없음). 첫째가 쓰는 계획: `activeFrom`이 늦은 것, 같으면 `updatedAt`이 늦은 것(데이터 7장).
    static func open(for petId: UUID, in context: ModelContext) throws -> [InsulinPlan] {
        try context.fetch(FetchDescriptor<InsulinPlan>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == nil
        }))
        .sorted { ($0.activeFrom, $0.updatedAt) > ($1.activeFrom, $1.updatedAt) }
    }
}

extension RestPeriod {
    /// 그 아이의 열린 휴양 기간(`endedAt` 없음). 하나라도 있으면 휴양 중.
    static func open(for petId: UUID, in context: ModelContext) throws -> [RestPeriod] {
        try context.fetch(FetchDescriptor<RestPeriod>(predicate: #Predicate {
            $0.petId == petId && $0.deletedAt == nil && $0.endedAt == nil
        }))
    }
}
