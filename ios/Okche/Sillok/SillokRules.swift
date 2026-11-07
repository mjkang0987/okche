import Foundation

/// 실록 화면 규칙(plan.md 10-1, 데이터 3장·6-3·6-4·12장). 안드로이드 `SillokRules.kt`와 같은 규칙. 화면은 `SillokViews.swift`.
enum SillokRules {
    /// 빠른 선택 칩. `days`가 없으면 자물쇠(3달·1년·전체 — 14단계 결제 전엔 안내 창).
    enum Range: String, CaseIterable {
        case week, month, quarter, year, all

        var title: String {
            switch self {
            case .week: "1주"
            case .month: "30일"
            case .quarter: "3달"
            case .year: "1년"
            case .all: "전체"
            }
        }

        var days: Int? {
            switch self {
            case .week: 7
            case .month: 30
            default: nil
            }
        }

        /// '30일과'·'1주와'(받침 따라).
        var withAnd: String { title + (self == .week ? "와" : "과") }
    }

    /// 보는 기간: 6시 기준 날 `start`...`end`(둘 다 포함), `back` = 오늘이 든 기간에서 몇 칸 앞인지(0이면 다음 안 눌림).
    struct Period: Equatable {
        let start: String
        let end: String
        let days: Int
        let back: Int

        var dayList: [String] { (0..<days).map { QuickRecord.shift(start, by: $0) } }
        /// 직전 같은 길이 기간.
        var previous: Period { Period(start: QuickRecord.shift(start, by: -days), end: QuickRecord.shift(start, by: -1), days: days, back: back + 1) }
        func contains(_ day: String) -> Bool { start <= day && day <= end }
    }

    /// 끝 = 오늘(6시 기준) − back×days일, 시작 = 끝 − (days−1)일.
    static func period(today: String, days: Int, back: Int = 0) -> Period {
        let end = QuickRecord.shift(today, by: -(back * days))
        return Period(start: QuickRecord.shift(end, by: -(days - 1)), end: end, days: days, back: back)
    }

    /// '9/26 – 10/25'.
    static func text(_ p: Period) -> String { "\(md(p.start)) – \(md(p.end))" }

    /// 머리 둘째 글: 오늘이 든 기간이면 '최근 30일', 지난 기간이면 '30일'.
    static func headline(_ p: Period, _ range: Range) -> String { p.back == 0 ? "최근 \(range.title)" : range.title }

    /// '10/14'.
    static func md(_ day: String) -> String {
        let p = day.split(separator: "-").compactMap { Int($0) }
        return p.count == 3 ? "\(p[1])/\(p[2])" : day
    }

    /// 그 아이의 기간 안 기록(지운 것 빼고).
    static func records(_ records: [Record], petId: UUID, in p: Period) -> [Record] {
        records.filter { $0.petId == petId && $0.deletedAt == nil && p.contains($0.day) }
    }

    private static func json(_ r: Record) -> [String: Any] {
        (try? JSONSerialization.jsonObject(with: Data(r.data.utf8)) as? [String: Any]) ?? [:]
    }

    private static func string(_ r: Record, _ key: String) -> String? { json(r)[key] as? String }

    /// 구토 n회 = 구토 기록 수.
    static func vomitCount(_ records: [Record]) -> Int { records.filter { $0.kind == RecordKind.vomit.rawValue }.count }

    /// 묽은 변 n회 = 매화 `shape`가 무름·묽음·물 같음.
    static func looseStoolCount(_ records: [Record]) -> Int {
        records.filter { $0.kind == RecordKind.stool.rawValue && ["soft", "loose", "watery"].contains(string($0, "shape") ?? "") }.count
    }

    /// 직전 기간과 비교 줄: 직전 기간에 그 아이 기록이 하나도 없으면 nil.
    static func compare(_ now: Int, _ before: Int, beforeHasRecords: Bool, _ range: Range) -> String? {
        guard beforeHasRecords else { return nil }
        if now < before { return "\(before - now)회 줄었어요" }
        if now > before { return "\(now - before)회 늘었어요" }
        return "직전 \(range.withAnd) 같아요"
    }

    /// 하루 구토 수(기간의 날 순).
    static func dailyVomits(_ records: [Record], _ p: Period) -> [Int] {
        let byDay = Dictionary(grouping: records.filter { $0.kind == RecordKind.vomit.rawValue }, by: \.day).mapValues(\.count)
        return p.dayList.map { byDay[$0] ?? 0 }
    }

    /// 막대 세로 눈금 위 값: 2와 가장 많은 날 중 큰 짝수(가운데 눈금이 정수가 되게).
    static func barTop(_ counts: [Int]) -> Int {
        let top = max(2, counts.max() ?? 0)
        return top % 2 == 0 ? top : top + 1
    }

    /// 다 먹은 끼니 a/b(6-3): 분모 = 기간 각 날의 끼니 차례(그때 계획) 중 아직 안 온 것(`now` 뒤)·휴양 기간 안의 것을 뺀 것,
    /// 분자 = 그 차례 중 같은 `slotKey`에 `all` 기록이 있는 차례 수. 차례가 없으면 nil(칸 숨김).
    static func mealFraction(_ records: [Record], plans: [MealPlan], rests: [RestPeriod], petId: UUID, _ p: Period, now: Date,
                             timeZone: TimeZone = .autoupdatingCurrent) -> (eaten: Int, total: Int)? {
        let petPlans = plans.filter { $0.petId == petId }
        let petRests = rests.filter { $0.petId == petId && $0.deletedAt == nil }
        let slots = p.dayList.flatMap { MealRules.occurrences(petPlans, day: $0, timeZone: timeZone) }
            .filter { s in s.at <= now && !petRests.contains { $0.startedAt <= s.at && ($0.endedAt.map { s.at < $0 } ?? true) } }
        guard !slots.isEmpty else { return nil }
        let eaten = Set(records.filter { $0.kind == RecordKind.meal.rawValue && $0.petId == petId && $0.deletedAt == nil && string($0, "result") == "all" }
            .compactMap(\.slotKey))
        return (eaten: slots.filter { eaten.contains($0.key) }.count, total: slots.count)
    }

    /// 변 상태 칸: 단단 / 정상 / 무름·묽음 / 물 같음.
    enum StoolBand: CaseIterable {
        case hard, normal, soft, watery

        var title: String {
            switch self {
            case .hard: "단단"
            case .normal: "정상"
            case .soft: "무름·묽음"
            case .watery: "물 같음"
            }
        }
    }

    /// 변 상태 비율(%) — 반올림해도 합이 100(나머지가 큰 칸부터 1씩). 모양이 있는 매화 기록이 없으면 nil(칸 숨김).
    static func stoolShares(_ records: [Record]) -> [(band: StoolBand, percent: Int)]? {
        let bands: [StoolBand] = records.filter { $0.kind == RecordKind.stool.rawValue }.compactMap {
            switch string($0, "shape") {
            case "hard": .hard
            case "normal": .normal
            case "soft", "loose": .soft
            case "watery": .watery
            default: nil
            }
        }
        guard !bands.isEmpty else { return nil }
        let raw = StoolBand.allCases.map { b in Double(bands.filter { $0 == b }.count) * 100 / Double(bands.count) }
        var floors = raw.map { Int($0) }
        var left = 100 - floors.reduce(0, +)
        // 같은 나머지면 앞 칸부터(안드로이드 `sortedByDescending`도 같은 값은 순서를 지킴).
        for i in raw.indices.sorted(by: { (raw[$0] - Double(floors[$0]), -$0) > (raw[$1] - Double(floors[$1]), -$1) }) where left > 0 {
            floors[i] += 1
            left -= 1
        }
        return zip(StoolBand.allCases, floors).map { (band: $0, percent: $1) }
    }

    /// 소피 칸: 하루 평균(기록 수 ÷ 기간 날 수), 찔끔·분홍·붉은색·힘만 줌 횟수. 소피 기록이 없으면 nil(칸 숨김).
    struct Urine: Equatable {
        let perDay: Double
        let little: Int
        let pinkRed: Int
        let straining: Int
    }

    static func urine(_ records: [Record], days: Int) -> Urine? {
        let urine = records.filter { $0.kind == RecordKind.urine.rawValue }
        guard !urine.isEmpty else { return nil }
        return Urine(perDay: Double(urine.count) / Double(days),
                     little: urine.filter { string($0, "amount") == "little" }.count,
                     pinkRed: urine.filter { string($0, "color") == "pinkRed" }.count,
                     straining: urine.filter { (json($0)["signs"] as? [String] ?? []).contains("straining") }.count)
    }

    /// 하루 평균 글자 '3.2'(소수 첫째 자리, 기기 지역과 상관없이 '.').
    static func perDayText(_ v: Double) -> String { String(format: "%.1f", locale: Locale(identifier: "en_US_POSIX"), v) }

    /// 체중 점: 기간 안 `weight` 기록, 시각 순 (시각, 그램).
    static func weightPoints(_ records: [Record]) -> [(at: Date, grams: Int)] {
        records.filter { $0.kind == RecordKind.weight.rawValue }
            .compactMap { r -> (at: Date, grams: Int)? in
                guard let grams = (json(r)["grams"] as? NSNumber)?.intValue, grams > 0 else { return nil }
                return (at: r.occurredAt, grams: grams)
            }
            .sorted { $0.at < $1.at }
    }

    /// 체중선 가로 글: 30일은 '1주'~'5주'(보드), 1주는 날짜 7개.
    static func weightAxis(_ p: Period) -> [String] {
        p.days <= 7 ? p.dayList.map(md) : (1...((p.days + 6) / 7)).map { "\($0)주" }
    }

    /// 체중 kg 글자 '4.6'(`kgText`에서 'kg'만 뺌).
    static func kgNumber(_ grams: Int) -> String {
        let text = QuickRecord.kgText(grams: grams)
        return text.hasSuffix("kg") ? String(text.dropLast(2)) : text
    }

    /// 바꾼 날(6-4). `what` = '사료'·'탕약', `text` = 줄 글, `spoken` = 화면 읽기.
    struct Change: Equatable {
        let day: String
        let what: String
        let text: String
        let spoken: String
    }

    /// 이 기간에 바뀐 것(6-4): 사료 = 그 아이 `FoodChange.startedOn`. 약 = 같은 `seriesId` 줄을 `activeFrom` 순으로 —
    /// 첫 줄 `activeFrom`(시작), 앞 줄과 주기·양이 다른 줄의 `activeFrom`(바꿈), 마지막 줄의 `activeUntil`(그만 — 추억으로 끝난 것 빼고).
    /// 날 순, 같은 날이면 사료 먼저.
    static func changes(pet: Pet, foodChanges: [FoodChange], foods: [Food], schedules: [MedSchedule], meds: [Medication], in p: Period,
                        timeZone: TimeZone = .autoupdatingCurrent) -> [Change] {
        let foodName = Dictionary(foods.map { ($0.id, $0.name) }, uniquingKeysWith: { a, _ in a })
        let medName = Dictionary(meds.map { ($0.id, $0.name) }, uniquingKeysWith: { a, _ in a })
        let food = foodChanges.filter { $0.petId == pet.id && $0.deletedAt == nil && p.contains($0.startedOn) }
            .sorted { $0.createdAt < $1.createdAt }
            .map { Change(day: $0.startedOn, what: "사료", text: "\(md($0.startedOn)) 사료 → \(foodName[$0.foodId] ?? "")",
                          spoken: "\(md($0.startedOn)) 사료 바꿈") }
        let series = Dictionary(grouping: schedules.filter { $0.petId == pet.id && $0.deletedAt == nil }, by: \.seriesId).values
        let med: [Change] = series.flatMap { group -> [Change] in
            let rows = group.sorted { ($0.activeFrom, $0.createdAt) < ($1.activeFrom, $1.createdAt) }
            guard let first = rows.first, let last = rows.last else { return [] }
            let name = medName[first.medicationId] ?? ""
            var marks: [(Date, String)] = [(first.activeFrom, "시작")]
            for (prev, row) in zip(rows, rows.dropFirst()) where doseKey(prev) != doseKey(row) { marks.append((row.activeFrom, "바꿈")) }
            if let until = last.activeUntil, until != pet.memorializedAt { marks.append((until, "그만")) }
            return marks.map { mark in
                let day = OkcheDay.of(mark.0, in: timeZone)
                return Change(day: day, what: "탕약", text: "\(md(day)) 탕약 → \(name) · \(mark.1)", spoken: "\(md(day)) 탕약 \(mark.1)")
            }
        }
        .filter { p.contains($0.day) }
        // 같은 날 안에서는 사료 먼저, 그다음 넣은 순서 그대로(안정 정렬 — 안드로이드 `sortedWith`와 같음).
        return (food + med).enumerated()
            .sorted { ($0.element.day, $0.element.what == "사료" ? 0 : 1, $0.offset) < ($1.element.day, $1.element.what == "사료" ? 0 : 1, $1.offset) }
            .map(\.element)
    }

    private static func doseKey(_ s: MedSchedule) -> String {
        [s.frequency.rawValue, s.times.joined(separator: ","), s.everyDays.map(String.init) ?? "", s.weekdays.map(String.init).joined(separator: ","),
         s.everyHours.map(String.init) ?? "", "\(s.amount)", s.unit.rawValue].joined(separator: "|")
    }

    /// 막대의 금색 선: 날 → 이름표('사료', '탕약', 같은 날 둘이면 '사료·탕약').
    static func marks(_ changes: [Change]) -> [String: String] {
        Dictionary(grouping: changes, by: \.day).mapValues { list in
            list.map(\.what).reduce(into: [String]()) { if !$0.contains($1) { $0.append($1) } }.joined(separator: "·")
        }
    }

    /// 그래프 화면 읽기 이름: '구토 횟수 일자별 그래프 · 좌우로 넘겨 보기 · 9/26부터 10/25까지 4번 · 10/14 사료 바꿈, 10/20 탕약 바꿈'.
    static func vomitChartLabel(_ p: Period, total: Int, changes: [Change]) -> String {
        (["구토 횟수 일자별 그래프", "좌우로 넘겨 보기", "\(md(p.start))부터 \(md(p.end))까지 \(total)번"]
            + (changes.isEmpty ? [] : [changes.map(\.spoken).joined(separator: ", ")])).joined(separator: " · ")
    }
}
