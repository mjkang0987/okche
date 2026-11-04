package app.okche.store

import kotlinx.coroutines.flow.Flow
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
    @Update abstract suspend fun update(record: Record)
    @Update abstract suspend fun update(change: FoodChange)
    @Update abstract suspend fun update(snack: Snack)
    @Update abstract suspend fun update(medication: Medication)

    // 약방(8-1): 뺀 약도 이름은 지난 기록 글자에 쓰여서 모두 읽는다.
    @Query("SELECT * FROM medication") abstract fun medicationsFlow(): Flow<List<Medication>>
    @Query("SELECT * FROM record WHERE kind = 'med' AND deleted_at IS NULL") abstract fun medRecordsFlow(): Flow<List<Record>>
    @Query("SELECT * FROM med_schedule WHERE deleted_at IS NULL") abstract fun medSchedulesFlow(): Flow<List<MedSchedule>>

    // 다과(7-4b): 목록에서 뺀 다과도 이름은 지난 기록 글자에 쓰여서 모두 읽는다.
    @Query("SELECT * FROM snack") abstract fun snacksFlow(): Flow<List<Snack>>
    @Query("SELECT * FROM record WHERE kind = 'snack' AND deleted_at IS NULL") abstract fun snackRecordsFlow(): Flow<List<Record>>

    // 사료 바꾸기(7-4a): 지우지 않은 줄만.
    @Query("SELECT * FROM food_change WHERE deleted_at IS NULL") abstract suspend fun foodChanges(): List<FoodChange>
    @Query("SELECT * FROM food_change WHERE deleted_at IS NULL") abstract fun foodChangesFlow(): Flow<List<FoodChange>>
    @Query("SELECT * FROM food WHERE deleted_at IS NULL") abstract fun foodsFlow(): Flow<List<Food>>
    @Query("SELECT * FROM food WHERE deleted_at IS NULL AND name = :name LIMIT 1") protected abstract suspend fun foodNamed(name: String): Food?

    /** 사료 바꾸기 저장: 같은 이름 사료가 있으면 그 사료, 없으면 새 사료 + 바꾸기 줄(한 번에). */
    @Transaction
    open suspend fun saveFoodChange(petId: String, name: String, startedOn: String, method: FoodChangeMethod, now: Long): FoodChange {
        val food = foodNamed(name) ?: Food(createdAt = now, name = name).also { insert(it) }
        return FoodChange(createdAt = now, petId = petId, foodId = food.id, startedOn = startedOn, method = method).also { insert(it) }
    }

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

    // 추억으로 간직하기·되돌리기·아이 지우기(6-4, 데이터 2장). 한 트랜잭션으로 저장해 중간에 실패해도 아무것도 남지 않는다.

    @Query("UPDATE pet SET memorialized_at = :at, updated_at = :now WHERE id = :petId")
    protected abstract suspend fun setMemorialized(petId: String, at: Long?, now: Long)

    @Query("UPDATE med_schedule SET active_until = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL AND active_until IS NULL")
    protected abstract suspend fun closeMedSchedules(petId: String, now: Long)

    @Query("UPDATE insulin_plan SET active_until = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL AND active_until IS NULL")
    protected abstract suspend fun closeInsulinPlans(petId: String, now: Long)

    @Query("UPDATE meal_plan SET active_until = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL AND active_until IS NULL")
    protected abstract suspend fun closeMealPlans(petId: String, now: Long)

    @Query("UPDATE rest_period SET ended_at = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL AND ended_at IS NULL")
    protected abstract suspend fun closeRestPeriods(petId: String, now: Long)

    /** 간직: `memorializedAt` = 지금, 열린 약 주기·인슐린·끼니 계획 `activeUntil`·열린 휴양 `endedAt` = 같은 시각. */
    @Transaction
    open suspend fun memorialize(petId: String, now: Long) {
        // 이미 간직 중이면 그대로(두 번 누름 — 간직 시각이 바뀌면 되돌릴 줄을 못 찾는다).
        if (pet(petId)?.memorializedAt != null) return
        setMemorialized(petId, now, now)
        closeMedSchedules(petId, now)
        closeInsulinPlans(petId, now)
        closeMealPlans(petId, now)
        closeRestPeriods(petId, now)
    }

    @Query("SELECT * FROM med_schedule WHERE pet_id = :petId AND deleted_at IS NULL AND active_until = :until")
    protected abstract suspend fun medSchedulesEndedAt(petId: String, until: Long): List<MedSchedule>

    @Query("SELECT * FROM insulin_plan WHERE pet_id = :petId AND deleted_at IS NULL AND active_until = :until")
    protected abstract suspend fun insulinPlansEndedAt(petId: String, until: Long): List<InsulinPlan>

    @Query("SELECT * FROM meal_plan WHERE pet_id = :petId AND deleted_at IS NULL AND active_until = :until")
    protected abstract suspend fun mealPlansEndedAt(petId: String, until: Long): List<MealPlan>

    /**
     * 되돌리기: `memorializedAt` 없음 + 간직할 때 끝낸 줄(`activeUntil` = 그때 `memorializedAt`)을 같은 내용 새 줄로(`activeFrom` = 지금).
     * 약 주기는 `seriesId` 그대로. 휴양은 다시 열지 않는다. 인슐린은 지금도 당뇨이고 열린 계획이 없을 때만
     * (간직 중 당뇨를 껐거나 인슐린을 새로 설정했으면 6-2 규칙대로 그대로 둔다).
     */
    @Transaction
    open suspend fun restoreFromMemorial(petId: String, now: Long) {
        val pet = pet(petId) ?: return
        val kept = pet.memorializedAt ?: return
        for (m in medSchedulesEndedAt(petId, kept)) {
            insert(m.copy(id = newId(), createdAt = now, updatedAt = now, activeFrom = now, activeUntil = null))
        }
        val reopenInsulin = pet.hasDiabetes && openInsulinPlans(petId).isEmpty()
        if (reopenInsulin) for (p in insulinPlansEndedAt(petId, kept)) {
            insert(p.copy(id = newId(), createdAt = now, updatedAt = now, activeFrom = now, activeUntil = null))
        }
        for (m in mealPlansEndedAt(petId, kept)) {
            insert(m.copy(id = newId(), createdAt = now, updatedAt = now, activeFrom = now, activeUntil = null))
        }
        setMemorialized(petId, null, now)
    }

    // '되돌릴 수 없어요'라서 선택 칸은 비운다(데이터 1-4 — 기록 data는 {}, 아이의 선택 칸, 인슐린 이름).
    @Query(
        "UPDATE pet SET deleted_at = :now, updated_at = :now, sex = NULL, neutered = NULL, birth_date = NULL, family_date = NULL, " +
            "diagnosis = NULL, profile_photo_id = NULL WHERE id = :petId AND deleted_at IS NULL",
    )
    protected abstract suspend fun deletePetRow(petId: String, now: Long)

    @Query("UPDATE record SET deleted_at = :now, updated_at = :now, data = '{}', slot_key = NULL WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deleteRecords(petId: String, now: Long)

    @Query("UPDATE med_schedule SET deleted_at = :now, updated_at = :now, how_to = NULL WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deleteMedSchedules(petId: String, now: Long)

    @Query("UPDATE insulin_plan SET deleted_at = :now, updated_at = :now, name = NULL WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deleteInsulinPlans(petId: String, now: Long)

    @Query("UPDATE food_change SET deleted_at = :now, updated_at = :now, finished_on = NULL WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deleteFoodChanges(petId: String, now: Long)

    @Query("UPDATE meal_plan SET deleted_at = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deleteMealPlans(petId: String, now: Long)

    @Query("UPDATE rest_period SET deleted_at = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deleteRestPeriods(petId: String, now: Long)

    /** 이 아이와만 이어진 사진(다른 아이 연결이 남지 않는 사진)을 지운다. 연결을 지우기 전에 부른다. */
    @Query(
        "UPDATE photo SET deleted_at = :now, updated_at = :now, caption = NULL, body_view = NULL, body_score = NULL, record_id = NULL " +
            "WHERE deleted_at IS NULL " +
            "AND id IN (SELECT photo_id FROM photo_pet WHERE pet_id = :petId AND deleted_at IS NULL) " +
            "AND NOT EXISTS (SELECT 1 FROM photo_pet o WHERE o.photo_id = photo.id AND o.pet_id != :petId AND o.deleted_at IS NULL)",
    )
    protected abstract suspend fun deletePhotosOnlyOf(petId: String, now: Long)

    @Query("UPDATE photo_pet SET deleted_at = :now, updated_at = :now WHERE pet_id = :petId AND deleted_at IS NULL")
    protected abstract suspend fun deletePhotoLinks(petId: String, now: Long)

    /** 아이 지우기: 아이와 그 아이의 기록·약 주기·인슐린·사료 바꾸기·끼니 계획·휴양 기간, 이 아이만의 사진과 사진 연결에 `deletedAt`. */
    @Transaction
    open suspend fun deletePet(petId: String, now: Long) {
        deletePetRow(petId, now)
        deleteRecords(petId, now)
        deleteMedSchedules(petId, now)
        deleteInsulinPlans(petId, now)
        deleteFoodChanges(petId, now)
        deleteMealPlans(petId, now)
        deleteRestPeriods(petId, now)
        deletePhotosOnlyOf(petId, now)
        deletePhotoLinks(petId, now)
    }

    /** 그날(6시 기준) 지우지 않은 기록, 늦은 것 위 — 기록 목록(7-1). */
    @Query("SELECT * FROM record WHERE day = :day AND deleted_at IS NULL ORDER BY occurred_at DESC")
    abstract fun dayRecordsFlow(day: String): Flow<List<Record>>

    /** 방금 남긴 기록: 그날 이 기기에서 가장 늦게 남긴 지우지 않은 기록(7-1). */
    // 급식기 자동 기록은 이 기기에서 '남긴' 기록이 아니라 뺀다.
    @Query("SELECT * FROM record WHERE day = :day AND deleted_at IS NULL AND auto = 0 ORDER BY created_at DESC LIMIT 1")
    abstract fun latestRecordFlow(day: String): Flow<Record?>

    @Query("SELECT MIN(occurred_at) FROM record WHERE pet_id = :petId AND deleted_at IS NULL")
    abstract suspend fun firstRecordAt(petId: String): Long?

    @Query("SELECT MAX(occurred_at) FROM record WHERE pet_id = :petId AND deleted_at IS NULL")
    abstract suspend fun lastRecordAt(petId: String): Long?

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

    /** 지운 줄 뺀 아이들(모신 순), 바뀌면 다시 내보냄 — 낙관·아이 바꾸기·더보기(6-3). */
    @Query("SELECT * FROM pet WHERE deleted_at IS NULL ORDER BY created_at") abstract fun petsFlow(): Flow<List<Pet>>

    /** 열린 휴양 기간 전부, 바뀌면 다시 내보냄(6-3). */
    @Query("SELECT * FROM rest_period WHERE deleted_at IS NULL AND ended_at IS NULL") abstract fun openRestPeriodsFlow(): Flow<List<RestPeriod>>
    @Query("SELECT * FROM med_schedule WHERE deleted_at IS NULL") abstract suspend fun medSchedules(): List<MedSchedule>
    @Query("SELECT * FROM insulin_plan WHERE deleted_at IS NULL") abstract suspend fun insulinPlans(): List<InsulinPlan>
    @Query("SELECT * FROM meal_plan WHERE deleted_at IS NULL") abstract suspend fun mealPlans(): List<MealPlan>
    @Query("SELECT * FROM photo WHERE deleted_at IS NULL") abstract suspend fun photos(): List<Photo>

    // 끼니(7-3)
    /** 그 아이의 지우지 않은 끼니 계획(열린 것 고르기는 `openMealPlan`). */
    @Query("SELECT * FROM meal_plan WHERE pet_id = :petId AND deleted_at IS NULL") abstract suspend fun mealPlans(petId: String): List<MealPlan>

    @Query("SELECT * FROM meal_plan WHERE pet_id = :petId AND deleted_at IS NULL") abstract fun mealPlansFlow(petId: String): Flow<List<MealPlan>>

    /** 그 차례의 지우지 않은 기록. */
    @Query("SELECT * FROM record WHERE slot_key = :key AND deleted_at IS NULL LIMIT 1") abstract suspend fun recordBySlot(key: String): Record?

    /** 수라 기록 ID 전부(지운 줄 포함 — 급식기 자동 기록을 되살리지 않게 1-5). */
    @Query("SELECT id FROM record WHERE kind = 'meal'") abstract suspend fun mealRecordIds(): List<String>

    @Query("SELECT slot_key FROM record WHERE kind = 'meal' AND deleted_at IS NULL AND slot_key IS NOT NULL") abstract suspend fun mealSlotKeys(): List<String>

    /** 그 ID의 기록 줄이 있는지(지운 줄 포함). */
    @Query("SELECT COUNT(*) FROM record WHERE id = :id") abstract suspend fun recordIdCount(id: String): Int

    /** 끼니 시간 저장·방식 바꾸기: 열린 계획을 닫고 새 줄(끼니가 없으면 닫기만) — 한 번에. */
    @Transaction
    open suspend fun replaceMealPlan(petId: String, plan: MealPlan?, now: Long, moved: List<Record> = emptyList()) {
        closeMealPlans(petId, now)
        plan?.let { insert(it) }
        for (r in moved) update(r)
    }

    /** 그 아이의 끼니 차례가 붙은 지우지 않은 수라 기록. */
    @Query("SELECT * FROM record WHERE pet_id = :petId AND kind = 'meal' AND deleted_at IS NULL AND slot_key IS NOT NULL")
    abstract suspend fun petMealsWithSlot(petId: String): List<Record>

    /** 그 아이의 수라 기록 중 그날·전날(6시 기준) 것 — 수라 화면은 차례 키로 짝지어서, 6시 전에 미리 남긴 6시대 끼니도 찾게. */
    @Query("SELECT * FROM record WHERE pet_id = :petId AND kind = 'meal' AND day IN (:day, :prevDay) AND deleted_at IS NULL ORDER BY occurred_at")
    abstract fun recentMealsFlow(petId: String, day: String, prevDay: String): Flow<List<Record>>

    @Transaction
    open suspend fun insertAll(records: List<Record>) {
        for (r in records) insert(r)
    }

    /** 실록 집계의 기본 검색: 한 아이·한 종류·한 날의 지우지 않은 기록. */
    @Query("SELECT * FROM record WHERE pet_id = :petId AND kind = :kind AND day = :day AND deleted_at IS NULL ORDER BY occurred_at")
    abstract suspend fun records(petId: String, kind: RecordKind, day: String): List<Record>
}
