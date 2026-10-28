import SwiftData

/// 기기 안 저장소. 모델을 더하면 여기 목록에도 넣는다.
enum OkcheStore {
    static var models: [any PersistentModel.Type] {
        [Pet.self, RestPeriod.self, Record.self, Medication.self, MedSchedule.self, InsulinPlan.self,
         Food.self, FoodChange.self, Snack.self, MealPlan.self, Photo.self, PhotoPet.self, Setting.self]
    }

    static func container(inMemory: Bool = false) throws -> ModelContainer {
        try ModelContainer(for: Schema(models), configurations: ModelConfiguration(isStoredInMemoryOnly: inMemory))
    }
}
