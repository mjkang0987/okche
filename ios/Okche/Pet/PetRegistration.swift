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

    enum Field: Hashable {
        case name, birth, age, weight, insulinTimes, save
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
            if let text = trimmed(approxAge), !(Int(text).map { (0...30).contains($0) } ?? false) {
                errors[.age] = "0에서 30 사이로 넣어 주세요"
            }
        } else if let birthDate, calendar.startOfDay(for: birthDate) > calendar.startOfDay(for: today) {
            errors[.birth] = "오늘보다 뒤 날짜는 넣을 수 없어요"
        }
        // 그램으로 반올림해 1 이상이어야 기록이 의미 있다.
        if let text = trimmed(weightKg), !(Self.number(text).map { ($0 * 1000).rounded() >= 1 && $0 <= 100 } ?? false) {
            errors[.weight] = "0보다 크고 100 이하인 숫자로 넣어 주세요"
        }
        if hasDiabetes {
            for slot in insulinSlots {
                guard let text = trimmed(slot.units) else { errors[.insulinUnits(slot.id)] = "양을 넣어 주세요"; continue }
                if !(Self.number(text).map { $0 > 0 && $0 <= 100 } ?? false) {
                    errors[.insulinUnits(slot.id)] = "0보다 크고 100 이하인 숫자로 넣어 주세요"
                }
            }
            if insulinSlots.isEmpty { errors[.insulinTimes] = "시간을 하나 이상 넣어 주세요" }
            else if Set(insulinSlots.map(\.time)).count != insulinSlots.count { errors[.insulinTimes] = "같은 시간이 두 번 있어요" }
        }
        return errors
    }

    /// 아이·체중 기록·인슐린 계획을 한 번에 저장한다. 오류가 있으면 아무것도 저장하지 않는다.
    @discardableResult
    func save(in context: ModelContext, now: Date = .now, calendar: Calendar = .okcheDevice) throws -> Pet {
        let errors = validate(today: now, calendar: calendar)
        guard errors.isEmpty else { throw Invalid(errors: errors) }

        var birth = birthDate.map { Self.day($0, calendar) }
        if birthUnknown {
            birth = trimmed(approxAge).flatMap(Int.init)
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
            let slots = insulinSlots
                .map { InsulinSlot(time: $0.time, units: Self.number(trimmed($0.units)!)!) }
                .sorted { $0.time < $1.time }
            context.insert(InsulinPlan(now: now, petId: pet.id, name: trimmed(insulinName), syringe: syringe, slots: slots,
                                       activeFrom: now))
        }
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
        return pet
    }

    private func trimmed(_ text: String) -> String? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }

    /// 소수점은 점·쉼표 모두 받는다(기기 지역 설정에 따라 숫자 자판이 쉼표를 낼 수 있음).
    /// 유한한 값만(아주 긴 숫자·'inf'는 무한대가 되어 저장할 수 없음).
    static func number(_ text: String) -> Double? {
        Double(text.replacingOccurrences(of: ",", with: ".")).flatMap { $0.isFinite ? $0 : nil }
    }

    /// 기기 달력 날짜 `YYYY-MM-DD`.
    static func day(_ date: Date, _ calendar: Calendar) -> String {
        let c = calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", c.year!, c.month!, c.day!)
    }
}
