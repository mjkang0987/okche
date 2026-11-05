import Foundation
import SwiftData

/// 탕약 차례·오늘 먹일 약·차례 기록(plan.md 8-3a, 데이터 5장 '바꾼 주기'·'짝 없는 기록', 6-1·6-2). 안드로이드 `MedSlots.kt`와 같은 규칙.
enum MedSlots {
    /// 차례 하나: 그 줄, `slotKey`, 먹일 시각, 차례의 날(6시 기준).
    struct Slot {
        let schedule: MedSchedule
        let key: String
        let due: Date
        let day: String
    }

    private static let handledStatuses: Set<String> = ["given", "skipped", "vomited"]

    static func json(_ record: Record) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any]) ?? [:]
    }

    private static func id(_ s: MedSchedule) -> String { s.id.uuidString.lowercased() }

    /// 바로 앞 줄이 있고 `activeFrom`이 그날인 `times` 줄 — 그날은 `activeFrom` 거르기 없이 넘김 규칙(5장).
    private static func isChangeDay(_ row: MedSchedule, day: String, series: [MedSchedule]) -> Bool {
        row.frequency != .everyNHours && row.frequency != .asNeeded && OkcheDay.of(row.activeFrom) == day
            && series.contains { $0.activeUntil == row.activeFrom && $0 !== row }
    }

    /// `times` 줄의 그날 차례(6-1). `fromFilter`가 거짓이면 `activeFrom` 앞 시간도.
    private static func timesSlots(_ row: MedSchedule, day: String, fromFilter: Bool) -> [Slot] {
        guard MedSchedules.isDoseDay(row, day: day) else { return [] }
        return Set(row.times).compactMap { time -> Slot? in
            guard let due = MedSchedules.occurrence(day: day, time: time),
                  !fromFilter || due >= row.activeFrom, row.activeUntil.map({ due < $0 }) ?? true else { return nil }
            return Slot(schedule: row, key: "\(id(row))|\(day)|\(time)", due: due, day: day)
        }
        .sorted { $0.due < $1.due }
    }

    /// 몇 시간마다 줄의 차례를 `through` 날까지(6-1). 늦게 줬을 때 밀리면 앞 차례의 가장 이른 준·토한 기록 + 간격.
    private static func hoursSlots(_ row: MedSchedule, given: [String: Date], through: String) -> [Slot] {
        guard row.frequency == .everyNHours, let first = row.firstAt, let h = row.everyHours, h > 0 else { return [] }
        let step = TimeInterval(h * 3600)
        var slots: [Slot] = []
        var due = first
        for n in 0..<100_000 {
            let day = OkcheDay.of(due)
            if day > through { break }
            if let until = row.activeUntil, due >= until { break }
            let key = "\(id(row))|#\(n)"
            // `firstAt`은 보호자가 정한 0번 차례 — 분 올림 때문에 저장한 순간(`activeFrom`)보다 1분 안에서 앞이면 남긴다(추억 되돌리기의 옛 `firstAt`은 거름).
            if due >= min(row.activeFrom, max(first, row.activeFrom.addingTimeInterval(-60))) { slots.append(Slot(schedule: row, key: key, due: due, day: day)) }
            due = row.shiftOnLate ? (given[key] ?? due).addingTimeInterval(step) : first.addingTimeInterval(Double(n + 1) * step)
        }
        return slots
    }

    /// 그날 차례 하나와 그 차례를 처리한 기록(없으면 남은 차례). 짝 없는 기록이 처리한 차례면 그 기록.
    struct Entry {
        let slot: Slot
        let record: Record?
    }

    /// 한 아이의 그날 남은 차례(6-2): 처리한 차례·넘김·버림을 빼고 먹일 시각 순. 시간이 지나도 남는다.
    static func remaining(_ schedules: [MedSchedule], records: [Record], petId: UUID, day: String) -> [Slot] {
        today(schedules, records: records, petId: petId, day: day).filter { $0.record == nil }.map(\.slot)
    }

    /// 한 아이의 그날 모든 차례(이미 기록한 것 포함, 넘김·버림 빼고) — 오늘 먹일 약 시트(8-3b).
    /// `schedules`는 그 아이의 닫힌 줄까지 모두(바꾼 날 규칙에 앞 줄이 필요), `records`는 `med` 기록(지운 것은 거름).
    static func today(_ schedules: [MedSchedule], records: [Record], petId: UUID, day: String) -> [Entry] {
        let recs = records.filter { $0.deletedAt == nil && $0.kind == RecordKind.med.rawValue && $0.petId == petId && $0.slotKey != nil }
        let keyed = Dictionary(grouping: recs, by: { $0.slotKey! })
        let handled = Set(keyed.filter { $0.value.contains { handledStatuses.contains(json($0)["status"] as? String ?? "") } }.keys)
        // 늦게 줬을 때 밀리는 몇 시간마다: 차례마다 가장 이른 준·토한 시각.
        let given = keyed.compactMapValues { list in
            list.filter { ["given", "vomited"].contains(json($0)["status"] as? String ?? "") }.map(\.occurredAt).min()
        }
        // 필요할 때만 줄도 묶음에 넣는다(차례는 없지만 '필요할 때만 → 매일'로 바꾼 날의 바로 앞 줄이 됨).
        let mine = schedules.filter { $0.petId == petId }
        var result: [Entry] = []
        for series in Dictionary(grouping: mine, by: \.seriesId).values {
            // 열린 줄이 둘 이상이면(백업 병합) `activeFrom`, 같으면 `updatedAt`이 늦은 것 하나만(5장).
            let open = series.filter { $0.activeUntil == nil }
            let keep = open.max { ($0.activeFrom, $0.updatedAt) < ($1.activeFrom, $1.updatedAt) }
            let rows = series.filter { $0.activeUntil != nil || $0 === keep }.sorted { $0.activeFrom < $1.activeFrom }
            // 짝 없는 기록은 그 주기의 모든 줄로 따진다 — 병합으로 빠진 열린 줄의 기록은 짝 없음(차례가 없으니).
            let rowById = Dictionary(series.map { (id($0), $0) }, uniquingKeysWith: { a, _ in a })

            func slots(_ row: MedSchedule, _ d: String) -> [Slot] {
                row.frequency == .everyNHours
                    ? hoursSlots(row, given: given, through: d).filter { $0.day == d }
                    : timesSlots(row, day: d, fromFilter: !isChangeDay(row, day: d, series: rows))
            }
            /// 짝 없는 기록: 이 주기 줄의 키인데 그 줄의 차례가 아닌 그날 기록.
            func isUnmatched(_ record: Record) -> Bool {
                guard let key = record.slotKey, let row = rowById[String(key.split(separator: "|").first ?? "")] else { return false }
                guard rows.contains(where: { $0 === row }) else { return true }
                if row.frequency == .everyNHours {
                    let through = QuickRecord.shift(max(record.day, day), by: 1)
                    return !hoursSlots(row, given: given, through: through).contains { $0.key == key }
                }
                let parts = key.split(separator: "|")
                guard parts.count == 3 else { return true }
                return !slots(row, String(parts[1])).contains { $0.key == key }
            }
            let unmatched = recs.filter { $0.day == day && isUnmatched($0) }

            var candidates: [Slot] = []
            var spent = Set<ObjectIdentifier>() // k에 이미 센 짝 없는 기록
            if let last = rows.lastIndex(where: { isChangeDay($0, day: day, series: rows) }) {
                // 바꾼 날: 앞 줄들의 그날 차례 중 기록 있는 것 + 앞 줄들의 짝 없는 기록 = k, 앞 줄 남은 차례는 버림, 새 줄 앞 k개는 넘김.
                let before = rows[..<last].flatMap { slots($0, day) }
                let earlierUnmatched = unmatched.filter { r in
                    rows[..<last].contains { r.slotKey?.hasPrefix(id($0) + "|") == true }
                }
                earlierUnmatched.forEach { spent.insert(ObjectIdentifier($0)) }
                let k = before.filter { handled.contains($0.key) }.count + earlierUnmatched.count
                candidates = Array(slots(rows[last], day).dropFirst(k)) + rows[(last + 1)...].flatMap { slots($0, day) }
            } else {
                candidates = rows.flatMap { slots($0, day) }
            }
            var entries = candidates.sorted { $0.due < $1.due }.map { slot in
                Entry(slot: slot, record: handled.contains(slot.key)
                      ? keyed[slot.key]?.filter { handledStatuses.contains(json($0)["status"] as? String ?? "") }.min { $0.occurredAt < $1.occurredAt }
                      : nil)
            }
            // 짝 없는 기록은 남은 차례를 앞에서부터 하나씩 처리한 것으로.
            var extras = unmatched.filter { !spent.contains(ObjectIdentifier($0)) }.sorted { $0.occurredAt < $1.occurredAt }[...]
            for i in entries.indices where entries[i].record == nil {
                guard let r = extras.popFirst() else { break }
                entries[i] = Entry(slot: entries[i].slot, record: r)
            }
            // 그만 먹인 줄(뒤를 잇는 줄 없이 닫힘)의 안 준 차례는 빼기 — 그만 먹이면 바로 줄·시트에서 사라짐(2026-11-05 사용자).
            let stopped = Set(rows.filter { r in
                guard let until = r.activeUntil else { return false }
                return !series.contains { next in next !== r && next.activeFrom >= until } // 그 뒤에 시작한 줄이 없음
            }.map { ObjectIdentifier($0) })
            result += entries.filter { $0.record != nil || !stopped.contains(ObjectIdentifier($0.slot.schedule)) }
        }
        return result.sorted { a, b in
            (a.slot.due, a.slot.schedule.createdAt, id(a.slot.schedule)) < (b.slot.due, b.slot.schedule.createdAt, id(b.slot.schedule))
        }
    }

    /// 일기 탕약 줄 버튼: 같은 약은 이른 차례 하나만, 최대 3개(8-3 정한 것 ②).
    static func buttons(_ slots: [Slot]) -> [Slot] {
        var seen = Set<UUID>()
        return Array(slots.filter { seen.insert($0.schedule.medicationId).inserted }.prefix(3))
    }

    /// 탕약 줄이 보이는지: 그 아이에게 열린 약 주기가 하나라도(필요할 때만 약만 있어도).
    static func hasOpen(_ schedules: [MedSchedule], petId: UUID) -> Bool {
        schedules.contains { $0.petId == petId && $0.activeUntil == nil }
    }

    /// 버튼 글자: 짧은 이름, 없으면 이름.
    static func shortName(_ med: Medication?) -> String {
        guard let med else { return "" }
        let short = med.shortName?.trimmingCharacters(in: .whitespaces) ?? ""
        return short.isEmpty ? med.name : short
    }

    /// 'HH:mm'(기기 시간대).
    static func hhmm(_ date: Date) -> String {
        let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: date)
        return String(format: "%02d:%02d", c.hour!, c.minute!)
    }

    /// '오전 8:00'.
    static func clockText(_ date: Date) -> String { MedSchedules.clock(hhmm(date)) }

    /// '오후 9시'·'오전 8시 30분'(화면 읽기).
    static func spokenTime(_ date: Date) -> String {
        let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: date)
        let h = c.hour!, m = c.minute!
        return "\(h < 12 ? "오전" : "오후") \(h % 12 == 0 ? 12 : h % 12)시" + (m > 0 ? " \(m)분" : "")
    }

    /// 차례 기록(바로 기록·'줬어요'): `occurredAt` = 지금, `day` = 그 차례의 날, 양·단위는 그 줄.
    @discardableResult
    static func record(_ slot: Slot, status: String = "given", now: Date = .now, in context: ModelContext) throws -> Record {
        try record([(slot, status)], now: now, in: context)[0]
    }

    /// 여러 차례를 한 번에(오늘 먹일 약 시트 '기록하기') — 하나라도 안 되면 아무것도 안 남김.
    @discardableResult
    static func record(_ picks: [(slot: Slot, status: String)], now: Date = .now, in context: ModelContext) throws -> [Record] {
        let records = try picks.map { slot, status in
            let s = slot.schedule
            return Record(now: now, petId: s.petId, kind: .med, occurredAt: now, day: slot.day, slotKey: slot.key,
                          data: try data(amount: s.amount, medicationId: s.medicationId, scheduleId: s.id, status: status, unit: s.unit))
        }
        records.forEach { context.insert($0) }
        try save(context)
        return records
    }

    private static func data(amount: Double, medicationId: UUID, scheduleId: UUID?, status: String, unit: DoseUnit) throws -> String {
        var json: [String: Any] = ["amount": amount, "medicationId": medicationId.uuidString.lowercased(), "status": status, "unit": unit.rawValue]
        json["scheduleId"] = scheduleId?.uuidString.lowercased()
        return String(decoding: try JSONSerialization.data(withJSONObject: json, options: [.sortedKeys]), as: UTF8.self)
    }

    /// 예정에 없던 약(`scheduleId`·`slotKey` 없음) 새로 남기기, 또는 탕약 기록 하나 고치기(약은 그대로 — 차례 기록은 차례의 날도 그대로).
    @discardableResult
    static func saveExtra(petId: UUID, medication: Medication, amount: Double, status: String, occurredAt: Date, editing: Record? = nil,
                          now: Date = .now, in context: ModelContext) throws -> Record {
        if let editing {
            var j = json(editing)
            j["amount"] = amount
            j["status"] = status
            editing.data = String(decoding: try JSONSerialization.data(withJSONObject: j, options: [.sortedKeys]), as: UTF8.self)
            if editing.occurredAt != occurredAt {
                if editing.slotKey == nil { editing.setOccurredAt(occurredAt, now: now) } else { editing.occurredAt = occurredAt }
            }
            editing.updatedAt = now
            try save(context)
            return editing
        }
        let record = Record(now: now, petId: petId, kind: .med, occurredAt: occurredAt,
                            data: try data(amount: amount, medicationId: medication.id, scheduleId: nil, status: status, unit: medication.unit))
        context.insert(record)
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

    /// 예정에 없던 약 고르기 순서(8-3): 이 아이의 필요할 때만 약 → 이 아이가 먹는 약 → 나머지(약방 순), 줄 아래 '물약 · 필요할 때만'.
    static func extraOptions(_ meds: [Medication], schedules: [MedSchedule], pet: Pet) -> [(med: Medication, detail: String)] {
        let open = schedules.filter { $0.petId == pet.id && $0.activeUntil == nil && $0.deletedAt == nil }
        func rank(_ m: Medication) -> Int {
            let mine = open.filter { $0.medicationId == m.id }
            if mine.contains(where: { $0.frequency == .asNeeded }) { return 0 }
            return mine.isEmpty ? 2 : 1
        }
        return meds.filter { $0.deletedAt == nil }.enumerated()
            .sorted { (rank($0.element), $0.offset) < (rank($1.element), $1.offset) }
            .map { _, m in
                let tag = [0: "필요할 때만", 1: "\(MedRules.withSubjectParticle(pet.name)) 먹는 약"][rank(m)]
                return (m, ([MedRules.formTitle(m.form)] + [tag].compactMap { $0 }).joined(separator: " · "))
            }
    }

    /// 시트 약 줄 아래 글자 '가루 · 1포 · 사료에 섞어서'.
    static func slotDetail(_ s: MedSchedule, med: Medication?) -> String {
        ([med.map { MedRules.formTitle($0.form) }, MedSchedules.amountText(s.amount, s.unit), MedSchedules.howToText(s.howTo)].compactMap { $0 })
            .joined(separator: " · ")
    }

    /// 기록 값 글자 '프레드니솔론 1알'·'프레드니솔론 건너뜀'·'프레드니솔론 먹고 토함'(약방에서 뺀 약도 이름은 남음).
    static func value(_ record: Record, meds: [Medication]) -> String {
        let j = json(record)
        let name = (j["medicationId"] as? String).flatMap { id in meds.first { $0.id.uuidString.lowercased() == id }?.name } ?? "약"
        switch j["status"] as? String {
        case "skipped": return "\(name) 건너뜀"
        case "vomited": return "\(name) 먹고 토함"
        default:
            guard let amount = (j["amount"] as? NSNumber)?.doubleValue, let unit = (j["unit"] as? String).flatMap(DoseUnit.init) else { return name }
            return "\(name) \(MedSchedules.amountText(amount, unit))"
        }
    }
}
