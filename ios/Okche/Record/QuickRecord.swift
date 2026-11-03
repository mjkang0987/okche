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
        Row(kind: .meal, key: "result", choices: [("all", "다 먹음"), ("some", "남김"), ("none", "안 먹음")]),
    ]

    /// 종류 이름(화면 글자).
    static func name(_ kind: String) -> String {
        ["vomit": "구토", "stool": "매화", "urine": "소피", "meal": "수라", "snack": "다과", "weight": "옥체",
         "med": "탕약", "glucose": "혈당", "insulin": "인슐린"][kind] ?? kind
    }

    /// 값 글자(데이터 3장 값 → 화면 글자). 시트에서만 고르는 값(7-2)도 미리 둔다.
    private static let valueTitles: [String: [String: String]] = [
        "contents": ["kibble": "사료", "digested": "불은 사료", "liquidFoam": "거품", "hairball": "헤어볼", "plant": "풀",
                     "foreign": "이물질"],
        "shape": ["hard": "단단", "normal": "정상", "soft": "무름", "loose": "묽음", "watery": "물 같음"],
        "amount": ["little": "찔끔", "normal": "보통", "much": "많음"],
        "result": ["all": "다 먹음", "some": "남김", "none": "안 먹음"],
    ]

    /// 기록 줄 값 글자: 구토 '사료', 매화 '정상', 소피 '보통', 수라 '다 먹음'. 모르는 값이면 빈 글자.
    static func value(_ record: Record) -> String {
        guard let row = rows.first(where: { $0.kind.rawValue == record.kind }),
              let json = try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any] else { return "" }
        let raw: [String] = (json[row.key] as? [String]) ?? (json[row.key] as? String).map { [$0] } ?? []
        return raw.compactMap { valueTitles[row.key]?[$0] }.joined(separator: " · ")
    }

    /// 바로 기록: 그때 시각·6시 기준 `day`로 바로 저장. `petId`가 없으면 누군지 모름.
    @discardableResult
    static func save(_ row: Row, value: String, petId: UUID?, now: Date = .now, timeZone: TimeZone = .autoupdatingCurrent,
                     in context: ModelContext) throws -> Record {
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
        records.filter { $0.day == today && $0.deletedAt == nil }.max { $0.createdAt < $1.createdAt }
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
        record.petId = petId
        record.updatedAt = now
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
