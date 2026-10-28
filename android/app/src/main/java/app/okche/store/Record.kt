package app.okche.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.ZoneId

@Suppress("EnumEntryName")
enum class RecordKind { vomit, stool, urine, meal, snack, weight, med, glucose, insulin }

/** 일기 기록 하나 (3장). 실록 집계가 `petId + kind + day`로 한 번에 되도록 한 테이블. */
@Entity(tableName = "record", indices = [Index("pet_id", "kind", "day"), Index("slot_key")])
data class Record(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    /** 없으면 '누군지 모름' — 어느 실록에도 안 들어감. */
    @ColumnInfo(name = "pet_id") val petId: String?,
    val kind: RecordKind,
    @ColumnInfo(name = "occurred_at") val occurredAt: Long,
    /** `YYYY-MM-DD`, 새벽 6시 기준. 기록할 때 정해 두고 시간대를 옮겨도 바꾸지 않는다(백업에서 온 값은 그대로 받음). */
    val day: String = OkcheDay.of(occurredAt),
    @ColumnInfo(name = "slot_key") val slotKey: String? = null,
    /** 종류별 내용 JSON 글자. */
    val data: String = "{}",
    /** 앱이 저절로 만든 기록(급식기). */
    val auto: Boolean = false,
) {
    /** '시간 바꾸기': 그때 기기 시간대로 `day`를 다시 정한다. */
    fun withOccurredAt(epochMs: Long, zone: ZoneId = ZoneId.systemDefault(), now: Long = System.currentTimeMillis()) =
        copy(occurredAt = epochMs, day = OkcheDay.of(epochMs, zone), updatedAt = now)
}
