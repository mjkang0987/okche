import Foundation
import SwiftData

/// 구토·매화·소피 자세히 시트·의서 레이어·기록 줄 값 글자 규칙(plan.md 7-2, 데이터 3장). 안드로이드 `RecordDetail.kt`와 같은 규칙.
enum RecordDetail {
    /// 시트 칸 하나: `data` 칸 이름, 칸 이름(legend)·덧말, 여럿 고르기인지, 숫자로 저장하는지, 고를 것(값, 시트 글자, 기록 줄 글자 — nil이면 줄에서 뺌).
    struct Field {
        let key: String
        let title: String
        var hint: String?
        var multi = false
        var numeric = false
        let options: [Option]
    }

    struct Option {
        let value: String
        let title: String
        let line: String?
    }

    /// 칸이 고른 값들(칸 이름 → 값들). 하나 고르기 칸도 배열 하나로 쥔다.
    typealias Values = [String: [String]]

    static func fields(_ kind: RecordKind) -> [Field] {
        switch kind {
        case .vomit: [
            Field(key: "contents", title: "토한 모양새는 어떠한가요?", hint: "(여러 개 고를 수 있어요)", multi: true, options: [
                Option(value: "kibble", title: "사료 알갱이", line: "사료"),
                Option(value: "digested", title: "죽처럼 불은 사료", line: "불은 사료"),
                Option(value: "liquidFoam", title: "액체·거품", line: "거품"),
                Option(value: "hairball", title: "헤어볼", line: "헤어볼"),
                Option(value: "plant", title: "풀·식물", line: "풀·식물"),
                Option(value: "foreign", title: "이물질 (끈·비닐 등)", line: "이물질"),
            ]),
            Field(key: "color", title: "빛깔", options: [
                Option(value: "food", title: "사료 색", line: nil),
                Option(value: "yellow", title: "노란색", line: "노란색"),
                Option(value: "clear", title: "투명·흰색", line: "투명·흰색"),
                Option(value: "green", title: "초록색", line: "초록색"),
                Option(value: "red", title: "붉은색 (피)", line: "붉은색"),
                Option(value: "darkRed", title: "검붉은·커피색", line: "검붉은·커피색"),
            ]),
            Field(key: "count", title: "연달아 몇 번?", numeric: true, options: [
                Option(value: "1", title: "1번", line: nil),
                Option(value: "2", title: "2번", line: "2번"),
                Option(value: "3", title: "3번 이상", line: "3번 이상"),
            ]),
        ]
        case .stool: [
            Field(key: "shape", title: "어떤 모양이었나요?", hint: "(단단 → 물)", options: [
                Option(value: "hard", title: "단단해요", line: "단단"),
                Option(value: "normal", title: "정상", line: "정상"),
                Option(value: "soft", title: "물러요", line: "무름"),
                Option(value: "loose", title: "묽어요", line: "묽음"),
                Option(value: "watery", title: "물 같아요", line: "물 같음"),
            ]),
            Field(key: "color", title: "빛깔", options: [
                Option(value: "brown", title: "갈색 (보통)", line: nil),
                Option(value: "yellowOrange", title: "노랑·주황", line: "노랑·주황"),
                Option(value: "green", title: "초록", line: "초록"),
                Option(value: "black", title: "검은색 (짜장처럼)", line: "검은색"),
                Option(value: "pale", title: "회백색", line: "회백색"),
            ]),
            Field(key: "signs", title: "함께 보였나요?", hint: "(여러 개 고를 수 있어요)", multi: true, options: [
                Option(value: "blood", title: "피가 비쳐요", line: "피"),
                Option(value: "mucus", title: "점액(끈적한 막)이 있어요", line: "점액"),
                Option(value: "foreign", title: "털·이물질이 섞였어요", line: "털·이물질"),
            ]),
        ]
        case .urine: [
            Field(key: "amount", title: "양은 어땠나요?", hint: "(모래 덩어리 크기로)", options: [
                Option(value: "little", title: "찔끔", line: "찔끔"),
                Option(value: "normal", title: "보통", line: "보통"),
                Option(value: "much", title: "많음", line: "많음"),
            ]),
            Field(key: "color", title: "빛깔", options: [
                Option(value: "paleYellow", title: "연한 노랑 (보통)", line: nil),
                Option(value: "clear", title: "거의 투명", line: "거의 투명"),
                Option(value: "darkYellow", title: "진한 노랑", line: "진한 노랑"),
                Option(value: "pinkRed", title: "분홍·붉은색", line: "분홍·붉은색"),
            ]),
            Field(key: "signs", title: "함께 보였나요?", hint: "(여러 개 고를 수 있어요)", multi: true, options: [
                Option(value: "straining", title: "힘만 주고 거의 안 나와요", line: "힘만 줌"),
                Option(value: "frequent", title: "화장실을 자주 들락거려요", line: "자주 들락거림"),
                Option(value: "outside", title: "화장실 밖에 봤어요", line: "화장실 밖"),
                Option(value: "pain", title: "볼 때 울거나 아파해요", line: "아파함"),
            ]),
        ]
        default: []
        }
    }

    /// 의서 레이어를 띄우는 값(2026-11-01 사용자): 구토 피·검붉은·이물질·3번 이상, 매화 검은색, 소피 힘만 줌.
    private static let alertValues: [RecordKind: [String: Set<String>]] = [
        .vomit: ["color": ["red", "darkRed"], "contents": ["foreign"], "count": ["3"]],
        .stool: ["color": ["black"]],
        .urine: ["signs": ["straining"]],
    ]

    /// 고르기 전 `before`에 없던 레이어 값을 `after`에서 새로 골랐는지(풀 때는 안 뜸).
    static func picksAlert(_ kind: RecordKind, before: Values, after: Values) -> Bool {
        (alertValues[kind] ?? [:]).contains { key, values in
            !Set(after[key] ?? []).subtracting(before[key] ?? []).isDisjoint(with: values)
        }
    }

    /// 칩 하나를 눌렀을 때: 하나 고르기는 바꾸거나 다시 누르면 풂, 여럿 고르기는 넣고 뺌.
    static func toggle(_ values: Values, field: Field, value: String) -> Values {
        var next = values
        let current = values[field.key] ?? []
        if current.contains(value) {
            next[field.key] = current.filter { $0 != value }
        } else {
            next[field.key] = field.multi ? current + [value] : [value]
        }
        if next[field.key]?.isEmpty == true { next[field.key] = nil }
        return next
    }

    /// 기록의 `data` → 시트 값(모르는 칸·값은 버림).
    static func values(_ record: Record) -> Values {
        guard let kind = RecordKind(rawValue: record.kind),
              let json = try? JSONSerialization.jsonObject(with: Data(record.data.utf8)) as? [String: Any] else { return [:] }
        var result: Values = [:]
        for field in fields(kind) {
            let raw: [String] = switch json[field.key] {
            case let list as [Any]: list.map { "\($0)" }
            case let one?: ["\(one)"]
            case nil: []
            }
            let known = raw.filter { value in field.options.contains { $0.value == value } }
            if !known.isEmpty { result[field.key] = field.multi ? known : [known[0]] }
        }
        return result
    }

    /// 시트 값 → `data` JSON(여럿 고르기는 배열, 횟수는 숫자, 안 고른 칸은 뺌).
    static func data(_ kind: RecordKind, _ values: Values) -> String {
        var json: [String: Any] = [:]
        for field in fields(kind) {
            guard let picked = values[field.key], !picked.isEmpty else { continue }
            let ordered = field.options.map(\.value).filter(picked.contains)
            if field.multi {
                json[field.key] = ordered
            } else if field.numeric {
                json[field.key] = Int(ordered[0]) ?? 0
            } else {
                json[field.key] = ordered[0]
            }
        }
        let bytes = (try? JSONSerialization.data(withJSONObject: json, options: [.sortedKeys])) ?? Data("{}".utf8)
        return String(decoding: bytes, as: UTF8.self)
    }

    /// 기록 줄 값 글자: 칸 순서대로 줄 글자를 ' · '로(보통 값은 뺌). 고른 게 없으면 '자세한 내용 없음'. 시트가 없는 종류는 nil.
    static func line(_ record: Record) -> String? {
        guard let kind = RecordKind(rawValue: record.kind), !fields(kind).isEmpty else { return nil }
        let values = values(record)
        let parts = fields(kind).flatMap { field in
            field.options.filter { values[field.key]?.contains($0.value) == true }.compactMap(\.line)
        }
        return parts.isEmpty ? "자세한 내용 없음" : parts.joined(separator: " · ")
    }

    /// 자세히 시트 '기록하기': 새 기록이면 넣고, 고치기면 그 기록의 `data`·`occurredAt`·`day`·`updatedAt`만 바꾼다.
    @discardableResult
    static func save(_ kind: RecordKind, values: Values, occurredAt: Date, editing: Record?, petId: UUID?, now: Date = .now,
                     timeZone: TimeZone = .autoupdatingCurrent, in context: ModelContext) throws -> Record {
        let at = min(occurredAt, now) // 지금보다 뒤는 안 됨
        let record: Record
        if let editing {
            editing.data = data(kind, values)
            // 시간을 바꿨을 때만 `day`를 다시 정한다(다른 시간대에서 남긴 기록이 칸만 고쳐 다른 날로 옮겨지지 않게).
            if at != editing.occurredAt {
                editing.setOccurredAt(at, timeZone: timeZone, now: now)
            } else {
                editing.updatedAt = now
            }
            record = editing
        } else {
            record = Record(now: now, petId: petId, kind: kind, occurredAt: at, timeZone: timeZone, data: data(kind, values))
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
}
