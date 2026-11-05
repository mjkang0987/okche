import Foundation
import SwiftData

/// 다과 규칙(plan.md 7-4b, 데이터 8장·7장 `snack`). 안드로이드 `SnackRules.kt`와 같은 규칙.
enum SnackRules {
    static let kinds: [(value: SnackKind, title: String)] = [
        (.lickable, "짜 먹는 간식"), (.freezeDried, "동결건조"), (.jerky, "져키·스틱"), (.wet, "캔·파우치"),
        (.prescription, "처방 간식"), (.human, "사람 음식"),
    ]

    static func kindTitle(_ kind: SnackKind) -> String { kinds.first { $0.value == kind }?.title ?? "" }

    /// 다과 기록의 다과 ID 글자(소문자 — 두 플랫폼 같은 글자).
    static func snackId(_ record: Record) -> String? { json(record)["snackId"] as? String }

    static func amount(_ record: Record) -> String? { json(record)["amount"] as? String }

    private static func json(_ record: Record) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any]) ?? [:]
    }

    /// 다과 기록 `data`: `snackId` + `amount`(앞뒤 빈칸 뺀 글자, 비면 없음).
    static func data(snackId: UUID, amount: String) -> String {
        var json: [String: Any] = ["snackId": snackId.uuidString.lowercased()]
        let trimmed = amount.trimmingCharacters(in: .whitespacesAndNewlines)
        if !trimmed.isEmpty { json["amount"] = trimmed }
        let bytes = (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data("{}".utf8)
        return String(decoding: bytes, as: UTF8.self)
    }

    /// 기록 값 글자 '연어 퓨레 · 반 개'(목록에서 뺀 다과도 이름은 남음 — 1-4).
    static func value(_ record: Record, snacks: [Snack]) -> String {
        let name = snackId(record).flatMap { id in snacks.first { $0.id.uuidString.lowercased() == id }?.name }
        return [name, amount(record)].compactMap { $0 }.joined(separator: " · ")
    }

    /// 다과 목록: 목록에서 빼지 않은 다과, 들인 순.
    static func listed(_ snacks: [Snack]) -> [Snack] {
        snacks.filter { $0.deletedAt == nil }.sorted { $0.createdAt < $1.createdAt }
    }

    /// 다과 목록 줄 아래 글자: '짜 먹는 간식 · 이번 달 6번 · 수라 바꾸기 중에도 괜찮아요'(0번이면 횟수 뺌). 이번 달 = 기기 달력 달, 지금 보는 아이.
    static func listDetail(_ snack: Snack, petId: UUID?, records: [Record], now: Date = .now, calendar: Calendar = .okcheDevice) -> String {
        let id = snack.id.uuidString.lowercased()
        let month = calendar.dateComponents([.year, .month], from: now)
        let count = petId == nil ? 0 : records.filter {
            $0.kind == RecordKind.snack.rawValue && $0.deletedAt == nil && $0.petId == petId && snackId($0) == id
                && calendar.dateComponents([.year, .month], from: $0.occurredAt) == month
        }.count
        return [kindTitle(snack.kind), count > 0 ? "이번 달 \(count)번" : nil, snack.okDuringFoodChange ? "수라 바꾸기 중에도 괜찮아요" : nil]
            .compactMap { $0 }.joined(separator: " · ")
    }

    /// 다과 기록 시트의 고르기 순서: 이 아이에게 최근 준 다과 먼저, 나머지는 들인 순. (다과, 마지막 준 날 `day`)
    static func pickOrder(_ snacks: [Snack], petId: UUID, records: [Record]) -> [(snack: Snack, lastDay: String?)] {
        var last: [String: (Date, String)] = [:]
        for record in records where record.kind == RecordKind.snack.rawValue && record.deletedAt == nil && record.petId == petId {
            guard let id = snackId(record) else { continue }
            if last[id].map({ record.occurredAt > $0.0 }) ?? true { last[id] = (record.occurredAt, record.day) }
        }
        let listed = listed(snacks).map { ($0, last[$0.id.uuidString.lowercased()]) }
        let given = listed.filter { $0.1 != nil }.sorted { $0.1!.0 > $1.1!.0 }
        return (given + listed.filter { $0.1 == nil }).map { ($0.0, $0.1?.1) }
    }

    /// 마지막 준 날 글자: '오늘'·'어제'·'10월 20일'.
    static func lastGiven(_ day: String, today: String) -> String {
        if day == today { return "오늘" }
        if day == QuickRecord.shift(today, by: -1) { return "어제" }
        let parts = day.split(separator: "-").compactMap { Int($0) }
        return parts.count == 3 ? "\(parts[1])월 \(parts[2])일" : day
    }

    /// 수라 바꾸기 기간(데이터 8장): 지금 먹는 사료 바꾸기의 `startedOn`부터 `finishedOn` 전날까지, 없으면 14일째까지.
    static func inFoodChange(petId: UUID, day: String, changes: [FoodChange]) -> Bool {
        guard let change = FoodRules.change(at: day, petId: petId, in: changes),
              let number = FoodRules.dayNumber(startedOn: change.startedOn, today: day) else { return false }
        if let finished = change.finishedOn { return day < finished }
        return number <= 14
    }

    /// 다과 들이기(이름 앞뒤 빈칸 뺌).
    @discardableResult
    static func add(name: String, kind: SnackKind, okDuringFoodChange: Bool, now: Date = .now, in context: ModelContext) throws -> Snack {
        let snack = Snack(now: now, name: name.trimmingCharacters(in: .whitespacesAndNewlines), kind: kind, okDuringFoodChange: okDuringFoodChange)
        context.insert(snack)
        try save(context)
        return snack
    }

    static func update(_ snack: Snack, name: String, kind: SnackKind, okDuringFoodChange: Bool, now: Date = .now, in context: ModelContext) throws {
        snack.name = name.trimmingCharacters(in: .whitespacesAndNewlines)
        snack.kind = kind
        snack.okDuringFoodChange = okDuringFoodChange
        snack.updatedAt = now
        try save(context)
    }

    /// 목록에서 빼기: `deletedAt`만(이름은 남아 지난 기록 글자에 쓰임 — 1-4).
    static func remove(_ snack: Snack, now: Date = .now, in context: ModelContext) throws {
        snack.deletedAt = now
        snack.updatedAt = now
        try save(context)
    }

    /// 목록에서 뺀 다과 되돌리기(뺀 직후 알림의 '되돌리기').
    static func restore(_ snack: Snack, now: Date = .now, in context: ModelContext) throws {
        snack.deletedAt = nil
        snack.updatedAt = now
        try save(context)
    }

    /// 다과 기록하기: 새 기록이면 넣고, 고치기면 `data`·(시간을 바꿨으면)`occurredAt`·`day`·`updatedAt`.
    @discardableResult
    static func record(snackId: UUID, amount: String, occurredAt: Date, editing: Record?, petId: UUID, now: Date = .now,
                       timeZone: TimeZone = .autoupdatingCurrent, in context: ModelContext) throws -> Record {
        let at = min(occurredAt, now) // 지금보다 뒤는 안 됨
        let data = data(snackId: snackId, amount: amount)
        let record: Record
        if let editing {
            editing.data = data
            if at != editing.occurredAt {
                editing.setOccurredAt(at, timeZone: timeZone, now: now)
            } else {
                editing.updatedAt = now
            }
            record = editing
        } else {
            record = Record(now: now, petId: petId, kind: .snack, occurredAt: at, timeZone: timeZone, data: data)
            context.insert(record)
        }
        try save(context)
        return record
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
