import Foundation
import SwiftData

/// 일기 바로 기록·방금 남긴 기록·기록 목록 규칙(plan.md 7-1, 데이터 3장). 안드로이드 `QuickRecord.kt`와 같은 규칙.
enum QuickRecord {
    /// 바로 기록 줄 하나: 종류, `data`의 칸 이름, (값, 버튼 글자) 셋. 구토는 `contents`가 여러 개라 배열로 저장.
    struct Row {
        let kind: RecordKind
        let key: String
        let choices: [(value: String, title: String)]
    }

    static let rows: [Row] = [
        Row(kind: .vomit, key: "contents", choices: [("kibble", "사료"), ("digested", "불은 사료"), ("liquidFoam", "거품")]),
        Row(kind: .stool, key: "shape", choices: [("hard", "단단"), ("normal", "정상"), ("soft", "무름")]),
        Row(kind: .urine, key: "amount", choices: [("little", "찔끔"), ("normal", "보통"), ("much", "많음")]),
        Row(kind: .meal, key: "result", choices: MealRules.results),
    ]

    /// 종류 이름(화면 글자).
    static func name(_ kind: String) -> String {
        ["vomit": "구토", "stool": "매화", "urine": "소피", "meal": "수라", "snack": "다과", "weight": "옥체",
         "med": "탕약", "glucose": "혈당", "insulin": "인슐린"][kind] ?? kind
    }

    /// 수라 결과 글자.
    private static let mealTitles = Dictionary(uniqueKeysWithValues: MealRules.results.map { ($0.value, $0.title) })

    /// 체중 글자: 그램 → kg 소수 둘째 자리(반올림, 0은 뺌 — 안드로이드 `kgText`와 같음, 기기 지역과 상관없이 '.').
    static func kgText(grams: Int) -> String {
        guard grams >= 0 else { return "" } // 음수는 보여 주지 않음(안드로이드와 같음)
        var exact = Decimal(grams) / 1000
        var rounded = Decimal()
        NSDecimalRound(&rounded, &exact, 2, .plain)
        return "\(rounded)kg"
    }

    /// 기록 줄 값 글자: 구토·매화·소피는 자세히 시트 규칙(`RecordDetail.line`), 수라는 '다 먹음', 체중은 kg. 모르는 값이면 빈 글자.
    static func value(_ record: Record) -> String {
        if let line = RecordDetail.line(record) { return line }
        if record.kind == RecordKind.weight.rawValue {
            let grams = (try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any])?["grams"] as? Int
            return grams.map(kgText) ?? ""
        }
        guard record.kind == RecordKind.meal.rawValue,
              let json = try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any],
              let result = json["result"] as? String else { return "" }
        return mealTitles[result] ?? ""
    }

    /// 바로 기록: 그때 시각·6시 기준 `day`로 바로 저장. `petId`가 없으면 누군지 모름.
    @discardableResult
    static func save(_ row: Row, value: String, petId: UUID?, now: Date = .now, timeZone: TimeZone = .autoupdatingCurrent,
                     in context: ModelContext) throws -> Record {
        // 수라는 끼니가 있으면 지금 끼니 차례로(그 차례에 이미 있으면 추가 식사 — 7-3).
        if row.kind == .meal {
            let slot = try MealRules.quickSlot(petId: petId, now: now, timeZone: timeZone, in: context)
            let record = Record(id: try slot.map { try MealRules.newRecordId(for: $0, in: context) } ?? UUID(),
                                now: now, petId: petId, kind: .meal, occurredAt: now, timeZone: timeZone, slotKey: slot?.key,
                                data: MealRules.data(result: value, foodId: try MealRules.foodId(petId, now, timeZone, in: context), slotName: slot?.slot.name, givenGrams: slot?.slot.grams, leftGrams: nil))
            context.insert(record)
            try save(context)
            return record
        }
        let payload: Any = row.kind == .vomit ? [value] : value
        let data = try JSONSerialization.data(withJSONObject: [row.key: payload], options: [.sortedKeys])
        let record = Record(now: now, petId: petId, kind: row.kind, occurredAt: now, timeZone: timeZone,
                            data: String(decoding: data, as: UTF8.self))
        context.insert(record)
        try save(context)
        return record
    }

    /// 방금 남긴 기록: 오늘(6시 기준) 이 기기에서 가장 늦게 남긴 지우지 않은 기록.
    static func latest(_ records: [Record], today: String) -> Record? {
        // 급식기 자동 기록은 이 기기에서 '남긴' 기록이 아니라 뺀다.
        records.filter { $0.day == today && $0.deletedAt == nil && !$0.auto }.max { $0.createdAt < $1.createdAt }
    }

    /// 그날 기록(시간 순, 늦은 것 위). `petId`가 있으면 그 아이 것만.
    static func records(_ all: [Record], day: String, petId: UUID?) -> [Record] {
        all.filter { $0.day == day && $0.deletedAt == nil && (petId == nil || $0.petId == petId) }
            .sorted { $0.occurredAt > $1.occurredAt }
    }

    /// 그날 요약: '구토 1번 · 매화 2번 · 소피 1번 · 수라 2번'(0번은 빼고, 없으면 nil).
    static func summary(_ records: [Record]) -> String? {
        let parts = rows.compactMap { row -> String? in
            let n = records.filter { $0.kind == row.kind.rawValue }.count
            return n > 0 ? "\(name(row.kind.rawValue)) \(n)번" : nil
        }
        return parts.isEmpty ? nil : parts.joined(separator: " · ")
    }

    /// 누구였는지 정하기.
    static func assign(_ record: Record, to petId: UUID?, now: Date = .now, in context: ModelContext) throws {
        guard record.petId != petId else { return }
        // 수라는 정한 아이가 그날 먹던 사료로(7-4a). 던질 수 있는 일을 먼저 끝낸 뒤 한꺼번에 바꾼다.
        var data = record.data
        if record.kind == RecordKind.meal.rawValue {
            var json = MealRules.json(record)
            json["foodId"] = try FoodRules.foodId(petId: petId, day: record.day, in: context)?.uuidString.lowercased()
            data = String(decoding: try JSONSerialization.data(withJSONObject: json, options: [.sortedKeys]), as: UTF8.self)
        }
        record.petId = petId
        record.updatedAt = now
        record.data = data
        try save(context)
    }

    /// 기록 지우기: `deletedAt`·`updatedAt`, '되돌릴 수 없어요'라서 선택 칸을 비움 — `data` = `{}`, `slotKey`(데이터 1-4).
    static func delete(_ record: Record, now: Date = .now, in context: ModelContext) throws {
        record.deletedAt = now
        record.updatedAt = now
        record.data = "{}"
        record.slotKey = nil
        try save(context)
    }

    /// `YYYY-MM-DD`를 하루 옮긴 날.
    static func shift(_ day: String, by days: Int) -> String {
        let calendar = Calendar(identifier: .gregorian)
        guard let date = PetRegistration.date(day, calendar), let moved = calendar.date(byAdding: .day, value: days, to: date) else { return day }
        return PetRegistration.day(moved, calendar)
    }

    /// 날짜 줄 글자: 오늘이면 '오늘 · 10월 25일 (일)', 아니면 '10월 24일 (토)'.
    static func dayTitle(_ day: String, today: String) -> String {
        var calendar = Calendar(identifier: .gregorian)
        calendar.locale = Locale(identifier: "ko_KR")
        guard let date = PetRegistration.date(day, calendar) else { return day }
        let c = calendar.dateComponents([.month, .day, .weekday], from: date)
        let weekday = ["일", "월", "화", "수", "목", "금", "토"][(c.weekday ?? 1) - 1]
        let text = "\(c.month!)월 \(c.day!)일 (\(weekday))"
        return day == today ? "오늘 · \(text)" : text
    }

    /// 기록의 시각 'HH:mm'(기기 시간대).
    static func time(_ record: Record) -> String {
        let c = Calendar.okcheDevice.dateComponents([.hour, .minute], from: record.occurredAt)
        return String(format: "%02d:%02d", c.hour!, c.minute!)
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
