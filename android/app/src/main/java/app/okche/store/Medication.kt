package app.okche.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Suppress("EnumEntryName")
enum class MedCategory { medicine, supplement }

@Suppress("EnumEntryName")
enum class MedForm { pill, powder, liquid, topical, injection }

/** '반 알'은 `pill` 0.5. */
@Suppress("EnumEntryName")
enum class DoseUnit { pill, ml, pack, drop }

@Suppress("EnumEntryName")
enum class MedFrequency { daily, everyNDays, weekdays, everyNHours, asNeeded }

@Suppress("EnumEntryName")
enum class HowTo { plain, inFood, inTreat, syringe }

@Suppress("EnumEntryName")
enum class Syringe { u40, u100, unknown }

/** 인슐린 맞는 시간(`HH:mm`)과 양. */
data class InsulinSlot(val time: String, val units: Double)

/** 약방의 약 (4장). 남은 양은 줄여 저장하지 않고 기록으로 계산한다. */
@Entity(tableName = "medication")
data class Medication(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    /** 지워도 남긴다(지난 실록 글이 빈칸이 되지 않게). */
    val name: String,
    /** 6자까지. 없으면 `name`. */
    @ColumnInfo(name = "short_name") val shortName: String? = null,
    val category: MedCategory,
    val form: MedForm,
    @ColumnInfo(name = "dose_amount") val doseAmount: Double,
    val unit: DoseUnit,
    @ColumnInfo(name = "stock_amount") val stockAmount: Double? = null,
    @ColumnInfo(name = "stock_counted_at") val stockCountedAt: Long? = null,
    @ColumnInfo(name = "photo_id") val photoId: String? = null,
    val memo: String? = null,
)

/** 약 주기 (5장). 횟수·시간·양을 바꾸면 고치지 않고 새 줄(같은 `seriesId`)을 만든다. */
@Entity(tableName = "med_schedule")
data class MedSchedule(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "pet_id") val petId: String,
    @ColumnInfo(name = "medication_id") val medicationId: String,
    /** 처음 만든 줄은 자기 `id`, 바꿔 만든 줄은 옛 줄 값. */
    @ColumnInfo(name = "series_id") val seriesId: String = id,
    val amount: Double,
    val unit: DoseUnit,
    val frequency: MedFrequency,
    /** `HH:mm` — 매일·며칠마다·요일 */
    val times: List<String> = emptyList(),
    /** 며칠마다(2 이상) */
    @ColumnInfo(name = "every_days") val everyDays: Int? = null,
    /** 며칠마다의 첫 먹는 날 `YYYY-MM-DD` */
    @ColumnInfo(name = "start_date") val startDate: String? = null,
    /** ISO 요일, 월=1 … 일=7 */
    val weekdays: List<Int> = emptyList(),
    /** 몇 시간마다 */
    @ColumnInfo(name = "every_hours") val everyHours: Int? = null,
    @ColumnInfo(name = "first_at") val firstAt: Long? = null,
    /** 몇 시간마다에서 늦게 줬을 때 준 시각부터 다시 세기(참) / 처음 정한 시간대로(거짓). */
    @ColumnInfo(name = "shift_on_late") val shiftOnLate: Boolean = true,
    @ColumnInfo(name = "how_to") val howTo: HowTo? = null,
    val reminder: Boolean = true,
    /** 0, 5, 10, 15, 30, 60 */
    @ColumnInfo(name = "lead_minutes") val leadMinutes: Int = 10,
    @ColumnInfo(name = "active_from") val activeFrom: Long,
    /** 이 시각은 포함하지 않음. */
    @ColumnInfo(name = "active_until") val activeUntil: Long? = null,
)

/** 인슐린 계획 (7장) — 당뇨 아이만. 시간·양을 바꾸면 새 줄. */
@Entity(tableName = "insulin_plan")
data class InsulinPlan(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "pet_id") val petId: String,
    val name: String? = null,
    val syringe: Syringe = Syringe.unknown,
    val slots: List<InsulinSlot>,
    @ColumnInfo(name = "ring_during_quiet") val ringDuringQuiet: Boolean = false,
    @ColumnInfo(name = "lead_minutes") val leadMinutes: Int = 10,
    @ColumnInfo(name = "active_from") val activeFrom: Long,
    @ColumnInfo(name = "active_until") val activeUntil: Long? = null,
)
