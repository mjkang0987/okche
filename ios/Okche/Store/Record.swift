import Foundation
import SwiftData

enum RecordKind: String, Codable, CaseIterable {
    case vomit, stool, urine, meal, snack, weight, med, glucose, insulin
}

/// 일기 기록 하나 (3장). 실록 집계가 `petId + kind + day`로 한 번에 되도록 한 테이블.
@Model
final class Record {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    /// 없으면 '누군지 모름' — 어느 실록에도 안 들어감.
    var petId: UUID?
    /// `RecordKind` 원시값. 검색 조건에 쓰므로 글자로 저장(iOS 17 `#Predicate`는 enum 칸 비교를 못 함).
    var kind: String
    var occurredAt: Date
    /// `YYYY-MM-DD`, 새벽 6시 기준(`OkcheDay`). 기록할 때 정해 두고 시간대를 옮겨도 바꾸지 않는다.
    private(set) var day: String
    var slotKey: String?
    /// 종류별 내용 JSON 글자.
    var data: String
    /// 앱이 저절로 만든 기록(급식기).
    var auto: Bool

    init(id: UUID = UUID(), now: Date = .now, petId: UUID?, kind: RecordKind, occurredAt: Date, timeZone: TimeZone = .autoupdatingCurrent,
         slotKey: String? = nil, data: String = "{}", auto: Bool = false) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.petId = petId
        self.kind = kind.rawValue
        self.occurredAt = occurredAt
        day = OkcheDay.of(occurredAt, in: timeZone)
        self.slotKey = slotKey
        self.data = data
        self.auto = auto
    }

    /// '시간 바꾸기': 그때 기기 시간대로 `day`를 다시 정한다.
    func setOccurredAt(_ date: Date, timeZone: TimeZone = .autoupdatingCurrent, now: Date = .now) {
        occurredAt = date
        day = OkcheDay.of(date, in: timeZone)
        updatedAt = now
    }
}
