import Foundation
import SwiftData

/// 약 주기·탕약 규칙(plan.md 8-2a, 데이터 5장·6-1). 안드로이드 `MedSchedules.kt`와 같은 규칙.
enum MedSchedules {
    static let frequencies: [(value: MedFrequency, title: String)] = [
        (.daily, "매일"), (.everyNDays, "며칠마다"), (.weekdays, "요일 정해서"), (.everyNHours, "몇 시간마다"), (.asNeeded, "필요할 때만"),
    ]
    static let howTos: [(value: HowTo, title: String)] = [(.plain, "그냥 먹여요"), (.inFood, "사료에 섞어요"), (.inTreat, "간식에 숨겨요"), (.syringe, "주사기로 먹여요")]
    static let leadChoices: [(minutes: Int, title: String)] = [(0, "먹일 시간에"), (5, "5분 전"), (10, "10분 전"), (15, "15분 전"), (30, "30분 전"), (60, "1시간 전")]
    /// ISO 요일(월=1) 글자.
    static let weekdayNames = ["월", "화", "수", "목", "금", "토", "일"]

    // MARK: 글자

    /// 한 번에 양 '1알'·'반 포'(0.5알·0.5포는 '반').
    static func amountText(_ amount: Double, _ unit: DoseUnit) -> String {
        if amount == 0.5, unit == .pill || unit == .pack { return "반 \(MedRules.unitTitle(unit))" }
        return "\(MedRules.number(amount))\(MedRules.unitTitle(unit))"
    }

    /// 주기 글자 '하루 2번'·'이틀에 한 번'·'월·목'·'8시간마다'·'필요할 때만'.
    static func frequencyText(_ s: MedSchedule) -> String {
        switch s.frequency {
        case .daily: "하루 \(s.times.count)번"
        case .everyNDays: everyDaysText(s.everyDays ?? 2)
        case .weekdays: s.weekdays.sorted().map { weekdayNames[($0 - 1 + 7) % 7] }.joined(separator: "·")
        case .everyNHours: "\(s.everyHours ?? 1)시간마다"
        case .asNeeded: "필요할 때만"
        }
    }

    static func everyDaysText(_ n: Int) -> String {
        switch n {
        case 2: "이틀에 한 번"
        case 3: "사흘에 한 번"
        case 7: "일주일에 한 번"
        default: "\(n)일에 한 번"
        }
    }

    /// 카드에 붙는 먹이는 방법('그냥 먹여요'는 안 붙임).
    static func howToText(_ howTo: HowTo?) -> String? {
        switch howTo {
        case .inFood: "사료에 섞어서"
        case .inTreat: "간식에 숨겨서"
        case .syringe: "주사기로"
        default: nil
        }
    }

    /// 카드 아래 글자 '콩이 · 이틀에 한 번 · 1알 · 사료에 섞어서'.
    static func cardDetail(_ s: MedSchedule, petName: String) -> String {
        ([petName, frequencyText(s), amountText(s.amount, s.unit)] + [howToText(s.howTo)].compactMap { $0 }).joined(separator: " · ")
    }

    /// '오전 8:00'.
    static func clock(_ time: String) -> String {
        let p = time.split(separator: ":").compactMap { Int($0) }
        guard p.count == 2 else { return time }
        return "\(p[0] < 12 ? "오전" : "오후") \(p[0] % 12 == 0 ? 12 : p[0] % 12):\(String(format: "%02d", p[1]))"
    }

    // MARK: 차례(6-1)

    private static var calendar: Calendar { .okcheDevice }

    /// 그날(6시 기준 `day`)이 먹는 날인지.
    static func isDoseDay(_ s: MedSchedule, day: String) -> Bool {
        switch s.frequency {
        case .daily: return true
        case .everyNDays:
            guard let start = s.startDate, let n = s.everyDays, n > 0, day >= start,
                  let diff = FoodRules.dayNumber(startedOn: start, today: day) else { return false }
            return (diff - 1) % n == 0
        case .weekdays:
            guard let date = PetRegistration.date(day, calendar) else { return false }
            let iso = (calendar.component(.weekday, from: date) + 5) % 7 + 1 // 일=1 → ISO 월=1
            return s.weekdays.contains(iso)
        case .everyNHours, .asNeeded: return false
        }
    }

    /// `{day}`의 `HH:mm` 순간 — 06:00 전이면 다음 날(1-2).
    static func occurrence(day: String, time: String) -> Date? {
        let p = time.split(separator: ":").compactMap { Int($0) }
        guard p.count == 2, let base = PetRegistration.date(day, calendar) else { return nil }
        let date = p[0] < OkcheDay.startHour ? calendar.date(byAdding: .day, value: 1, to: base)! : base
        return calendar.date(bySettingHour: p[0], minute: p[1], second: 0, of: date)
    }

    /// 다음 먹일 때(지금 이후 첫 차례). 필요할 때만이면 nil.
    // shortcut: 몇 시간마다 '준 시각부터 다시 세기'는 기록이 생기는 8-3에서 — 지금은 `firstAt`부터 간격대로.
    static func nextDue(_ s: MedSchedule, now: Date = .now) -> Date? {
        switch s.frequency {
        case .asNeeded: return nil
        case .everyNHours:
            guard let first = s.firstAt, let h = s.everyHours, h > 0 else { return nil }
            let step = TimeInterval(h * 3600)
            let start = max(now, s.activeFrom)
            if first >= start { return first }
            return first.addingTimeInterval(ceil(start.timeIntervalSince(first) / step) * step)
        default:
            var day = OkcheDay.of(max(now, s.activeFrom))
            for _ in 0..<400 {
                if isDoseDay(s, day: day) {
                    let hit = s.times.sorted().compactMap { occurrence(day: day, time: $0) }
                        .first { $0 >= now && $0 >= s.activeFrom }
                    if let hit { return hit }
                }
                day = QuickRecord.shift(day, by: 1)
            }
            return nil
        }
    }

    /// 며칠마다·요일 카드 옆 글자 '오늘 먹는 날 · 다음 10/29' / '다음 10/29'.
    static func doseDayText(_ s: MedSchedule, today: String) -> String? {
        guard s.frequency == .everyNDays || s.frequency == .weekdays else { return nil }
        var day = QuickRecord.shift(today, by: 1)
        var next: String?
        for _ in 0..<400 where next == nil {
            if isDoseDay(s, day: day) { next = day }
            day = QuickRecord.shift(day, by: 1)
        }
        let nextText = next.map { d in d.split(separator: "-").compactMap { Int($0) }.dropFirst().map(String.init).joined(separator: "/") }
            .map { "다음 \($0)" }
        return ([isDoseDay(s, day: today) ? "오늘 먹는 날" : nil, nextText].compactMap { $0 }).joined(separator: " · ")
    }

    /// '이렇게 돌아가요' — 처음 먹일 때부터 간격대로 몇 번.
    static func hoursPreview(firstAt: Date, everyHours: Int, count: Int = 4) -> [String] {
        let f = DateFormatter()
        f.locale = Locale(identifier: "ko_KR")
        f.dateFormat = "M/d (E) a h:mm"
        return (0..<count).map { f.string(from: firstAt.addingTimeInterval(TimeInterval($0 * everyHours * 3600))) }
    }

    // MARK: 탕약 카드

    /// 쓰는 줄: 열린 줄(지우지 않음), `seriesId`마다 하나(늦은 `activeFrom`, 같으면 늦은 `updatedAt` — 5장).
    static func openSeries(_ schedules: [MedSchedule]) -> [MedSchedule] {
        Dictionary(grouping: schedules.filter { $0.deletedAt == nil && $0.activeUntil == nil }, by: \.seriesId).values
            .compactMap { $0.max { ($0.activeFrom, $0.updatedAt) < ($1.activeFrom, $1.updatedAt) } }
    }

    /// 먹고 있는 약 카드: 추억·지운 아이 빼고, 다음 먹일 시간 순(같으면 아이 모신 순), 필요할 때만은 맨 뒤.
    static func cards(_ schedules: [MedSchedule], pets: [Pet], now: Date = .now) -> [(schedule: MedSchedule, pet: Pet)] {
        let alive = Dictionary(uniqueKeysWithValues: pets.filter { $0.deletedAt == nil && $0.memorializedAt == nil }.map { ($0.id, $0) })
        return openSeries(schedules).compactMap { s in alive[s.petId].map { (s, $0) } }
            .map { ($0.0, $0.1, nextDue($0.0, now: now)) }
            .sorted { a, b in
                switch (a.2, b.2) {
                case let (x?, y?) where x != y: x < y
                case (nil, _?): false
                case (_?, nil): true
                default: (a.1.createdAt, a.0.createdAt, a.0.id.uuidString) < (b.1.createdAt, b.0.createdAt, b.0.id.uuidString) // 늘 같은 순서로
                }
            }
            .map { ($0.0, $0.1) }
    }

    /// 하루 양(일분 셈): 매일 = 시간 수 × 양, 며칠마다 ÷ 간격, 요일 × 요일 수 ÷ 7, 몇 시간마다 × 24 ÷ 간격, 필요할 때만 0.
    static func dailyAmount(_ s: MedSchedule) -> Double {
        let perDay = Double(s.times.count) * s.amount
        switch s.frequency {
        case .daily: return perDay
        case .everyNDays: return perDay / Double(max(s.everyDays ?? 1, 1))
        case .weekdays: return perDay * Double(s.weekdays.count) / 7
        case .everyNHours: return s.amount * 24 / Double(max(s.everyHours ?? 1, 1))
        case .asNeeded: return 0
        }
    }

    /// 남은 약 ○일분 = 남은 양 ÷ 그 약의 열린 줄들 하루 양 합(같은 단위만), 내림. 하루 양이 0이거나 남은 양을 안 적었으면 nil.
    static func daysLeft(_ med: Medication, schedules: [MedSchedule], records: [Record]) -> Int? {
        guard let left = MedRules.remaining(med, records: records) else { return nil }
        let perDay = openSeries(schedules).filter { $0.medicationId == med.id && $0.unit == med.unit }.reduce(0) { $0 + dailyAmount($1) }
        guard perDay > 0 else { return nil }
        return Int((max(left, 0) / perDay).rounded(.down))
    }

    /// 카드 약방 줄 '남은 약 48일분'·'남은 24알'·'남은 양 안 적음'.
    static func stockText(_ med: Medication, schedules: [MedSchedule], records: [Record]) -> String {
        if let days = daysLeft(med, schedules: schedules, records: records) { return "남은 약 \(days)일분" }
        guard let left = MedRules.remaining(med, records: records) else { return "남은 양 안 적음" }
        return "남은 \(MedRules.number(max(left, 0)))\(MedRules.unitTitle(med.unit))"
    }

    // MARK: 약 추가

    enum Field: Hashable { case pets, med, dose, everyDays, weekdays, everyHours, times }

    struct Form: Equatable {
        var petIds: [UUID] = []
        var medicationId: UUID?
        var dose = "1"
        var frequency = MedFrequency.daily
        var everyDays = "2"
        var startDate = Date.now
        var weekdays: [Int] = []
        var times = ["08:00"]
        var everyHours = "8"
        var firstAt = Date.now
        var shiftOnLate = true
        var reminder = true
        var leadMinutes = 10
        var howTo = HowTo.plain
    }

    /// 처음 먹일 때 처음 값: 지금을 분 단위로 올림.
    static func roundedUp(_ date: Date) -> Date {
        let seconds = date.timeIntervalSince1970
        return Date(timeIntervalSince1970: (seconds / 60).rounded(.up) * 60)
    }

    static func errors(_ f: Form) -> [Field: String] {
        var e: [Field: String] = [:]
        if f.petIds.isEmpty { e[.pets] = "먹을 아이를 골라 주세요" }
        if f.medicationId == nil { e[.med] = "약을 골라 주세요" }
        let dose = f.dose.trimmingCharacters(in: .whitespaces)
        if PetRegistration.number(dose).map({ $0 > 0 }) != true { e[.dose] = dose.isEmpty ? "한 번에 줄 양을 넣어 주세요" : "0보다 큰 숫자로 넣어 주세요" }
        switch f.frequency {
        case .everyNDays:
            if (PetRegistration.wholeNumber(f.everyDays.trimmingCharacters(in: .whitespaces)) ?? 0) < 2 { e[.everyDays] = "2 이상 숫자로 넣어 주세요" }
        case .weekdays:
            if f.weekdays.isEmpty { e[.weekdays] = "요일을 하나 이상 골라 주세요" }
        case .everyNHours:
            if (PetRegistration.wholeNumber(f.everyHours.trimmingCharacters(in: .whitespaces)) ?? 0) < 1 { e[.everyHours] = "1 이상 숫자로 넣어 주세요" }
        default: break
        }
        if [.daily, .everyNDays, .weekdays].contains(f.frequency), f.times.isEmpty { e[.times] = "시간을 하나 이상 넣어 주세요" }
        return e
    }

    /// 저장: 고른 아이마다 한 줄(같은 설정·같은 `activeFrom`, `seriesId` = 자기 `id`). 같은 약이 이미 열려 있어도 막지 않음.
    @discardableResult
    static func add(_ f: Form, unit: DoseUnit, now: Date = .now, in context: ModelContext) throws -> [MedSchedule] {
        let made = rows(f, unit: unit, now: now)
        made.forEach(context.insert)
        try save(context)
        return made
    }

    /// 고른 아이마다 새 줄(넣지는 않음).
    private static func rows(_ f: Form, unit: DoseUnit, now: Date) -> [MedSchedule] {
        guard let medId = f.medicationId else { return [] }
        let usesTimes = [.daily, .everyNDays, .weekdays].contains(f.frequency)
        let hours = f.frequency == .everyNHours
        let made = f.petIds.map { petId in
            MedSchedule(now: now, petId: petId, medicationId: medId, amount: PetRegistration.number(f.dose.trimmingCharacters(in: .whitespaces)) ?? 1,
                        unit: unit, frequency: f.frequency, times: usesTimes ? Array(Set(f.times)).sorted() : [],
                        everyDays: f.frequency == .everyNDays ? PetRegistration.wholeNumber(f.everyDays.trimmingCharacters(in: .whitespaces)) : nil,
                        // 고른 날은 달력 날짜(0시) — 6시 기준으로 바꾸면 하루 앞이 됨.
                        startDate: f.frequency == .everyNDays ? PetRegistration.day(f.startDate, .okcheDevice) : nil,
                        weekdays: f.frequency == .weekdays ? f.weekdays.sorted() : [],
                        everyHours: hours ? PetRegistration.wholeNumber(f.everyHours.trimmingCharacters(in: .whitespaces)) : nil,
                        firstAt: hours ? f.firstAt : nil, shiftOnLate: f.shiftOnLate, howTo: f.howTo,
                        reminder: f.frequency == .asNeeded ? false : f.reminder, leadMinutes: f.leadMinutes, activeFrom: now)
        }
        return made
    }

    // MARK: 고치기·그만 먹이기(8-2b)

    /// 고치기 처음 값: 그 줄의 지금 값. 몇 시간마다가 아니면 '처음 먹일 때'는 미리 채운 값.
    static func form(_ s: MedSchedule, firstAt: Date) -> Form {
        var f = Form()
        f.petIds = [s.petId]
        f.medicationId = s.medicationId
        f.dose = MedRules.number(s.amount)
        f.frequency = s.frequency
        f.everyDays = String(s.everyDays ?? 2)
        f.startDate = s.startDate.flatMap { PetRegistration.date($0, .okcheDevice) } ?? f.startDate
        f.weekdays = s.weekdays.sorted()
        f.times = s.times.isEmpty ? ["08:00"] : s.times.sorted()
        f.everyHours = String(s.everyHours ?? 8)
        f.firstAt = firstAt
        f.shiftOnLate = s.shiftOnLate
        f.reminder = s.reminder
        f.leadMinutes = s.leadMinutes
        f.howTo = s.howTo ?? .plain
        return f
    }

    /// '처음 먹일 때' 미리 채우는 값(6-1): 새 줄이 '준 시각부터'이거나 옛 줄이 시간 주기면 마지막 준 기록 + 새 간격(없으면 옛 줄 다음 차례),
    /// '처음 정한 시간대로'면 옛 `firstAt` + m × 새 간격 중 지금 이후 첫 시각. 지금보다 이르면 지금(분 올림).
    static func firstAtPrefill(_ old: MedSchedule, everyHours: Int, shiftOnLate: Bool, series: [MedSchedule], records: [Record], now: Date = .now) -> Date {
        let step = TimeInterval(max(everyHours, 1) * 3600)
        var value: Date?
        if shiftOnLate || old.frequency != .everyNHours {
            let ids = Set(series.filter { $0.seriesId == old.seriesId }.map { $0.id.uuidString.lowercased() })
            let last = records.filter { r in
                guard r.kind == RecordKind.med.rawValue, r.deletedAt == nil,
                      let json = try? JSONSerialization.jsonObject(with: Data(r.data.utf8)) as? [String: Any],
                      let id = json["scheduleId"] as? String, ids.contains(id) else { return false }
                return ["given", "vomited"].contains(json["status"] as? String ?? "")
            }.map(\.occurredAt).max()
            value = last.map { $0.addingTimeInterval(step) } ?? nextDue(old, now: now)
        } else if let first = old.firstAt {
            value = first >= now ? first : first.addingTimeInterval(ceil(now.timeIntervalSince(first) / step) * step)
        }
        guard let value, value >= now else { return roundedUp(now) }
        return value
    }

    /// 줄의 셈에 드는 칸(바뀌면 새 줄 — 5장). 주기마다 쓰는 칸만.
    private struct Core: Equatable {
        var frequency: MedFrequency
        var amount: Double?
        var times: [String] = []
        var everyDays: Int?
        var startDate: String?
        var weekdays: [Int] = []
        var everyHours: Int?
        var firstAt: Date?
        var shiftOnLate: Bool?
    }

    private static func core(_ f: Form) -> Core {
        var c = Core(frequency: f.frequency, amount: PetRegistration.number(f.dose.trimmingCharacters(in: .whitespaces)))
        switch f.frequency {
        case .daily: c.times = Array(Set(f.times)).sorted()
        case .everyNDays:
            c.times = Array(Set(f.times)).sorted()
            c.everyDays = PetRegistration.wholeNumber(f.everyDays.trimmingCharacters(in: .whitespaces))
            c.startDate = PetRegistration.day(f.startDate, .okcheDevice)
        case .weekdays:
            c.times = Array(Set(f.times)).sorted()
            c.weekdays = f.weekdays.sorted()
        case .everyNHours:
            c.everyHours = PetRegistration.wholeNumber(f.everyHours.trimmingCharacters(in: .whitespaces))
            c.firstAt = f.firstAt
            c.shiftOnLate = f.shiftOnLate
        case .asNeeded: break
        }
        return c
    }

    enum ChangeResult: Equatable { case none, sameRow, newRow }

    /// 고치기 저장(5장): 횟수·시간·양 → 그 줄을 닫고 같은 `seriesId`로 새 줄, 알림·몇 분 전·먹이는 방법만 → 그 줄을 고침, 안 바꿨으면 그대로.
    @discardableResult
    static func change(_ s: MedSchedule, from original: Form, to f: Form, now: Date = .now, in context: ModelContext) throws -> ChangeResult {
        guard f != original else { return .none }
        if core(f) != core(original) {
            s.activeUntil = now
            s.updatedAt = now
            var next = f
            next.petIds = [s.petId]
            next.medicationId = s.medicationId
            if next.frequency == .everyNHours, next.firstAt < now { next.firstAt = roundedUp(now) } // 지금보다 이르면 지금
            // 닫기와 새 줄을 한 번에 저장(반쯤 바뀐 채 남지 않게).
            for row in rows(next, unit: s.unit, now: now) {
                row.seriesId = s.seriesId
                context.insert(row)
            }
            try save(context)
            return .newRow
        }
        s.reminder = f.frequency == .asNeeded ? false : f.reminder
        s.leadMinutes = f.leadMinutes
        s.howTo = f.howTo
        s.updatedAt = now
        try save(context)
        return .sameRow
    }

    /// 이 약 그만 먹이기: 그 줄만 닫음(다른 아이가 먹는 같은 약·약방은 그대로).
    static func stop(_ s: MedSchedule, now: Date = .now, in context: ModelContext) throws {
        s.activeUntil = now
        s.updatedAt = now
        try save(context)
    }

    /// 알림 스위치(바로 저장 — 그 줄을 고침, 5장).
    static func setReminder(_ s: MedSchedule, _ on: Bool, now: Date = .now, in context: ModelContext) throws {
        s.reminder = on
        s.updatedAt = now
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
