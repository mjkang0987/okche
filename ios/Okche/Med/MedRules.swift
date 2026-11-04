import Foundation
import SwiftData

/// 약방 규칙(plan.md 8-1, 데이터 4장). 안드로이드 `MedRules.kt`와 같은 규칙.
enum MedRules {
    static let categories: [(value: MedCategory, title: String)] = [(.medicine, "약"), (.supplement, "보조제")]
    static let forms: [(value: MedForm, title: String)] = [
        (.pill, "알약"), (.powder, "가루"), (.liquid, "물약"), (.topical, "연고·안약"), (.injection, "주사"),
    ]

    static func formTitle(_ form: MedForm) -> String { forms.first { $0.value == form }?.title ?? "" }

    /// 단위는 모양을 따른다(데이터 4장).
    static func unit(_ form: MedForm) -> DoseUnit {
        switch form {
        case .pill: .pill
        case .powder: .pack
        case .liquid, .injection: .ml
        case .topical: .drop
        }
    }

    static func unitTitle(_ unit: DoseUnit) -> String {
        switch unit {
        case .pill: "알"
        case .pack: "포"
        case .ml: "ml"
        case .drop: "방울"
        }
    }

    /// 숫자 글자: 소수 둘째 자리까지, 끝 0은 뺌('1'·'0.5'·'2.25').
    static func number(_ value: Double) -> String {
        var exact = Decimal(value)
        var rounded = Decimal()
        NSDecimalRound(&rounded, &exact, 2, .plain)
        return "\(rounded)"
    }

    private static func json(_ record: Record) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any]) ?? [:]
    }

    /// 지금 남은 양 = 적은 양 − 적은 뒤 그 약의 `given`·`vomited` 기록 양(같은 단위만, 지운 기록 빼고). 안 적었으면 nil.
    static func remaining(_ med: Medication, records: [Record]) -> Double? {
        guard let stock = med.stockAmount else { return nil }
        let id = med.id.uuidString.lowercased(), unit = med.unit.rawValue
        let used = records.filter { record in
            guard record.kind == RecordKind.med.rawValue, record.deletedAt == nil,
                  med.stockCountedAt.map({ record.occurredAt > $0 }) ?? true else { return false }
            let json = json(record)
            return json["medicationId"] as? String == id && json["unit"] as? String == unit
                && ["given", "vomited"].contains(json["status"] as? String ?? "")
        }
        .reduce(0) { $0 + ((json($1)["amount"] as? NSNumber)?.doubleValue ?? 0) }
        return stock - used
    }

    /// 약 줄 아래 글자 '알약 · 1알씩 · 남은 24알 (48일분)' / '… · 남은 양 안 적음'(일분은 먹고 있는 주기가 있을 때 — 8-2).
    static func detail(_ med: Medication, records: [Record], schedules: [MedSchedule] = []) -> String {
        let unit = unitTitle(med.unit)
        let days = MedSchedules.daysLeft(med, schedules: schedules, records: records).map { " (\($0)일분)" } ?? ""
        let left = remaining(med, records: records).map { "남은 \(number(max($0, 0)))\(unit)\(days)" } ?? "남은 양 안 적음"
        return "\(formTitle(med.form)) · \(number(med.doseAmount))\(unit)씩 · \(left)"
    }

    /// 약방 목록: 묶음(약·보조제)마다 들인 순, 뺀 약 빼고. 빈 묶음은 뺀다.
    static func grouped(_ meds: [Medication]) -> [(category: MedCategory, title: String, meds: [Medication])] {
        let listed = meds.filter { $0.deletedAt == nil }.sorted { $0.createdAt < $1.createdAt }
        return categories.compactMap { category in
            let inGroup = listed.filter { $0.category == category.value }
            return inGroup.isEmpty ? nil : (category.value, category.title, inGroup)
        }
    }

    // MARK: 들이기·고치기

    enum Field: Hashable { case name, shortName, dose, stock }

    struct Form: Equatable {
        var name = ""
        var shortName = ""
        var category = MedCategory.medicine
        var form = MedForm.pill
        var dose = "1"
        var stock = ""
        var memo = ""

        init() {}

        /// 고치기: 지금 값(남은 양 칸은 계산한 지금 남은 양).
        init(_ med: Medication, records: [Record]) {
            name = med.name
            shortName = med.shortName ?? ""
            category = med.category
            form = med.form
            dose = MedRules.number(med.doseAmount)
            stock = MedRules.remaining(med, records: records).map { MedRules.number(max($0, 0)) } ?? ""
            memo = med.memo ?? ""
        }
    }

    private static func trimmed(_ text: String) -> String? {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return t.isEmpty ? nil : t
    }

    /// 칸 검사: 이름 필수, 짧은 이름 6자까지, 한 번에 0보다 큰 수, 남은 양 0 이상(선택).
    static func errors(_ form: Form) -> [Field: String] {
        var errors: [Field: String] = [:]
        if trimmed(form.name) == nil { errors[.name] = "약 이름을 넣어 주세요" }
        if (trimmed(form.shortName)?.count ?? 0) > 6 { errors[.shortName] = "6자까지 넣어 주세요" }
        if let dose = trimmed(form.dose).flatMap(PetRegistration.number), dose > 0 {
        } else {
            errors[.dose] = trimmed(form.dose) == nil ? "한 번에 줄 양을 넣어 주세요" : "0보다 큰 숫자로 넣어 주세요"
        }
        if let stock = trimmed(form.stock), PetRegistration.number(stock) == nil {
            errors[.stock] = "0 이상 숫자로 넣어 주세요"
        }
        return errors
    }

    /// 약방에 들이기. 남은 양을 적었으면 지금 센 것으로.
    @discardableResult
    static func add(_ form: Form, now: Date = .now, in context: ModelContext) throws -> Medication {
        let stock = trimmed(form.stock).flatMap(PetRegistration.number)
        let med = Medication(now: now, name: trimmed(form.name) ?? "", shortName: trimmed(form.shortName), category: form.category,
                             form: form.form, doseAmount: trimmed(form.dose).flatMap(PetRegistration.number) ?? 1, unit: unit(form.form),
                             stockAmount: stock, stockCountedAt: stock == nil ? nil : now, memo: trimmed(form.memo))
        context.insert(med)
        try save(context)
        return med
    }

    /// 고치기: 바꾼 게 없으면 저장하지 않음. 남은 양 칸을 바꿨을 때만 다시 셈(비우면 안 적음).
    /// - Returns: 저장했는지.
    @discardableResult
    static func update(_ med: Medication, from original: Form, to form: Form, now: Date = .now, in context: ModelContext) throws -> Bool {
        guard form != original else { return false }
        med.name = trimmed(form.name) ?? med.name
        med.shortName = trimmed(form.shortName)
        med.category = form.category
        let unitChanged = unit(form.form) != med.unit
        med.form = form.form
        med.unit = unit(form.form)
        med.doseAmount = trimmed(form.dose).flatMap(PetRegistration.number) ?? med.doseAmount
        med.memo = trimmed(form.memo)
        // 모양이 바뀌어 단위가 바뀌면 옛 단위 기록은 더 빼지 않으니, 칸에 보이는 지금 남은 양으로 다시 센다.
        if form.stock != original.stock || unitChanged {
            med.stockAmount = trimmed(form.stock).flatMap(PetRegistration.number)
            med.stockCountedAt = med.stockAmount == nil ? nil : now
        }
        med.updatedAt = now
        try save(context)
        return true
    }

    // MARK: 빼기

    /// 이름 + 이/가(받침이 있으면 '이'). 한글로 끝나지 않으면 '이(가)'.
    static func withSubjectParticle(_ name: String) -> String {
        guard let last = name.unicodeScalars.last, (0xAC00...0xD7A3).contains(last.value) else { return name + "이(가)" }
        return name + ((last.value - 0xAC00) % 28 == 0 ? "가" : "이")
    }

    /// 이름 + 은/는.
    static func withTopicParticle(_ name: String) -> String {
        guard let last = name.unicodeScalars.last, (0xAC00...0xD7A3).contains(last.value) else { return name + "은(는)" }
        return name + ((last.value - 0xAC00) % 28 == 0 ? "는" : "은")
    }

    /// 이 약을 먹고 있는 아이 이름(열린 약 주기 — `activeUntil` 없음, 지우지 않음). 비면 뺄 수 있음.
    static func takers(_ med: Medication, schedules: [MedSchedule], pets: [Pet]) -> [String] {
        let ids = Set(schedules.filter { $0.medicationId == med.id && $0.deletedAt == nil && $0.activeUntil == nil }.map(\.petId))
        return pets.filter { ids.contains($0.id) && $0.deletedAt == nil }.sorted { $0.createdAt < $1.createdAt }.map(\.name)
    }

    /// '위장약 시럽은 보리가 먹고 있는 약이에요. …'(여럿이면 '콩이·보리가').
    static func blockedMessage(_ med: Medication, takers: [String]) -> String {
        "\(withTopicParticle(med.name)) \(withSubjectParticle(takers.joined(separator: "·"))) 먹고 있는 약이에요. 먼저 탕약에서 이 약을 '그만 먹이기'로 바꿔 주세요."
    }

    /// 약방에서 빼기: `deletedAt`만(지난 기록의 약 이름은 남음 — 1-4).
    static func remove(_ med: Medication, now: Date = .now, in context: ModelContext) throws {
        med.deletedAt = now
        med.updatedAt = now
        try save(context)
    }

    private static func save(_ context: ModelContext) throws {
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
    }
}
