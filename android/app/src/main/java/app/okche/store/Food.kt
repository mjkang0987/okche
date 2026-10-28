package app.okche.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Suppress("EnumEntryName")
enum class FoodChangeMethod { gradual, atOnce }

@Suppress("EnumEntryName")
enum class SnackKind { lickable, freezeDried, jerky, wet, prescription, human }

/** 직접 급여 / 급식기(수라간 — 끼니 시간에 자동 기록). */
@Suppress("EnumEntryName")
enum class MealMode { direct, feeder }

/** 끼니 이름과 시간(`HH:mm`). */
data class MealSlot(val name: String, val time: String)

/** 사료 (8장). 지워도 `name`을 남긴다. */
@Entity(tableName = "food")
data class Food(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    val name: String,
    @ColumnInfo(name = "ingredient_photo_id") val ingredientPhotoId: String? = null,
)

/** 사료 바꾸기 (8장). 지금 먹는 사료 = 그 아이의 가장 늦은 `startedOn`. */
@Entity(tableName = "food_change")
data class FoodChange(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "pet_id") val petId: String,
    @ColumnInfo(name = "food_id") val foodId: String,
    /** `YYYY-MM-DD` */
    @ColumnInfo(name = "started_on") val startedOn: String,
    val method: FoodChangeMethod,
    /** '다 바꿨어요'를 누른 날 `YYYY-MM-DD` */
    @ColumnInfo(name = "finished_on") val finishedOn: String? = null,
    /** 마지막으로 '아직이에요'를 누른 단계: 0·7·10·12·14 */
    @ColumnInfo(name = "checked_step") val checkedStep: Int = 0,
)

/** 다과 (8장). */
@Entity(tableName = "snack")
data class Snack(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    val name: String,
    val kind: SnackKind,
    @ColumnInfo(name = "ok_during_food_change") val okDuringFoodChange: Boolean = false,
    @ColumnInfo(name = "ingredient_photo_id") val ingredientPhotoId: String? = null,
)

/** 끼니 계획 (8장). 시간을 바꾸면 그 시각에 새 줄. */
@Entity(tableName = "meal_plan")
data class MealPlan(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "pet_id") val petId: String,
    val mode: MealMode,
    val slots: List<MealSlot>,
    @ColumnInfo(name = "active_from") val activeFrom: Long,
    @ColumnInfo(name = "active_until") val activeUntil: Long? = null,
)
