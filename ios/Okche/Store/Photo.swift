import Foundation
import SwiftData

enum PhotoKind: String, Codable, CaseIterable {
    case normal
    /// 체형 비교용
    case body
}

enum BodyView: String, Codable, CaseIterable {
    case top, side
}

/// 사진 (9장). 파일은 앱 저장 공간에 원본 그대로.
@Model
final class Photo {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var takenAt: Date
    /// 찍은 곳의 달력 날짜 `YYYY-MM-DD` — '작년 오늘'은 6시 규칙을 쓰지 않는다.
    var localDate: String
    var recordId: UUID?
    var kind: PhotoKind
    var bodyView: BodyView?
    /// 1(아주 마름) … 5(아주 통통)
    var bodyScore: Int?
    /// `{id}.원래 확장자`
    var file: String
    var caption: String?
    var goodDay: Bool

    init(id: UUID = UUID(), now: Date = .now, takenAt: Date, localDate: String, recordId: UUID? = nil,
         kind: PhotoKind = .normal, bodyView: BodyView? = nil, bodyScore: Int? = nil, fileExtension: String,
         caption: String? = nil, goodDay: Bool = false) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.takenAt = takenAt
        self.localDate = localDate
        self.recordId = recordId
        self.kind = kind
        self.bodyView = bodyView
        self.bodyScore = bodyScore
        file = "\(id.uuidString.lowercased()).\(fileExtension)"
        self.caption = caption
        self.goodDay = goodDay
    }
}

/// 사진 하나에 여러 아이 (9장).
@Model
final class PhotoPet {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var photoId: UUID
    var petId: UUID

    init(id: UUID = UUID(), now: Date = .now, photoId: UUID, petId: UUID) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.photoId = photoId
        self.petId = petId
    }
}
