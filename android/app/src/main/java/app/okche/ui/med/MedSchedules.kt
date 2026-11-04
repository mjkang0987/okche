package app.okche.ui.med

import app.okche.store.DoseUnit
import app.okche.store.HowTo
import app.okche.store.MedFrequency
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDao
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.ui.pet.toDecimal
import app.okche.ui.pet.toWholeNumber
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor

// 약 주기·탕약 규칙(plan.md 8-2a, 데이터 5장·6-1). iOS `MedSchedules.swift`와 같은 규칙.

val medFrequencies = listOf(
    MedFrequency.daily to "매일", MedFrequency.everyNDays to "며칠마다", MedFrequency.weekdays to "요일 정해서",
    MedFrequency.everyNHours to "몇 시간마다", MedFrequency.asNeeded to "필요할 때만",
)
val howTos = listOf(HowTo.plain to "그냥 먹여요", HowTo.inFood to "사료에 섞어요", HowTo.inTreat to "간식에 숨겨요", HowTo.syringe to "주사기로 먹여요")
val medLeadChoices = listOf(0 to "먹일 시간에", 5 to "5분 전", 10 to "10분 전", 15 to "15분 전", 30 to "30분 전", 60 to "1시간 전")
/** ISO 요일(월=1) 글자. */
val weekdayNames = listOf("월", "화", "수", "목", "금", "토", "일")

/** 한 번에 양 '1알'·'반 포'(0.5알·0.5포는 '반'). */
fun amountText(amount: Double, unit: DoseUnit): String =
    if (amount == 0.5 && (unit == DoseUnit.pill || unit == DoseUnit.pack)) "반 ${unitTitle(unit)}" else "${medNumber(amount)}${unitTitle(unit)}"

fun everyDaysText(n: Int) = when (n) {
    2 -> "이틀에 한 번"
    3 -> "사흘에 한 번"
    7 -> "일주일에 한 번"
    else -> "${n}일에 한 번"
}

/** 주기 글자 '하루 2번'·'이틀에 한 번'·'월·목'·'8시간마다'·'필요할 때만'. */
fun frequencyText(s: MedSchedule) = when (s.frequency) {
    MedFrequency.daily -> "하루 ${s.times.size}번"
    MedFrequency.everyNDays -> everyDaysText(s.everyDays ?: 2)
    MedFrequency.weekdays -> s.weekdays.sorted().joinToString("·") { weekdayNames[(it - 1 + 7) % 7] }
    MedFrequency.everyNHours -> "${s.everyHours ?: 1}시간마다"
    MedFrequency.asNeeded -> "필요할 때만"
}

/** 카드에 붙는 먹이는 방법('그냥 먹여요'는 안 붙임). */
fun howToText(howTo: HowTo?) = when (howTo) {
    HowTo.inFood -> "사료에 섞어서"
    HowTo.inTreat -> "간식에 숨겨서"
    HowTo.syringe -> "주사기로"
    else -> null
}

/** 카드 아래 글자 '콩이 · 이틀에 한 번 · 1알 · 사료에 섞어서'. */
fun cardDetail(s: MedSchedule, petName: String) = listOfNotNull(petName, frequencyText(s), amountText(s.amount, s.unit), howToText(s.howTo)).joinToString(" · ")

/** '오전 8:00'. */
fun clock(time: String): String {
    val p = time.split(":").mapNotNull { it.toIntOrNull() }
    if (p.size != 2) return time
    return "${if (p[0] < 12) "오전" else "오후"} ${if (p[0] % 12 == 0) 12 else p[0] % 12}:${"%02d".format(p[1])}"
}

/** 그날(6시 기준 `day`)이 먹는 날인지. */
fun isDoseDay(s: MedSchedule, day: String): Boolean = when (s.frequency) {
    MedFrequency.daily -> true
    MedFrequency.everyNDays -> {
        val start = s.startDate
        val n = s.everyDays
        if (start == null || n == null || n <= 0 || day < start) false
        else runCatching { ChronoUnit.DAYS.between(LocalDate.parse(start), LocalDate.parse(day)) % n == 0L }.getOrDefault(false)
    }
    MedFrequency.weekdays -> runCatching { LocalDate.parse(day).dayOfWeek.value in s.weekdays }.getOrDefault(false)
    MedFrequency.everyNHours, MedFrequency.asNeeded -> false
}

/** `{day}`의 `HH:mm` 순간 — 06:00 전이면 다음 날(1-2). */
fun occurrence(day: String, time: String, zone: ZoneId = ZoneId.systemDefault()): Long? = runCatching {
    val t = LocalTime.parse(time)
    val date = LocalDate.parse(day).let { if (t.hour < 6) it.plusDays(1) else it }
    LocalDateTime.of(date, t).atZone(zone).toInstant().toEpochMilli()
}.getOrNull()

/** 다음 먹일 때(지금 이후 첫 차례). 필요할 때만이면 null. */
// shortcut: 몇 시간마다 '준 시각부터 다시 세기'는 기록이 생기는 8-3에서 — 지금은 `firstAt`부터 간격대로.
fun nextDue(s: MedSchedule, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): Long? {
    when (s.frequency) {
        MedFrequency.asNeeded -> return null
        MedFrequency.everyNHours -> {
            val first = s.firstAt ?: return null
            val h = s.everyHours?.takeIf { it > 0 } ?: return null
            val step = h * 3_600_000L
            val start = maxOf(now, s.activeFrom)
            if (first >= start) return first
            return first + ceil((start - first).toDouble() / step).toLong() * step
        }
        else -> {
            var day = app.okche.store.OkcheDay.of(maxOf(now, s.activeFrom), zone)
            repeat(400) {
                if (isDoseDay(s, day)) {
                    s.times.sorted().mapNotNull { occurrence(day, it, zone) }.firstOrNull { it >= now && it >= s.activeFrom }?.let { return it }
                }
                day = LocalDate.parse(day).plusDays(1).toString()
            }
            return null
        }
    }
}

/** 며칠마다·요일 카드 옆 글자 '오늘 먹는 날 · 다음 10/29' / '다음 10/29'. */
fun doseDayText(s: MedSchedule, today: String): String? {
    if (s.frequency != MedFrequency.everyNDays && s.frequency != MedFrequency.weekdays) return null
    var day = LocalDate.parse(today).plusDays(1)
    var next: LocalDate? = null
    repeat(400) {
        if (next == null && isDoseDay(s, day.toString())) next = day
        day = day.plusDays(1)
    }
    return listOfNotNull(if (isDoseDay(s, today)) "오늘 먹는 날" else null, next?.let { "다음 ${it.monthValue}/${it.dayOfMonth}" }).joinToString(" · ")
}

/** '이렇게 돌아가요' — 처음 먹일 때부터 간격대로 몇 번. */
fun hoursPreview(firstAt: Long, everyHours: Int, count: Int = 4, zone: ZoneId = ZoneId.systemDefault()): List<String> {
    val f = DateTimeFormatter.ofPattern("M/d (E) a h:mm", Locale.KOREAN)
    return (0 until count).map { Instant.ofEpochMilli(firstAt + it * everyHours * 3_600_000L).atZone(zone).format(f) }
}

/** 쓰는 줄: 열린 줄(지우지 않음), `seriesId`마다 하나(늦은 `activeFrom`, 같으면 늦은 `updatedAt` — 5장). */
fun openSeries(schedules: List<MedSchedule>) = schedules.filter { it.deletedAt == null && it.activeUntil == null }
    .groupBy { it.seriesId }.values.mapNotNull { rows -> rows.maxWithOrNull(compareBy({ it.activeFrom }, { it.updatedAt })) }

/** 먹고 있는 약 카드: 추억·지운 아이 빼고, 다음 먹일 시간 순(같으면 아이 모신 순), 필요할 때만은 맨 뒤. */
fun medCards(schedules: List<MedSchedule>, pets: List<Pet>, now: Long = System.currentTimeMillis()): List<Pair<MedSchedule, Pet>> {
    val alive = pets.filter { it.deletedAt == null && it.memorializedAt == null }.associateBy { it.id }
    return openSeries(schedules).mapNotNull { s -> alive[s.petId]?.let { s to it } }
        .sortedWith(compareBy<Pair<MedSchedule, Pet>, Long?>(nullsLast()) { nextDue(it.first, now) }.thenBy { it.second.createdAt }.thenBy { it.first.createdAt }.thenBy { it.first.id }) // 늘 같은 순서로
}

/** 하루 양(일분 셈): 매일 = 시간 수 × 양, 며칠마다 ÷ 간격, 요일 × 요일 수 ÷ 7, 몇 시간마다 × 24 ÷ 간격, 필요할 때만 0. */
fun dailyAmount(s: MedSchedule): Double {
    val perDay = s.times.size * s.amount
    return when (s.frequency) {
        MedFrequency.daily -> perDay
        MedFrequency.everyNDays -> perDay / maxOf(s.everyDays ?: 1, 1)
        MedFrequency.weekdays -> perDay * s.weekdays.size / 7
        MedFrequency.everyNHours -> s.amount * 24 / maxOf(s.everyHours ?: 1, 1)
        MedFrequency.asNeeded -> 0.0
    }
}

/** 남은 약 ○일분 = 남은 양 ÷ 그 약의 열린 줄들 하루 양 합(같은 단위만), 내림. 하루 양이 0이거나 남은 양을 안 적었으면 null. */
fun daysLeft(med: Medication, schedules: List<MedSchedule>, records: List<Record>): Int? {
    val left = medRemaining(med, records) ?: return null
    val perDay = openSeries(schedules).filter { it.medicationId == med.id && it.unit == med.unit }.sumOf(::dailyAmount)
    if (perDay <= 0) return null
    return floor(maxOf(left, 0.0) / perDay).toInt()
}

/** 카드 약방 줄 '남은 약 48일분'·'남은 24알'·'남은 양 안 적음'. */
fun stockText(med: Medication, schedules: List<MedSchedule>, records: List<Record>): String {
    daysLeft(med, schedules, records)?.let { return "남은 약 ${it}일분" }
    val left = medRemaining(med, records) ?: return "남은 양 안 적음"
    return "남은 ${medNumber(maxOf(left, 0.0))}${unitTitle(med.unit)}"
}

/** 약 추가 칸. */
data class ScheduleFields(
    val petIds: List<String> = emptyList(),
    val medicationId: String? = null,
    val dose: String = "1",
    val frequency: MedFrequency = MedFrequency.daily,
    val everyDays: String = "2",
    val startDate: LocalDate = LocalDate.now(),
    val weekdays: List<Int> = emptyList(),
    val times: List<String> = listOf("08:00"),
    val everyHours: String = "8",
    val firstAt: Long = roundedUp(System.currentTimeMillis()),
    val shiftOnLate: Boolean = true,
    val reminder: Boolean = true,
    val leadMinutes: Int = 10,
    val howTo: HowTo = HowTo.plain,
)

/** 처음 먹일 때 처음 값: 지금을 분 단위로 올림. */
fun roundedUp(epochMs: Long) = ceil(epochMs / 60_000.0).toLong() * 60_000L

private val timeFrequencies = setOf(MedFrequency.daily, MedFrequency.everyNDays, MedFrequency.weekdays)

/** 칸 검사. 칸 이름 → 문구(칸 순서). */
fun scheduleErrors(f: ScheduleFields): Map<String, String> {
    val e = linkedMapOf<String, String>()
    if (f.petIds.isEmpty()) e["pets"] = "먹을 아이를 골라 주세요"
    if (f.medicationId == null) e["med"] = "약을 골라 주세요"
    val dose = f.dose.trim()
    if (dose.toDecimal()?.takeIf { it > 0 } == null) e["dose"] = if (dose.isEmpty()) "한 번에 줄 양을 넣어 주세요" else "0보다 큰 숫자로 넣어 주세요"
    when (f.frequency) {
        MedFrequency.everyNDays -> if ((f.everyDays.toWholeNumber() ?: 0) < 2) e["everyDays"] = "2 이상 숫자로 넣어 주세요"
        MedFrequency.weekdays -> if (f.weekdays.isEmpty()) e["weekdays"] = "요일을 하나 이상 골라 주세요"
        MedFrequency.everyNHours -> if ((f.everyHours.toWholeNumber() ?: 0) < 1) e["everyHours"] = "1 이상 숫자로 넣어 주세요"
        else -> Unit
    }
    if (f.frequency in timeFrequencies && f.times.isEmpty()) e["times"] = "시간을 하나 이상 넣어 주세요"
    return e
}

/** 저장: 고른 아이마다 한 줄(같은 설정·같은 `activeFrom`, `seriesId` = 자기 `id`). 같은 약이 이미 열려 있어도 막지 않음. */
suspend fun addSchedules(dao: OkcheDao, f: ScheduleFields, unit: DoseUnit, now: Long = System.currentTimeMillis()): List<MedSchedule> {
    val medId = f.medicationId ?: return emptyList()
    val hours = f.frequency == MedFrequency.everyNHours
    val rows = f.petIds.map { petId ->
        val id = app.okche.store.newId()
        MedSchedule(
            id = id, createdAt = now, petId = petId, medicationId = medId, seriesId = id, amount = f.dose.toDecimal() ?: 1.0, unit = unit,
            frequency = f.frequency, times = if (f.frequency in timeFrequencies) f.times.distinct().sorted() else emptyList(),
            everyDays = if (f.frequency == MedFrequency.everyNDays) f.everyDays.toWholeNumber() else null,
            startDate = if (f.frequency == MedFrequency.everyNDays) f.startDate.toString() else null,
            weekdays = if (f.frequency == MedFrequency.weekdays) f.weekdays.sorted() else emptyList(),
            everyHours = if (hours) f.everyHours.toWholeNumber() else null, firstAt = if (hours) f.firstAt else null,
            shiftOnLate = f.shiftOnLate, howTo = f.howTo, reminder = f.frequency != MedFrequency.asNeeded && f.reminder,
            leadMinutes = f.leadMinutes, activeFrom = now,
        )
    }
    dao.insertSchedules(rows)
    return rows
}

/** 알림 스위치(바로 저장 — 그 줄을 고침, 5장). */
suspend fun setReminder(dao: OkcheDao, s: MedSchedule, on: Boolean, now: Long = System.currentTimeMillis()) = dao.update(s.copy(reminder = on, updatedAt = now))
