import Foundation

/// 탕약 알림 목록 계산(8-4a, 데이터 6-2) — 휴대폰에 걸기 전 순수 계산. 걸기·지우기는 8-4b.
enum MedNotify {
    enum Kind: String { case due, again, more, low }

    struct Alert: Equatable {
        let id: String
        let kind: Kind
        let fireAt: Date
        let title: String
        let body: String
        /// 누르면 열 아이(묶이면 첫 아이). 이어짐 알림은 없음.
        let petId: UUID?
        /// 남은 약 알림: 누르면 약방의 그 약 고치기.
        var medId: UUID?
        /// 남은 약 알림의 보낸 기록 열쇠(`{약 id}|{stockCountedAt}`).
        var sentKey: String?
    }

    /// 아이폰 걸어 둘 수 있는 알림 64개 — 이어짐 알림 하나를 남긴다.
    static let limit = 63
    static let window: TimeInterval = 72 * 3600

    /// 오늘(6시 기준)부터 3일 뒤까지 남은 차례 → 울릴 시각마다 하나로 묶은 알림, 이른 것부터 63개 + 이어짐 알림.
    static func alerts(_ schedules: [MedSchedule], records: [Record], pets: [Pet], rests: [RestPeriod], meds: [Medication],
                       medTimeOn: Bool, limit: Int = limit, now: Date = .now) -> [Alert] {
        guard medTimeOn else { return [] }
        let live = schedules.filter { $0.deletedAt == nil }
        let medById = Dictionary(meds.filter { $0.deletedAt == nil }.map { ($0.id, $0) }, uniquingKeysWith: { a, _ in a })
        // 넷째 날 새벽 차례도 몇 분 전이면 72시간 안에 울릴 수 있어 하루 더 본다(창 밖은 아래에서 거름).
        let days = (0...4).compactMap { Calendar.current.date(byAdding: .day, value: $0, to: now) }.map { OkcheDay.of($0) }
        struct Entry { let kind: Kind; let fireAt: Date; let due: Date; let pet: Pet; let med: Medication; let schedule: MedSchedule }
        var entries: [Entry] = []
        var seen = Set<String>() // 몇 시간마다 줄이 날 경계에서 두 번 잡히지 않게
        for pet in pets where pet.deletedAt == nil && pet.memorializedAt == nil {
            let petRests = rests.filter { $0.petId == pet.id && $0.deletedAt == nil }
            for day in Set(days) {
                for slot in MedSlots.remaining(live, records: records, petId: pet.id, day: day) where seen.insert(slot.key).inserted {
                    let s = slot.schedule
                    guard s.reminder, s.frequency != .asNeeded, let med = medById[s.medicationId] else { continue }
                    // 휴양 중인 아이는 알림 멈춤(2-1).
                    if petRests.contains(where: { $0.startedAt <= slot.due && ($0.endedAt.map { slot.due < $0 } ?? true) }) { continue }
                    // 울릴 시각은 분 단위로 내림 — 몇 시간마다 '준 시각부터'는 초가 붙어 같은 분이 따로 묶이고 id가 겹칠 수 있음.
                    // 창은 내리기 전 시각으로 견줌(같은 분 안에 다시 걸어도 빠지지 않게).
                    var fires = [(Kind.due, slot.due.addingTimeInterval(-TimeInterval(s.leadMinutes * 60)))]
                    if s.remindAgainMinutes > 0 { fires.append((.again, slot.due.addingTimeInterval(TimeInterval(s.remindAgainMinutes * 60)))) }
                    for (kind, fireAt) in fires where fireAt > now && fireAt <= now.addingTimeInterval(window) {
                        entries.append(Entry(kind: kind, fireAt: minute(fireAt), due: slot.due, pet: pet, med: med, schedule: s))
                    }
                }
            }
        }
        let groups = Dictionary(grouping: entries) { "\($0.kind.rawValue)|\($0.fireAt.timeIntervalSince1970)" }.values
        var alerts: [Alert] = groups.compactMap { group in
            let sorted = group.sorted { ($0.pet.createdAt, $0.med.name) < ($1.pet.createdAt, $1.med.name) }
            guard let first = sorted.first else { return nil }
            let words = sorted.map { e in
                first.kind == .due ? "\(e.pet.name) \(e.med.name) \(MedSchedules.amountText(e.schedule.amount, e.schedule.unit))" : "\(e.pet.name) \(e.med.name)"
            }
            let body = words.count > 3 ? words.prefix(3).joined(separator: " · ") + " 외 \(words.count - 3)" : words.joined(separator: " · ")
            let title = first.kind == .due ? "탕약 · \(MedSlots.clockText(sorted.map(\.due).min() ?? first.due))" : "아직 안 준 약이 있어요"
            return Alert(id: "med.\(first.kind.rawValue).\(Int(first.fireAt.timeIntervalSince1970))", kind: first.kind, fireAt: first.fireAt,
                         title: title, body: body, petId: first.pet.id)
        }
        alerts.sort { ($0.fireAt, $0.kind == .due ? 0 : 1) < ($1.fireAt, $1.kind == .due ? 0 : 1) }
        alerts = Array(alerts.prefix(max(limit, 0)))
        if let last = alerts.last {
            alerts.append(Alert(id: "med.more", kind: .more, fireAt: last.fireAt.addingTimeInterval(60), title: "옥체", body: "옥체를 열면 알림이 이어져요", petId: nil))
        }
        return alerts
    }

    /// 남은 약 알림(8-4c): 약방 '○일분'이 기준 이하가 되는 날 오전 10시에 한 번 '약방 · 프레드니솔론이 3일분 남았어요'.
    /// 이미 기준 이하면 오늘 10시(지났으면 내일). `sent`(`{약 id}|{stockCountedAt}`)에 있으면 남은 양을 다시 적기 전까지 안 보냄.
    static func lowMedAlerts(meds: [Medication], schedules: [MedSchedule], records: [Record], on: Bool, days threshold: Int,
                             quiet: QuietHours?, sent: Set<String>, now: Date = .now) -> [Alert] {
        guard on else { return [] }
        let calendar = Calendar.current
        let live = schedules.filter { $0.deletedAt == nil }
        // 밤 알림 쉬기 시간에 걸리면 보내지 않음(10시라 보통 겹치지 않음).
        if let quiet, quiet.enabled, inQuiet("10:00", quiet) { return [] }
        return meds.filter { $0.deletedAt == nil }.compactMap { med in
            guard let left = MedSchedules.daysLeft(med, schedules: live, records: records), !sent.contains(sentKey(med)) else { return nil }
            let ahead = max(left - threshold, 0)
            guard let day = calendar.date(byAdding: .day, value: ahead, to: now),
                  var fireAt = calendar.date(bySettingHour: 10, minute: 0, second: 0, of: day) else { return nil }
            if fireAt <= now, let next = calendar.date(byAdding: .day, value: 1, to: fireAt) { fireAt = next }
            guard fireAt <= now.addingTimeInterval(window) else { return nil }
            // 울리는 날에 남을 일수(하루에 하루분씩 줄어듦) — 10시가 지나 내일로 밀리면 하루 적게.
            let days = left - (calendar.dateComponents([.day], from: calendar.startOfDay(for: now), to: calendar.startOfDay(for: fireAt)).day ?? 0)
            return Alert(id: "low.\(med.id.uuidString.lowercased())", kind: .low, fireAt: fireAt, title: "약방",
                         body: lowMedBody(med.name, days: days), petId: nil, medId: med.id, sentKey: sentKey(med))
        }
        .prefix(limit).map { $0 }
    }

    /// '프레드니솔론이 3일분 남았어요', 하루분도 안 남으면 '프레드니솔론이 거의 다 떨어졌어요'.
    static func lowMedBody(_ name: String, days: Int) -> String {
        days > 0 ? "\(MedRules.withSubjectParticle(name)) \(days)일분 남았어요" : "\(MedRules.withSubjectParticle(name)) 거의 다 떨어졌어요"
    }

    /// 알림 켠 약(열린 줄, 필요할 때만 아님)이 하나라도 있는지 — 꺼짐 띠를 띄울 때.
    static func wantsReminders(_ schedules: [MedSchedule]) -> Bool {
        schedules.contains { $0.deletedAt == nil && $0.activeUntil == nil && $0.reminder && $0.frequency != .asNeeded }
    }

    static func sentKey(_ med: Medication) -> String {
        "\(med.id.uuidString.lowercased())|\(med.stockCountedAt.map { String(Int($0.timeIntervalSince1970)) } ?? "")"
    }

    /// `HH:mm`이 쉬는 시간 안인지(자정을 넘기는 시간 포함, 끝은 빼고).
    static func inQuiet(_ time: String, _ quiet: QuietHours) -> Bool {
        quiet.start <= quiet.end ? (quiet.start <= time && time < quiet.end) : (time >= quiet.start || time < quiet.end)
    }

    private static func minute(_ date: Date) -> Date {
        Date(timeIntervalSince1970: (date.timeIntervalSince1970 / 60).rounded(.down) * 60)
    }
}
