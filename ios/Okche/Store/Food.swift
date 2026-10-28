import Foundation
import SwiftData

enum FoodChangeMethod: String, Codable, CaseIterable {
    case gradual, atOnce
}

enum SnackKind: String, Codable, CaseIterable {
    case lickable, freezeDried, jerky, wet, prescription, human
}

enum MealMode: String, Codable, CaseIterable {
    /// 직접 급여
    case direct
    /// 급식기(수라간) — 끼니 시간에 자동 기록
    case feeder
}

/// 끼니 이름과 시간.
struct MealSlot: Codable, Hashable {
    var name: String
    /// `HH:mm`
    var time: String
}

/// 사료 (8장). 지워도 `name`을 남긴다.
@Model
final class Food {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var name: String
    var ingredientPhotoId: UUID?

    init(id: UUID = UUID(), now: Date = .now, name: String, ingredientPhotoId: UUID? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.name = name
        self.ingredientPhotoId = ingredientPhotoId
    }
}

/// 사료 바꾸기 (8장). 지금 먹는 사료 = 그 아이의 가장 늦은 `startedOn`.
@Model
final class FoodChange {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var petId: UUID
    var foodId: UUID
    /// `YYYY-MM-DD`
    var startedOn: String
    var method: FoodChangeMethod
    /// '다 바꿨어요'를 누른 날 `YYYY-MM-DD`
    var finishedOn: String?
    /// 마지막으로 '아직이에요'를 누른 단계: 0·7·10·12·14
    var checkedStep: Int

    init(id: UUID = UUID(), now: Date = .now, petId: UUID, foodId: UUID, startedOn: String, method: FoodChangeMethod,
         finishedOn: String? = nil, checkedStep: Int = 0) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.petId = petId
        self.foodId = foodId
        self.startedOn = startedOn
        self.method = method
        self.finishedOn = finishedOn
        self.checkedStep = checkedStep
    }
}

/// 다과 (8장).
@Model
final class Snack {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var name: String
    var kind: SnackKind
    var okDuringFoodChange: Bool
    var ingredientPhotoId: UUID?

    init(id: UUID = UUID(), now: Date = .now, name: String, kind: SnackKind, okDuringFoodChange: Bool = false,
         ingredientPhotoId: UUID? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.name = name
        self.kind = kind
        self.okDuringFoodChange = okDuringFoodChange
        self.ingredientPhotoId = ingredientPhotoId
    }
}

/// 끼니 계획 (8장). 시간을 바꾸면 그 시각에 새 줄.
@Model
final class MealPlan {
    @Attribute(.unique) var id: UUID
    var createdAt: Date
    var updatedAt: Date
    var deletedAt: Date?

    var petId: UUID
    var mode: MealMode
    var slots: [MealSlot]
    var activeFrom: Date
    var activeUntil: Date?

    init(id: UUID = UUID(), now: Date = .now, petId: UUID, mode: MealMode, slots: [MealSlot], activeFrom: Date,
         activeUntil: Date? = nil) {
        self.id = id
        createdAt = now
        updatedAt = now
        self.petId = petId
        self.mode = mode
        self.slots = slots
        self.activeFrom = activeFrom
        self.activeUntil = activeUntil
    }
}
