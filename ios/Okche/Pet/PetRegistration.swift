import Foundation
import SwiftData

/// 아이 등록 화면의 입력과 저장(plan.md 6-1). 화면과 떼어 두어 테스트한다. 안드로이드와 같은 규칙.
struct PetRegistration {
    var name = ""
    /// 종을 바꾸면 품종·털색을 그 종의 첫째로 되돌린다.
    var species: Species = .cat {
        didSet {
            guard species != oldValue else { return }
            breed = PetCatalog.breeds(species)[0]
            furColor = PetCatalog.furColors(species)[0].name
        }
    }
    var breed = PetCatalog.breeds(.cat)[0]
    var furColor = PetCatalog.furColors(.cat)[0].name
    var sex: Sex?
    var neutered: Bool?
    var birthDate: Date?
    var birthUnknown = false
    /// '나이 (대략) ○살쯤'
    var approxAge = ""
    var weightKg = ""
    var diagnosis = ""
    var hasDiabetes = false
    var insulinName = ""
    var insulinSlots = [InsulinSlotInput(time: "08:00"), InsulinSlotInput(time: "20:00")]
    var syringe: Syringe = .unknown

    struct InsulinSlotInput: Identifiable, Equatable {
        let id = UUID()
        /// `HH:mm`
        var time: String
        var units = ""
    }

    /// 칸 이름. 아이 정보 고치기(`PetEditing`)·인슐린 설정(`InsulinSetup`)도 같이 쓴다.
    enum Field: Hashable {
        case name, birth, age, weight, insulinTimes, save
        /// 가족 된 날, 당뇨를 켰는데 인슐린 계획이 없음 (6-2)
        case family, insulinPlan
        /// 오류는 없고 키보드 초점만 쓰는 칸
        case diagnosis, insulinName
        case insulinUnits(UUID)
    }

    struct Invalid: Error {
        let errors: [Field: String]
    }

    /// 칸마다 오류 글. 비어 있으면 저장할 수 있다.
    func validate(today: Date = .now, calendar: Calendar = .okcheDevice) -> [Field: String] {
        var errors: [Field: String] = [:]
        if trimmed(name) == nil { errors[.name] = "이름을 넣어 주세요" }
        if birthUnknown {
            errors[.age] = Self.ageError(approxAge)
        } else {
            errors[.birth] = Self.futureDayError(birthDate, today: today, calendar: calendar)
        }
        // 그램으로 반올림해 1 이상이어야 기록이 의미 있다.
        if let text = trimmed(weightKg), !(Self.number(text).map { ($0 * 1000).rounded() >= 1 && $0 <= 100 } ?? false) {
            errors[.weight] = "0보다 크고 100 이하인 숫자로 넣어 주세요"
        }
        if hasDiabetes { errors.merge(Self.insulinErrors(insulinSlots)) { a, _ in a } }
        return errors
    }

    /// 아이·체중 기록·인슐린 계획을 한 번에 저장한다. 오류가 있으면 아무것도 저장하지 않는다.
    @discardableResult
    func save(in context: ModelContext, now: Date = .now, calendar: Calendar = .okcheDevice) throws -> Pet {
        let errors = validate(today: now, calendar: calendar)
        guard errors.isEmpty else { throw Invalid(errors: errors) }

        var birth = birthDate.map { Self.day($0, calendar) }
        if birthUnknown {
            birth = trimmed(approxAge).flatMap(Self.wholeNumber)
                .flatMap { calendar.date(byAdding: .year, value: -$0, to: now) }
                .map { Self.day($0, calendar) }
        }
        // 나이를 비워 날짜가 없으면 어림잡음 표시도 하지 않는다.
        let pet = Pet(now: now, name: trimmed(name)!, species: species, breed: breed, furColor: furColor, sex: sex,
                      neutered: neutered, birthDate: birth, birthEstimated: birthUnknown && birth != nil, diagnosis: trimmed(diagnosis),
                      hasDiabetes: hasDiabetes)
        context.insert(pet)
        if let kg = trimmed(weightKg).flatMap(Self.number) {
            let grams = Int((kg * 1000).rounded())
            context.insert(Record(now: now, petId: pet.id, kind: .weight, occurredAt: now, timeZone: calendar.timeZone,
                                  data: #"{"grams":\#(grams)}"#))
        }
        if hasDiabetes {
            let slots = Self.slotsToSave(insulinSlots)
            context.insert(InsulinPlan(now: now, petId: pet.id, name: trimmed(insulinName), syringe: syringe, slots: slots,
                                       ringDuringQuiet: true, activeFrom: now))
        }
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
        return pet
    }

    private func trimmed(_ text: String) -> String? { Self.trimmed(text) }

    static func trimmed(_ text: String) -> String? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }

    /// '나이 (대략)': 비우면 괜찮고, 넣으면 0~30 정수.
    static func ageError(_ text: String) -> String? {
        guard let text = trimmed(text) else { return nil }
        return (wholeNumber(text).map { (0...30).contains($0) } ?? false) ? nil : "0에서 30 사이로 넣어 주세요"
    }

    /// 생일·가족 된 날: 오늘(기기 달력) 뒤면 안 됨.
    static func futureDayError(_ date: Date?, today: Date, calendar: Calendar) -> String? {
        guard let date, calendar.startOfDay(for: date) > calendar.startOfDay(for: today) else { return nil }
        return "오늘보다 뒤 날짜는 넣을 수 없어요"
    }

    /// 인슐린 시간과 양: 시간 1개 이상·겹침 없음, 양은 0 초과 100 이하.
    static func insulinErrors(_ slots: [InsulinSlotInput]) -> [Field: String] {
        var errors: [Field: String] = [:]
        for slot in slots {
            guard let text = trimmed(slot.units) else { errors[.insulinUnits(slot.id)] = "양을 넣어 주세요"; continue }
            if !(number(text).map { $0 > 0 && $0 <= 100 } ?? false) {
                errors[.insulinUnits(slot.id)] = "0보다 크고 100 이하인 숫자로 넣어 주세요"
            }
        }
        if slots.isEmpty { errors[.insulinTimes] = "시간을 하나 이상 넣어 주세요" }
        else if Set(slots.map(\.time)).count != slots.count { errors[.insulinTimes] = "같은 시간이 두 번 있어요" }
        return errors
    }

    /// 칸 글자 → 저장할 슬롯(시간 순). 검사를 통과한 뒤에만 부른다.
    static func slotsToSave(_ slots: [InsulinSlotInput]) -> [InsulinSlot] {
        slots.map { InsulinSlot(time: $0.time, units: number(trimmed($0.units)!)!) }.sorted { $0.time < $1.time }
    }

    /// 소수점은 점·쉼표 모두 받는다(기기 지역 설정에 따라 숫자 자판이 쉼표를 낼 수 있음).
    /// ASCII 숫자와 소수점(점·쉼표) 하나만 — 안드로이드와 같은 규칙('+3'·'1e1'·전각 숫자는 숫자가 아님).
    /// 유한한 값만(아주 긴 숫자는 무한대가 되어 저장할 수 없음).
    static func number(_ text: String) -> Double? {
        guard text.wholeMatch(of: /[0-9]+[.,]?[0-9]*|[.,][0-9]+/) != nil else { return nil }
        return Double(text.replacingOccurrences(of: ",", with: ".")).flatMap { $0.isFinite ? $0 : nil }
    }

    /// 나이: ASCII 숫자만.
    static func wholeNumber(_ text: String) -> Int? {
        text.wholeMatch(of: /[0-9]+/) != nil ? Int(text) : nil
    }

    /// `YYYY-MM-DD` → 기기 달력의 그날 0시.
    static func date(_ day: String, _ calendar: Calendar) -> Date? {
        let parts = day.split(separator: "-").compactMap { Int($0) }
        guard parts.count == 3 else { return nil }
        return calendar.date(from: DateComponents(year: parts[0], month: parts[1], day: parts[2]))
    }

    /// 기기 달력 날짜 `YYYY-MM-DD`.
    static func day(_ date: Date, _ calendar: Calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}
