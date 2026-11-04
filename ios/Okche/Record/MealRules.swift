import Foundation
import SwiftData

/// 수라·끼니·급식기 규칙(plan.md 7-3, 데이터 7·8장, 고정 ID 1-5). 안드로이드 `MealRules.kt`와 같은 규칙.
enum MealRules {
    /// 끼니 차례 하나: 그 끼니가 그날 오는 때와 `slotKey`.
    struct Occurrence: Equatable {
        let slot: MealSlot
        let at: Date
        let day: String
        let key: String
        /// 이 차례를 낸 계획의 급여 방식(급식기면 고정 ID — 1-5).
        var mode: MealMode = .direct
    }

    /// 끼니 이름: 시간 순으로 1개 아침, 2개 아침·저녁, 3개 아침·점심·저녁, 넷째부터 '끼니 4'(2026-11-04 사용자 — 보드대로 자동).
    static func names(_ count: Int) -> [String] {
        let base: [String] = switch count {
        case 0: []
        case 1: ["아침"]
        case 2: ["아침", "저녁"]
        default: ["아침", "점심", "저녁"]
        }
        return base + (base.count..<max(count, base.count)).map { "끼니 \($0 + 1)" }
    }

    /// 시간 순으로 정렬하고 이름을 붙인 끼니들.
    static func named(_ slots: [MealSlot]) -> [MealSlot] {
        let sorted = slots.sorted { $0.time < $1.time }
        return zip(sorted, names(sorted.count)).map { MealSlot(name: $1, time: $0.time, grams: $0.grams) }
    }

    /// 쓰는 계획: 열린 줄 중 `activeFrom`이 늦은 것, 같으면 `updatedAt`이 늦은 것(7장 — 두 기기 백업을 합친 경우).
    static func openPlan(_ plans: [MealPlan]) -> MealPlan? {
        plans.filter { $0.activeUntil == nil && $0.deletedAt == nil }
            .max { ($0.activeFrom, $0.updatedAt) < ($1.activeFrom, $1.updatedAt) }
    }

    static func openPlan(petId: UUID, in context: ModelContext) throws -> MealPlan? {
        try openPlan(context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate { $0.petId == petId })))
    }

    /// 달력 날짜 `date`의 `time`에 오는 차례(시간대 = `timeZone`).
    private static func occurrence(_ plan: MealPlan, _ slot: MealSlot, on date: Date, _ calendar: Calendar) -> Occurrence? {
        let hm = slot.time.split(separator: ":").compactMap { Int($0) }
        guard hm.count == 2, let at = calendar.date(bySettingHour: hm[0], minute: hm[1], second: 0, of: date) else { return nil }
        let day = OkcheDay.of(at, in: calendar.timeZone)
        // ID 글자는 소문자(5-1) — 안드로이드와 같은 `slotKey`·고정 ID가 나오게.
        return Occurrence(slot: slot, at: at, day: day, key: "\(plan.id.uuidString.lowercased())|\(day)|\(slot.time)", mode: plan.mode)
    }

    private static func calendar(_ timeZone: TimeZone) -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = timeZone
        return calendar
    }

    /// 그 계획이 쓰이던 동안의 차례인지: `activeFrom` ≤ 시각 < `activeUntil`. 끼니를 바꾸면 '아직 지나지 않은 끼니부터' 새 계획(8장).
    private static func covers(_ plan: MealPlan, _ at: Date) -> Bool {
        at >= plan.activeFrom && plan.activeUntil.map { at < $0 } ?? true
    }

    /// 쓸 계획들: 닫힌 줄 전부 + 열린 줄은 하나(7장 — 두 기기 백업을 합쳐 둘이어도 분모가 두 배가 안 되게).
    private static func usable(_ plans: [MealPlan]) -> [MealPlan] {
        let live = plans.filter { $0.deletedAt == nil }
        return live.filter { $0.activeUntil != nil } + [openPlan(live)].compactMap { $0 }
    }

    /// 그날(6시 기준) 끼니 차례들 — 그날 계획을 바꿨으면 바꾸기 전 차례는 앞 계획, 뒤는 새 계획에서. 시각 순.
    static func occurrences(_ plans: [MealPlan], day: String, timeZone: TimeZone = .autoupdatingCurrent) -> [Occurrence] {
        usable(plans).flatMap { plan in occurrences(plan, day: day, timeZone: timeZone).filter { covers(plan, $0.at) } }
            .sorted { $0.at < $1.at }
    }

    /// 지금 차례(여러 계획 — 바꾼 날에도 지난 끼니는 앞 계획의 차례).
    static func current(_ plans: [MealPlan], now: Date, timeZone: TimeZone = .autoupdatingCurrent) -> Occurrence? {
        let cal = calendar(timeZone)
        let candidates = usable(plans).flatMap { plan in
            (-1...1).flatMap { offset in plan.slots.compactMap { occurrence(plan, $0, on: cal.date(byAdding: .day, value: offset, to: now)!, cal) } }
                .filter { covers(plan, $0.at) }
        }
        return candidates.filter { $0.at.addingTimeInterval(-3600) <= now }.max { $0.at < $1.at }
    }

    /// 그날(6시 기준) 한 계획의 끼니 차례들, 시각 순. 6시 전 끼니는 달력 다음 날에 온다.
    static func occurrences(_ plan: MealPlan, day: String, timeZone: TimeZone = .autoupdatingCurrent) -> [Occurrence] {
        let cal = calendar(timeZone)
        guard let date = PetRegistration.date(day, cal) else { return [] }
        return plan.slots.compactMap { slot in
            let next = slot.time < String(format: "%02d:00", OkcheDay.startHour)
            return occurrence(plan, slot, on: next ? cal.date(byAdding: .day, value: 1, to: date)! : date, cal)
        }
        .sorted { $0.at < $1.at }
    }

    /// 지금 차례: 각 차례 구간은 자기 시간 1시간 전부터 다음 차례 1시간 전까지(7장 인슐린과 같은 규칙).
    static func current(_ plan: MealPlan, now: Date, timeZone: TimeZone = .autoupdatingCurrent) -> Occurrence? {
        let cal = calendar(timeZone)
        let candidates = (-1...1).flatMap { offset in
            plan.slots.compactMap { occurrence(plan, $0, on: cal.date(byAdding: .day, value: offset, to: now)!, cal) }
        }
        return candidates.filter { $0.at.addingTimeInterval(-3600) <= now }.max { $0.at < $1.at }
    }

    /// 수라 기록 `data`: 결과 + 끼니 이름·준 양·남은 양(있을 때만).
    static func data(result: String, slotName: String?, givenGrams: Int?, leftGrams: Int?) -> String {
        var json: [String: Any] = ["result": result]
        json["slotName"] = slotName
        json["givenGrams"] = givenGrams
        json["leftGrams"] = result == "some" ? leftGrams : nil
        let bytes = (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data("{}".utf8)
        return String(decoding: bytes, as: UTF8.self)
    }

    static func json(_ record: Record) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any]) ?? [:]
    }

    /// 그 차례의 지우지 않은 수라 기록.
    static func record(key: String, in context: ModelContext) throws -> Record? {
        var query = FetchDescriptor<Record>(predicate: #Predicate { $0.slotKey == key && $0.deletedAt == nil })
        query.fetchLimit = 1
        return try context.fetch(query).first
    }

    /// 일기 수라 바로 기록의 차례: 끼니가 있으면 지금 차례, 그 차례에 이미 기록이 있으면 없음(추가 식사). 끼니가 없으면 없음.
    static func quickSlot(petId: UUID?, now: Date, timeZone: TimeZone = .autoupdatingCurrent, in context: ModelContext) throws -> Occurrence? {
        guard let petId else { return nil }
        let plans = try context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate { $0.petId == petId }))
        guard openPlan(plans) != nil, let slot = current(plans, now: now, timeZone: timeZone),
              try record(key: slot.key, in: context) == nil else { return nil }
        return slot
    }

    /// 수라 화면에서 끼니 결과를 고름: 그 차례 기록이 있으면 결과만 고치고(남김이 아니면 남은 양 비움), 없으면 지금 시각으로 만든다.
    @discardableResult
    static func pick(_ result: String, for slot: Occurrence, petId: UUID, now: Date = .now, timeZone: TimeZone = .autoupdatingCurrent,
                     in context: ModelContext) throws -> Record {
        let record: Record
        if let existing = try self.record(key: slot.key, in: context) {
            let json = json(existing)
            existing.data = data(result: result, slotName: json["slotName"] as? String, givenGrams: json["givenGrams"] as? Int,
                                 leftGrams: json["leftGrams"] as? Int)
            existing.updatedAt = now
            existing.auto = false // 보호자가 고친 기록
            record = existing
        } else {
            // 급식기 차례는 자동 기록과 같은 고정 ID로(1-5 — 다른 기기의 자동 기록과 합쳐지게). 그 ID 줄이 지운 채 있으면 새 ID.
            let fixed = UUID.feederRecord(slotKey: slot.key)
            var taken = FetchDescriptor<Record>(predicate: #Predicate { $0.id == fixed })
            taken.fetchLimit = 1
            let id = try slot.mode == .feeder && context.fetch(taken).isEmpty ? fixed : UUID()
            record = Record(id: id, now: now, petId: petId, kind: .meal, occurredAt: now, timeZone: timeZone, slotKey: slot.key,
                            data: data(result: result, slotName: slot.slot.name, givenGrams: slot.slot.grams, leftGrams: nil))
            context.insert(record)
        }
        try save(context)
        return record
    }

    /// 끼니 없이 '지금 먹은 것': 지금 시각, `slotKey` 없음.
    @discardableResult
    static func pickNow(_ result: String, petId: UUID, now: Date = .now, timeZone: TimeZone = .autoupdatingCurrent, in context: ModelContext) throws -> Record {
        let record = Record(now: now, petId: petId, kind: .meal, occurredAt: now, timeZone: timeZone,
                            data: data(result: result, slotName: nil, givenGrams: nil, leftGrams: nil))
        context.insert(record)
        try save(context)
        return record
    }

    /// '남은 양 (g, 선택)': 0~2000 정수, 비우면 없음.
    static func setLeft(_ grams: Int?, on record: Record, now: Date = .now, in context: ModelContext) throws {
        let json = json(record)
        record.data = data(result: json["result"] as? String ?? "some", slotName: json["slotName"] as? String,
                           givenGrams: json["givenGrams"] as? Int, leftGrams: grams)
        record.updatedAt = now
        try save(context)
    }

    /// 끼니 시간 저장·급여 방식 바꾸기: 열린 계획을 지금 닫고 같은 시각 새 줄(지난 기록은 그대로). 직접 급여에서 끼니가 없으면 닫기만.
    static func savePlan(petId: UUID, mode: MealMode, slots: [MealSlot], now: Date = .now, in context: ModelContext) throws {
        let plans = try context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate { $0.petId == petId }))
        for plan in plans where plan.activeUntil == nil && plan.deletedAt == nil {
            plan.activeUntil = now
            plan.updatedAt = now
        }
        if !slots.isEmpty {
            context.insert(MealPlan(now: now, petId: petId, mode: mode, slots: named(slots), activeFrom: now))
        }
        try save(context)
    }

    // shortcut: 앞으로 올 때마다 급식기 계획의 모든 날을 처음부터 돈다(이미 있으면 건너뜀), 오래 쓰다 느려지면 마지막으로 채운 날을 기기 설정에 두고 그 뒤만 돈다.
    /// 급식기 자동 기록: 급식기 계획마다 `activeFrom`부터 지금(닫혔으면 닫힌 때)까지 지난 차례를 '다 먹음'으로 고정 ID로 채운다.
    /// 같은 ID 줄이 있으면(지운 줄 포함) 만들지 않고, 그 차례에 보호자가 남긴 기록이 있어도 만들지 않는다. 몇 개 만들었는지 돌려준다.
    @discardableResult
    static func fillFeeder(now: Date = .now, timeZone: TimeZone = .autoupdatingCurrent, in context: ModelContext) throws -> Int {
        let feeder = MealMode.feeder
        // 아이마다 쓸 계획(열린 줄 하나 — 7장)만, 그중 급식기.
        let all = try context.fetch(FetchDescriptor<MealPlan>(predicate: #Predicate { $0.deletedAt == nil }))
        let plans = Dictionary(grouping: all, by: \.petId).values.flatMap(usable).filter { $0.mode == feeder }
        guard !plans.isEmpty else { return 0 }
        let meal = RecordKind.meal.rawValue
        let meals = try context.fetch(FetchDescriptor<Record>(predicate: #Predicate { $0.kind == meal }))
        var ids = Set(meals.map(\.id))
        var keys = Set(meals.filter { $0.deletedAt == nil }.compactMap(\.slotKey))
        let cal = calendar(timeZone)
        var made = 0
        for plan in plans {
            let until = min(plan.activeUntil ?? now, now)
            guard plan.activeFrom < until else { continue }
            var date = cal.date(byAdding: .day, value: -1, to: cal.startOfDay(for: plan.activeFrom))!
            while date <= until {
                for slot in plan.slots {
                    guard let o = occurrence(plan, slot, on: date, cal), o.at >= plan.activeFrom, o.at <= until,
                          plan.activeUntil.map({ o.at < $0 }) ?? true else { continue }
                    let id = UUID.feederRecord(slotKey: o.key)
                    guard !ids.contains(id), !keys.contains(o.key) else { continue }
                    context.insert(Record(id: id, now: o.at, petId: plan.petId, kind: .meal, occurredAt: o.at, timeZone: timeZone, slotKey: o.key,
                                          data: data(result: "all", slotName: slot.name, givenGrams: slot.grams, leftGrams: nil), auto: true))
                    ids.insert(id)
                    keys.insert(o.key)
                    made += 1
                }
                date = cal.date(byAdding: .day, value: 1, to: date)!
            }
        }
        if made > 0 { try save(context) }
        return made
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
