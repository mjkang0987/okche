package app.okche.store

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update

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

    @Update abstract suspend fun update(pet: Pet)
    @Update abstract suspend fun update(restPeriod: RestPeriod)
    @Update abstract suspend fun update(plan: InsulinPlan)
    @Update abstract suspend fun update(setting: Setting)

    @Insert(onConflict = OnConflictStrategy.IGNORE) protected abstract suspend fun insertIfAbsent(setting: Setting)

    @Query("SELECT * FROM setting WHERE id = :id") protected abstract suspend fun setting(id: String): Setting?

    /** 설정 줄을 읽고, 없을 때만 처음 값으로 만든다. 지운 줄이 있어도 새로 만들지 않는다. */
    @Transaction
    open suspend fun loadOrCreateSetting(): Setting {
        insertIfAbsent(Setting())
        return checkNotNull(setting(FixedIds.SETTING))
    }

    /** 아이 등록: 아이·첫 체중 기록·인슐린 계획을 한 번에 저장한다(하나라도 실패하면 아무것도 안 남음). */
    @Transaction
    open suspend fun insertPetRegistration(pet: Pet, weight: Record?, plan: InsulinPlan?) {
        insert(pet)
        weight?.let { insert(it) }
        plan?.let { insert(it) }
    }

    /**
     * 아이 정보 고치기(6-2): 바뀐 아이, 닫을 인슐린 계획, 새 휴양 기간, 닫을 휴양 기간을 한 번에 저장한다.
     */
    @Transaction
    open suspend fun savePetEdit(pet: Pet?, closePlans: List<InsulinPlan>, newRest: RestPeriod?, closeRests: List<RestPeriod>) {
        pet?.let { update(it) }
        closePlans.forEach { update(it) }
        newRest?.let { insert(it) }
        closeRests.forEach { update(it) }
    }

    /** 인슐린 설정(6-2): 고친·닫은 계획, 새 계획, 당뇨로 바꾼 아이, 인슐린 알림 켜짐(설정 줄)을 한 번에 저장한다. */
    @Transaction
    open suspend fun saveInsulinSetup(updates: List<InsulinPlan>, newPlan: InsulinPlan?, pet: Pet?, notifyInsulin: Boolean, now: Long) {
        updates.forEach { update(it) }
        newPlan?.let { insert(it) }
        pet?.let { update(it) }
        val setting = loadOrCreateSetting()
        if (setting.notify.insulinTime != notifyInsulin) {
            update(setting.copy(notify = setting.notify.copy(insulinTime = notifyInsulin), updatedAt = now))
        }
    }

    @Query("SELECT * FROM pet WHERE id = :id AND deleted_at IS NULL") abstract suspend fun pet(id: String): Pet?

    /** 그 아이의 열린 인슐린 계획. 첫째가 쓰는 계획(늦은 active_from, 같으면 늦은 updated_at — 문서 7장). */
    @Query("SELECT * FROM insulin_plan WHERE pet_id = :petId AND deleted_at IS NULL AND active_until IS NULL ORDER BY active_from DESC, updated_at DESC")
    abstract suspend fun openInsulinPlans(petId: String): List<InsulinPlan>

    /** 그 아이의 열린 휴양 기간. 하나라도 있으면 휴양 중(2-1). */
    @Query("SELECT * FROM rest_period WHERE pet_id = :petId AND deleted_at IS NULL AND ended_at IS NULL")
    abstract suspend fun openRestPeriods(petId: String): List<RestPeriod>

    /** 그 아이·그 종류의 지우지 않은 마지막 기록(아이 정보의 체중 줄). */
    @Query("SELECT * FROM record WHERE pet_id = :petId AND kind = :kind AND deleted_at IS NULL ORDER BY occurred_at DESC LIMIT 1")
    abstract suspend fun latestRecord(petId: String, kind: RecordKind): Record?

    // 화면에 보일 목록은 지운 줄을 뺀다(1-4).
    @Query("SELECT * FROM pet WHERE deleted_at IS NULL ORDER BY created_at") abstract suspend fun pets(): List<Pet>
    @Query("SELECT * FROM med_schedule WHERE deleted_at IS NULL") abstract suspend fun medSchedules(): List<MedSchedule>
    @Query("SELECT * FROM insulin_plan WHERE deleted_at IS NULL") abstract suspend fun insulinPlans(): List<InsulinPlan>
    @Query("SELECT * FROM meal_plan WHERE deleted_at IS NULL") abstract suspend fun mealPlans(): List<MealPlan>
    @Query("SELECT * FROM photo WHERE deleted_at IS NULL") abstract suspend fun photos(): List<Photo>

    /** 실록 집계의 기본 검색: 한 아이·한 종류·한 날의 지우지 않은 기록. */
    @Query("SELECT * FROM record WHERE pet_id = :petId AND kind = :kind AND day = :day AND deleted_at IS NULL ORDER BY occurred_at")
    abstract suspend fun records(petId: String, kind: RecordKind, day: String): List<Record>
}
