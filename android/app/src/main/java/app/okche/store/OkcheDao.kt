package app.okche.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * 같은 id를 넣으면 `@Insert`는 오류를 낸다(덮어쓰지 않음). 고정 ID 줄은 `IGNORE`로 없을 때만 만든다.
 * 백업 병합은 넣기 전에 11장 규칙(지운 쪽, 늦게 고친 쪽)으로 고른다.
 */
@Dao
abstract class OkcheDao {
    @Insert abstract suspend fun insert(pet: Pet)
    @Insert abstract suspend fun insert(restPeriod: RestPeriod)
    @Insert abstract suspend fun insert(record: Record)
    @Insert abstract suspend fun insert(medication: Medication)
    @Insert abstract suspend fun insert(schedule: MedSchedule)
    @Insert abstract suspend fun insert(plan: InsulinPlan)
    @Insert abstract suspend fun insert(food: Food)
    @Insert abstract suspend fun insert(change: FoodChange)
    @Insert abstract suspend fun insert(snack: Snack)
    @Insert abstract suspend fun insert(plan: MealPlan)
    @Insert abstract suspend fun insert(photo: Photo)
    @Insert abstract suspend fun insert(photoPet: PhotoPet)
    @Insert abstract suspend fun insert(setting: Setting)

    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertIfAbsent(setting: Setting)

    @Query("SELECT * FROM setting WHERE id = :id") protected abstract suspend fun setting(id: String): Setting?

    /** 설정 줄을 읽고, 없을 때만 처음 값으로 만든다. 지운 줄이 있어도 새로 만들지 않는다. */
    @Transaction
    open suspend fun loadOrCreateSetting(): Setting {
        insertIfAbsent(Setting())
        return checkNotNull(setting(FixedIds.SETTING))
    }

    @Query("SELECT * FROM pet ORDER BY created_at") abstract suspend fun pets(): List<Pet>
    @Query("SELECT * FROM med_schedule") abstract suspend fun medSchedules(): List<MedSchedule>
    @Query("SELECT * FROM insulin_plan") abstract suspend fun insulinPlans(): List<InsulinPlan>
    @Query("SELECT * FROM meal_plan") abstract suspend fun mealPlans(): List<MealPlan>
    @Query("SELECT * FROM photo") abstract suspend fun photos(): List<Photo>

    /** 실록 집계의 기본 검색: 한 아이·한 종류·한 날의 지우지 않은 기록. */
    @Query("SELECT * FROM record WHERE pet_id = :petId AND kind = :kind AND day = :day AND deleted_at IS NULL ORDER BY occurred_at")
    abstract suspend fun records(petId: String, kind: RecordKind, day: String): List<Record>
}
