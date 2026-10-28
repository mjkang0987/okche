import Foundation
import SwiftData

// 모든 모델은 docs/data-model.md를 따른다. 모델끼리는 SwiftData 관계 대신 ID 칸으로 잇는다(백업 JSON·Room과 같은 모양).
// 공통 칸: id(고유) · createdAt · updatedAt(고칠 때마다) · deletedAt(지운 때, 줄은 남김).

enum Species: String, Codable, CaseIterable {
    case cat, dog
}

/// 아이 (2장).
@Model
final class Pet {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var name: String
    var species: Species
    var breed: String?
    var furColor: String?
    /// `YYYY-MM-DD`
    var birthDate: String?
    var birthEstimated: Bool
    /// 가족 된 날 `YYYY-MM-DD`
    var familyDate: String?
    var diagnosis: String?
    var hasDiabetes: Bool
    var memorializedAt: Date?
    var profilePhotoId: UUID?

    init(id: UUID = UUID(), now: Date = .now, name: String, species: Species, breed: String? = nil, furColor: String? = nil,
         birthDate: String? = nil, birthEstimated: Bool = false, familyDate: String? = nil, diagnosis: String? = nil,
         hasDiabetes: Bool = false, memorializedAt: Date? = nil, profilePhotoId: UUID? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.name = name
        self.species = species
        self.breed = breed
        self.furColor = furColor
        self.birthDate = birthDate
        self.birthEstimated = birthEstimated
        self.familyDate = familyDate
        self.diagnosis = diagnosis
        self.hasDiabetes = hasDiabetes
        self.memorializedAt = memorializedAt
        self.profilePhotoId = profilePhotoId
    }
}

/// 휴양 기간 (2-1). `endedAt`이 없으면 지금 휴양 중.
@Model
final class RestPeriod {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var petId: UUID
    var startedAt: Date
    /// 이 시각은 포함하지 않음.
    var endedAt: Date?

    init(id: UUID = UUID(), now: Date = .now, petId: UUID, startedAt: Date, endedAt: Date? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.petId = petId
        self.startedAt = startedAt
        self.endedAt = endedAt
    }
}
