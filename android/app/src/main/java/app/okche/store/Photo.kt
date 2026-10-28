package app.okche.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** 보통 / 체형 비교용. */
@Suppress("EnumEntryName")
enum class PhotoKind { normal, body }

@Suppress("EnumEntryName")
enum class BodyView { top, side }

/** 사진 (9장). 파일은 앱 저장 공간에 원본 그대로. */
@Entity(tableName = "photo")
data class Photo(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "taken_at") val takenAt: Long,
    /** 찍은 곳의 달력 날짜 `YYYY-MM-DD` — '작년 오늘'은 6시 규칙을 쓰지 않는다. */
    @ColumnInfo(name = "local_date") val localDate: String,
    @ColumnInfo(name = "record_id") val recordId: String? = null,
    val kind: PhotoKind = PhotoKind.normal,
    @ColumnInfo(name = "body_view") val bodyView: BodyView? = null,
    /** 1(아주 마름) … 5(아주 통통) */
    @ColumnInfo(name = "body_score") val bodyScore: Int? = null,
    /** `{id}.원래 확장자` (`fileName`) */
    val file: String,
    val caption: String? = null,
    @ColumnInfo(name = "good_day") val goodDay: Boolean = false,
) {
    companion object {
        fun fileName(id: String, extension: String) = "$id.$extension"
    }
}

/** 사진 하나에 여러 아이 (9장). 아이별 앨범을 바로 찾도록 양쪽에 색인. */
@Entity(tableName = "photo_pet", indices = [Index("photo_id"), Index("pet_id")])
data class PhotoPet(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "photo_id") val photoId: String,
    @ColumnInfo(name = "pet_id") val petId: String,
)
