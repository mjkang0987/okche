import SwiftData

/// 기기 안 저장소. 모델을 더하면 여기 목록에도 넣는다.
enum OkcheStore {
    static var models: [any PersistentModel.Type] {
        [Pet.self, RestPeriod.self, Record.self, Medication.self, MedSchedule.self, InsulinPlan.self,
         Food.self, FoodChange.self, Snack.self, MealPlan.self, Photo.self, PhotoPet.self, Setting.self]
    }

    static func container(inMemory: Bool = false) throws -> ModelContainer {
        try ModelContainer(for: Schema(versionedSchema: SchemaV1.self), migrationPlan: MigrationPlan.self,
                           // 백업은 본인 iCloud Drive 파일로 한다. CloudKit 동기화는 쓰지 않는다(켜지면 .unique를 못 씀).
                           configurations: ModelConfiguration(isStoredInMemoryOnly: inMemory, cloudKitDatabase: .none))
    }
}

/// 첫 출시 구조. 출시한 뒤에는 칸 하나를 더하는 것도 SchemaV2로 한다: 출시 때 모델을 SchemaV1 안으로 얼려 두고
/// 옮기는 단계를 더한다. 그냥 바꿔도 경량 마이그레이션으로 열리긴 하지만, 나중에 V1을 얼릴 때 버전이 안 맞게 된다.
enum SchemaV1: VersionedSchema {
    static let versionIdentifier = Schema.Version(1, 0, 0)
    static var models: [any PersistentModel.Type] { OkcheStore.models }
}

enum MigrationPlan: SchemaMigrationPlan {
    static var schemas: [any VersionedSchema.Type] { [SchemaV1.self] }
    static var stages: [MigrationStage] { [] }
}
