import Foundation
import SwiftData

/// 혈당 규칙(plan.md 9-1, 데이터 3장·7장). 안드로이드 `GlucoseRules.kt`와 같은 규칙.
enum GlucoseRules {
    enum Timing: String, CaseIterable { case beforeInsulin, afterInsulin, other }
    enum Sign: String, CaseIterable { case weak, wobbly, seizure }

    static let timingTitles: [(value: Timing, title: String)] = [(.beforeInsulin, "인슐린 맞기 전"), (.afterInsulin, "맞고 난 뒤"), (.other, "그 밖")]
    static let signTitles: [(value: Sign, title: String)] = [(.weak, "기운이 없고 축 늘어져요"), (.wobbly, "비틀거리거나 떨어요"), (.seizure, "경련을 해요")]
    /// 값 글자의 짧은 말.
    private static let timingWords: [Timing: String] = [.beforeInsulin: "맞기 전", .afterInsulin: "맞고 난 뒤", .other: "그 밖"]
    private static let signWords: [Sign: String] = [.weak: "기운 없음", .wobbly: "비틀거림", .seizure: "경련"]

    // MARK: 숫자

    /// 앞뒤 빈칸 뺀 숫자만 1~999(6-1 숫자 규칙).
    static func number(_ text: String) -> Int? {
        let t = text.trimmingCharacters(in: .whitespaces)
        guard !t.isEmpty, t.allSatisfy({ $0.isASCII && $0.isNumber }), let n = Int(t), (1...999).contains(n) else { return nil }
        return n
    }

    /// 빈칸이면 안내 없음(버튼만 흐림), 틀리면 '1에서 999 사이로 넣어 주세요'.
    static func error(_ text: String) -> String? {
        text.trimmingCharacters(in: .whitespaces).isEmpty || number(text) != nil ? nil : "1에서 999 사이로 넣어 주세요"
    }

    // MARK: 지금 차례 구간(7장)

    /// 쓰는 계획: 열린 줄 중 늦은 `activeFrom`, 같으면 늦은 `updatedAt`.
    static func openPlan(_ plans: [InsulinPlan], petId: UUID) -> InsulinPlan? {
        plans.filter { $0.petId == petId && $0.deletedAt == nil && $0.activeUntil == nil }
            .max { ($0.activeFrom, $0.updatedAt) < ($1.activeFrom, $1.updatedAt) }
    }

    /// 각 차례는 자기 시간 1시간 전부터 다음 차례 1시간 전까지(날을 넘김). 지금이 든 구간.
    static func window(_ plan: InsulinPlan, now: Date, calendar: Calendar = .current) -> (start: Date, end: Date)? {
        let times = Array(Set(plan.slots.map(\.time))).sorted()
        guard !times.isEmpty else { return nil }
        // 어제·오늘·내일의 구간 시작(차례 − 1시간)을 이어 놓고 지금이 든 칸을 찾는다.
        let starts: [Date] = (-1...1).flatMap { offset -> [Date] in
            guard let day = calendar.date(byAdding: .day, value: offset, to: calendar.startOfDay(for: now)) else { return [] }
            return times.compactMap { t in
                let p = t.split(separator: ":").compactMap { Int($0) }
                return calendar.date(bySettingHour: p.first ?? 0, minute: p.count > 1 ? p[1] : 0, second: 0, of: day)?.addingTimeInterval(-3600)
            }
        }.sorted()
        for (a, b) in zip(starts, starts.dropFirst()) where a <= now && now < b { return (a, b) }
        return nil
    }

    /// 남기기 짐작: 열린 계획이 없으면 그 밖, 지금 구간에 다 맞음·일부만 기록이 있으면 맞고 난 뒤, 아니면 nil(묻기 — 건너뜀만 있어도).
    static func guess(petId: UUID, plans: [InsulinPlan], insulinRecords: [Record], now: Date = .now) -> Timing? {
        guard let plan = openPlan(plans, petId: petId) else { return .other }
        guard let w = window(plan, now: now) else { return nil }
        let given = insulinRecords.contains { r in
            r.petId == petId && r.deletedAt == nil && r.kind == RecordKind.insulin.rawValue
                && r.occurredAt >= w.start && r.occurredAt < w.end && ["full", "partial"].contains(field(r, "status") as? String)
        }
        return given ? .afterInsulin : nil
    }

    // MARK: 기록

    static func data(mgdl: Int, timing: Timing, signs: Set<Sign>) -> String {
        let json: [String: Any] = ["mgdl": mgdl, "timing": timing.rawValue,
                                   "signs": Sign.allCases.filter(signs.contains).map(\.rawValue)]
        return String(decoding: (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data("{}".utf8), as: UTF8.self)
    }

    /// 새 기록이면 넣고, 고치기면 그 기록을 바꿔 저장.
    @discardableResult
    static func save(petId: UUID?, mgdl: Int, timing: Timing, signs: Set<Sign>, at: Date, editing: Record? = nil,
                     now: Date = .now, in context: ModelContext) throws -> Record {
        let record: Record
        if let editing {
            if editing.occurredAt != at { editing.setOccurredAt(at, now: now) }
            editing.data = data(mgdl: mgdl, timing: timing, signs: signs)
            editing.updatedAt = now
            record = editing
        } else {
            record = Record(now: now, petId: petId, kind: .glucose, occurredAt: at, data: data(mgdl: mgdl, timing: timing, signs: signs))
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

    static func mgdl(_ r: Record) -> Int? { field(r, "mgdl") as? Int }
    static func timing(_ r: Record) -> Timing? { (field(r, "timing") as? String).flatMap(Timing.init) }
    static func signs(_ r: Record) -> Set<Sign> { Set((field(r, "signs") as? [String] ?? []).compactMap(Sign.init)) }

    /// 값 글자 '186 · 맞고 난 뒤 · 비틀거림'(줄 이름 '혈당'은 앞에서 붙음).
    static func value(_ r: Record) -> String {
        guard let n = mgdl(r) else { return "" }
        let parts = ["\(n)"] + [timing(r).flatMap { timingWords[$0] }].compactMap { $0 }
            + Sign.allCases.filter(signs(r).contains).compactMap { signWords[$0] }
        return parts.joined(separator: " · ")
    }

    private static func field(_ r: Record, _ key: String) -> Any? {
        (try? JSONSerialization.jsonObject(with: Data(r.data.utf8)) as? [String: Any])?[key]
    }
}
