package app.okche.store

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

// 모든 엔티티는 docs/data-model.md를 따른다. 엔티티끼리는 외래 키 대신 ID 칸으로 잇는다(백업 JSON·iOS와 같은 모양).
// 공통 칸: id(UUID 소문자 글자) · createdAt · updatedAt(고칠 때마다) · deletedAt(지운 때, 줄은 남김). 시각은 UTC 밀리초.
// 테이블·열 이름은 snake_case(문서 1-1). 선택지 enum은 이름이 곧 저장 값이라 문서의 영어 값 그대로 쓴다.

fun newId(): String = UUID.randomUUID().toString()

@Suppress("EnumEntryName")
enum class Species { cat, dog }

/** 성별 — 남아·여아. */
@Suppress("EnumEntryName")
enum class Sex { male, female }

/** 아이 (2장). */
@Entity(tableName = "pet")
data class Pet(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    val name: String,
    val species: Species,
    /** 목록에서 고르거나 '믹스 · 잘 모르겠어요' */
    val breed: String,
    @ColumnInfo(name = "fur_color") val furColor: String,
    /** 성별(선택), 없으면 안 고름. */
    val sex: Sex? = null,
    /** 중성화(선택) — 했어요 참·안 했어요 거짓, 없으면 안 고름. */
    val neutered: Boolean? = null,
    /** `YYYY-MM-DD` */
    @ColumnInfo(name = "birth_date") val birthDate: String? = null,
    @ColumnInfo(name = "birth_estimated") val birthEstimated: Boolean = false,
    /** 가족 된 날 `YYYY-MM-DD` */
    @ColumnInfo(name = "family_date") val familyDate: String? = null,
    val diagnosis: String? = null,
    @ColumnInfo(name = "has_diabetes") val hasDiabetes: Boolean = false,
    @ColumnInfo(name = "memorialized_at") val memorializedAt: Long? = null,
    @ColumnInfo(name = "profile_photo_id") val profilePhotoId: String? = null,
)

/** 휴양 기간 (2-1). `endedAt`이 없으면 지금 휴양 중. */
@Entity(tableName = "rest_period")
data class RestPeriod(
    @PrimaryKey val id: String = newId(),
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    @ColumnInfo(name = "pet_id") val petId: String,
    @ColumnInfo(name = "started_at") val startedAt: Long,
    /** 이 시각은 포함하지 않음. */
    @ColumnInfo(name = "ended_at") val endedAt: Long? = null,
)
