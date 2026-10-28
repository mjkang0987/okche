package app.okche.store

import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/** 하루 기준 새벽 6시 (docs/data-model.md 1-2). */
object OkcheDay {
    const val START_HOUR = 6

    /**
     * 기록의 `day`(`YYYY-MM-DD`): 현지 벽시계가 6시 전이면 달력의 전날.
     * 경과 시간 6시간을 빼지 않고 시(hour)로 판단해 서머타임 날에도 iOS와 같은 값이 된다.
     */
    fun of(epochMs: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val local = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), zone)
        val date = if (local.hour < START_HOUR) local.toLocalDate().minusDays(1) else local.toLocalDate()
        return date.toString()
    }
}
