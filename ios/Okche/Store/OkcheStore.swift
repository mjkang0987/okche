import SwiftData

/// 기기 안 저장소. 모델을 더하면 여기 목록에도 넣는다.
enum OkcheStore {
    static var models: [any PersistentModel.Type] {
        [Pet.self, RestPeriod.self, Record.self, Medication.self, MedSchedule.self, InsulinPlan.self,
         Food.self, FoodChange.self, Snack.self, MealPlan.self, Photo.self, PhotoPet.self, Setting.self]
    }

    static func container(inMemory: Bool = false) throws -> ModelContainer {
        try ModelContainer(for: Schema(versionedSchema: SchemaV1.self), migrationPlan: MigrationPlan.self,
                           configurations: ModelConfiguration(isStoredInMemoryOnly: inMemory))
    }
}

/// 첫 출시 구조. 모델을 바꾸면 지금 모델을 SchemaV1 안으로 얼려 두고 SchemaV2와 옮기는 단계를 더한다.
enum SchemaV1: VersionedSchema {
    static let versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] { OkcheStore.models }
}

enum MigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}
