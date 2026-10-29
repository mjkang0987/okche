import Foundation
import SwiftData

// 모든 모델은 docs/data-model.md를 따른다. 모델끼리는 SwiftData 관계 대신 ID 칸으로 잇는다(백업 JSON·Room과 같은 모양).
// 공통 칸: id(고유) · createdAt · updatedAt(고칠 때마다) · deletedAt(지운 때, 줄은 남김).
// id가 겹치는 줄을 넣으면 SwiftData는 오류 없이 기존 줄을 통째로 덮어쓴다(지운 줄도 되살아남).
// 고정 ID 줄은 꼭 있는지 먼저 보고 넣는다(`Setting.loadOrCreate`처럼). 백업 병합도 넣기 전에 11장 규칙으로 고른다.
// Codable 구조체 칸에 칸을 더할 때는 Optional로 — 이미 저장된 값을 못 읽어 앱이 죽을 수 있다.
// 구조체 칸은 앞말 없이 모델 표의 열로 펼쳐지므로, 한 모델 안 구조체끼리·모델 칸과 이름이 겹치지 않게 한다.

enum Species: String, Codable, CaseIterable {
    case cat, dog
}

enum Sex: String, Codable, CaseIterable {
    case male, female
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
    /// 목록에서 고르거나 '믹스 · 잘 모르겠어요'
    var breed: String
    var furColor: String
    /// 선택 — 없으면 안 고름
    var sex: Sex?
    /// 중성화(선택) — 없으면 안 고름
    var neutered: Bool?
    /// `YYYY-MM-DD`
    var birthDate: String?
    var birthEstimated: Bool
    /// 가족 된 날 `YYYY-MM-DD`
    var familyDate: String?
    var diagnosis: String?
    var hasDiabetes: Bool
    var memorializedAt: Date?
    var profilePhotoId: UUID?

    init(id: UUID = UUID(), now: Date = .now, name: String, species: Species, breed: String, furColor: String, sex: Sex? = nil, neutered: Bool? = nil,
         birthDate: String? = nil, birthEstimated: Bool = false, familyDate: String? = nil, diagnosis: String? = nil,
         hasDiabetes: Bool = false, memorializedAt: Date? = nil, profilePhotoId: UUID? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.name = name
        self.species = species
        self.breed = breed
        self.furColor = furColor
        self.sex = sex
        self.neutered = neutered
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
