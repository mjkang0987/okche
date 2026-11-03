package app.okche.ui.pet

import app.okche.store.OkcheDao
import app.okche.store.Pet
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// 추억 화면 숫자(plan.md 6-4). iOS `PetLifecycle.stats`와 같은 규칙. 간직·되돌리기·지우기 저장은 OkcheDao 트랜잭션.

/** 함께한 날(가족 된 날부터 오늘까지, 없으면 첫 기록 날부터, 둘 다 없으면 null)·처음/마지막 기록 날짜(없으면 null). */
data class MemorialStats(val days: Long?, val first: String?, val last: String?)

private val memorialDate = DateTimeFormatter.ofPattern("yyyy년 M월 d일", Locale.KOREAN)

suspend fun memorialStats(dao: OkcheDao, pet: Pet, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): MemorialStats {
    val day = { at: Long -> Instant.ofEpochMilli(at).atZone(zone).toLocalDate() }
    val first = dao.firstRecordAt(pet.id)?.let(day)
    val last = dao.lastRecordAt(pet.id)?.let(day)
    return MemorialStats(
        days = companionDays(pet.familyDate ?: first?.toString(), today),
        first = first?.format(memorialDate),
        last = last?.format(memorialDate),
    )
}
