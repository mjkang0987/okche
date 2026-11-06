package app.okche.store

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Suppress("EnumEntryName")
enum class CelebrateTone { joseon, plain }

/** 밤 알림 쉬기. 이 시간의 알림은 보내지 않고 버린다. 시각은 `HH:mm`. */
data class QuietHours(val enabled: Boolean, val start: String, val end: String)

/** 알림 종류별 켜짐. */
data class NotifySettings(
    @ColumnInfo(name = "med_time") val medTime: Boolean,
    @ColumnInfo(name = "insulin_time") val insulinTime: Boolean,
    @ColumnInfo(name = "low_med") val lowMed: Boolean,
    @ColumnInfo(name = "weigh_in") val weighIn: Boolean,
    @ColumnInfo(name = "good_day") val goodDay: Boolean,
    @ColumnInfo(name = "last_year_today") val lastYearToday: Boolean,
)

/** 체중 재기 알림. `weekday`는 ISO(월=1 … 일=7), `time`은 `HH:mm`. */
data class WeightReminder(val weekday: Int, val time: String)

/**
 * 함께 쓰는 설정 (10장). 고정 ID 한 줄이라 두 기기 백업을 합쳐도 하나.
 * 이 기기만의 설정(백업 대상 등)은 여기 두지 않고 DataStore에.
 */
@Entity(tableName = "setting")
data class Setting(
    @PrimaryKey val id: String = FixedIds.SETTING,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = createdAt,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    // 밤 알림 쉬기·알림 켜짐은 기별 보드(Notify) 값(8-4c). 새로 깐 기기만 — 있는 줄은 그대로.
    // shortcut: 축하 문구의 처음 값은 임시(문서 10장), 그 설정 화면 만들 때 디자인과 맞춘다.
    @Embedded(prefix = "quiet_hours_") val quietHours: QuietHours = QuietHours(enabled = true, start = "22:00", end = "08:00"),
    @Embedded(prefix = "notify_") val notify: NotifySettings = NotifySettings(
        medTime = true, insulinTime = true, lowMed = true, weighIn = true, goodDay = true, lastYearToday = true,
    ),
    @Embedded(prefix = "weight_reminder_") val weightReminder: WeightReminder = WeightReminder(weekday = 7, time = "10:00"),
    /** 남은 약 알림 기준 일수 */
    @ColumnInfo(name = "low_med_days") val lowMedDays: Int = 3,
    @ColumnInfo(name = "celebrate_tone") val celebrateTone: CelebrateTone = CelebrateTone.joseon,
)
