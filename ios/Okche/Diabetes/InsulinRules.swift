import Foundation
import SwiftData

/// 인슐린 줄·시트 규칙(plan.md 9-2, 데이터 3장·7장). 지금 차례 구간·열린 계획은 `GlucoseRules`와 같이 쓴다. 안드로이드 `InsulinRules.kt`와 같은 규칙.
enum InsulinRules {
    enum Status: String { case full, partial, skipped }

    static let statusTitles: [(value: Status, title: String)] = [(.full, "다 맞았어요"), (.partial, "일부만 들어갔어요"), (.skipped, "건너뛰었어요")]
    static let mealTitles: [(value: String, title: String)] = [("all", "다 먹음"), ("some", "남김"), ("none", "안 먹음")]
    private static let mealWords = ["all": "밥 다 먹음", "some": "밥 남김", "none": "밥 안 먹음"]

    /// 구간 안 차례: 시간·양·구간·차례 시각(구간 시작 + 1시간)·`slotKey`.
    struct Slot {
        let time: String
        let units: Double
        let start: Date
        let end: Date
        let due: Date
        let key: String
    }

    /// 그 시각에 쓰던 계획(`activeFrom` ≤ 시각 < `activeUntil`) — 늦은 `activeFrom`, 같으면 늦은 `updatedAt`. 지난 기록·고치기는 이 계획으로.
    static func planAt(_ plans: [InsulinPlan], petId: UUID, at: Date) -> InsulinPlan? {
        plans.filter { $0.petId == petId && $0.deletedAt == nil && $0.activeFrom <= at && ($0.activeUntil.map { at < $0 } ?? true) }
            .max { ($0.activeFrom, $0.updatedAt) < ($1.activeFrom, $1.updatedAt) }
    }

    /// `at`이 든 구간의 차례. 차례 날은 그 차례 시각의 6시 기준 날. 그 시간의 양이 없으면 nil(안드로이드와 같음).
    static func slot(_ plan: InsulinPlan, at: Date) -> Slot? {
        guard let w = GlucoseRules.window(plan, now: at) else { return nil }
        let due = w.start.addingTimeInterval(3600)
        let time = MedSlots.hhmm(due)
        guard let units = plan.slots.first(where: { $0.time == time })?.units else { return nil }
        return Slot(time: time, units: units, start: w.start, end: w.end, due: due,
                    key: "\(plan.id.uuidString.lowercased())|\(OkcheDay.of(due))|\(time)")
    }

    /// 처리한 상태: 구간 안 그 아이의 가장 늦은 인슐린 기록(어느 계획·기기든, 건너뜀 포함 — 두 번 맞히기 방지).
    static func handled(petId: UUID, records: [Record], slot: Slot) -> Record? {
        records.filter { $0.petId == petId && $0.deletedAt == nil && $0.kind == RecordKind.insulin.rawValue
            && $0.occurredAt >= slot.start && $0.occurredAt < slot.end }
            .max { $0.occurredAt < $1.occurredAt }
    }

    /// 다음 차례 시각(지금 구간 끝 + 1시간).
    static func next(_ slot: Slot) -> Date { slot.end.addingTimeInterval(3600) }

    /// 시트 시각이 든 구간에 이미 맞은(다 맞음·일부만) 다른 기록 — 고치는 기록은 빼고(건너뜀만이면 없음).
    static func twice(petId: UUID, records: [Record], slot: Slot, editing: Record?) -> Record? {
        records.filter { $0.petId == petId && $0.deletedAt == nil && $0.kind == RecordKind.insulin.rawValue && $0.id != editing?.id
            && $0.occurredAt >= slot.start && $0.occurredAt < slot.end && [Status.full, .partial].contains(status($0)) }
            .max { $0.occurredAt < $1.occurredAt } // 여럿이면 가장 늦은 것(안드로이드와 같음)
    }

    /// 맞은 양: 0보다 크고 100 이하(인슐린 설정과 같음).
    static func units(_ text: String) -> Double? {
        guard let n = PetRegistration.number(text.trimmingCharacters(in: .whitespacesAndNewlines)), n > 0, n <= 100 else { return nil }
        return n
    }

    static func unitsError(_ text: String) -> String? {
        text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || units(text) != nil ? nil : "0보다 크고 100 이하인 숫자로 넣어 주세요"
    }

    // MARK: 기록

    static func data(planId: UUID?, units: Double, status: Status, mealBefore: String?) -> String {
        var json: [String: Any] = ["units": status == .skipped ? 0 : units, "status": status.rawValue]
        if let planId { json["planId"] = planId.uuidString.lowercased() }
        if let mealBefore { json["mealBefore"] = mealBefore }
        return String(decoding: (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data("{}".utf8), as: UTF8.self)
    }

    /// 새 기록(차례 = 그 시각이 든 구간)이거나 고치기.
    @discardableResult
    static func save(petId: UUID, plan: InsulinPlan?, units: Double, status: Status, mealBefore: String?, at: Date,
                     editing: Record? = nil, now: Date = .now, in context: ModelContext) throws -> Record {
        let at = min(at, now) // 앞날 기록 막기
        // 시간을 안 바꾼 고치기·계획이 없으면 그 기록의 계획·차례 그대로(바꾼 뒤 계획으로 옮겨 가지 않게).
        let keep = editing.map { $0.occurredAt == at } ?? false
        let picked = keep ? nil : plan.flatMap { InsulinRules.slot($0, at: at) }
        let json = data(planId: keep ? editing.flatMap(planId) : (plan?.id ?? editing.flatMap(planId)), units: units, status: status, mealBefore: mealBefore)
        let record: Record
        if let editing {
            if editing.occurredAt != at { editing.setOccurredAt(at, now: now) }
            editing.data = json
            if let picked { editing.slotKey = picked.key }
            editing.updatedAt = now
            record = editing
        } else {
            record = Record(now: now, petId: petId, kind: .insulin, occurredAt: at, slotKey: picked?.key, data: json)
            context.insert(record)
        }
        do {
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
        return record
    }

    static func status(_ r: Record) -> Status? { (field(r, "status") as? String).flatMap(Status.init) }
    static func unitsValue(_ r: Record) -> Double { (field(r, "units") as? NSNumber)?.doubleValue ?? 0 }
    static func mealBefore(_ r: Record) -> String? { field(r, "mealBefore") as? String }
    static func planId(_ r: Record) -> UUID? { (field(r, "planId") as? String).flatMap(UUID.init(uuidString:)) }

    /// '2단위 맞음'·'1.5단위 일부만'·'건너뜀' + ' · 밥 안 먹음'(줄 이름 '인슐린'은 앞에서 붙음).
    static func value(_ r: Record) -> String {
        guard let s = status(r) else { return "" }
        let head = switch s {
        case .full: "\(MedRules.number(unitsValue(r)))단위 맞음"
        case .partial: "\(MedRules.number(unitsValue(r)))단위 일부만"
        case .skipped: "건너뜀"
        }
        return ([head] + [mealBefore(r).flatMap { mealWords[$0] }].compactMap { $0 }).joined(separator: " · ")
    }

    private static func field(_ r: Record, _ key: String) -> Any? {
        (try? JSONSerialization.jsonObject(with: Data(r.data.utf8)) as? [String: Any])?[key]
    }
}
