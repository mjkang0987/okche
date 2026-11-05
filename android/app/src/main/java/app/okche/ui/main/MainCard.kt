package app.okche.ui.main

import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.Species
import app.okche.ui.pet.petAge
import java.time.LocalDate
import java.time.Year

// 문안 탭 규칙(plan.md 7-0): 표시 알약·아이 카드 줄·오늘 기록. iOS `MainCard.swift`와 같은 규칙.

/** 표시 알약: '오늘 생일'(어림잡은 생일은 안 띄움) > '가족 된 날'(그날이 아닌 해). 2/29는 평년엔 2/28. [day]는 기기 달력 오늘(나이·동행과 같은 기준). */
fun mainPill(birthDate: String?, birthEstimated: Boolean, familyDate: String?, day: String): String? {
    if (!birthEstimated && sameMonthDay(birthDate, day)) return "오늘 생일"
    if (sameMonthDay(familyDate, day) && familyDate?.take(4) != day.take(4)) return "가족 된 날"
    return null
}

private fun sameMonthDay(date: String?, day: String): Boolean {
    if (date?.length != 10 || day.length != 10) return false
    val md = date.takeLast(5)
    val today = day.takeLast(5)
    if (md == today) return true
    val year = day.take(4).toIntOrNull() ?: return false
    return md == "02-29" && today == "02-28" && !Year.isLeap(year.toLong())
}

/** '고양이 · 코리안숏헤어 · 7살' — 품종이 비었거나 생일이 없으면 그 칸을 뺀다. */
fun mainDetail(pet: Pet, today: LocalDate = LocalDate.now()) =
    listOfNotNull(if (pet.species == Species.cat) "고양이" else "강아지", pet.breed.ifEmpty { null }, petAge(pet.birthDate, today))
        .joinToString(" · ")

/** 머리 날짜 '10월 25일 일요일'(6시 기준 [day]). */
fun mainDayTitle(day: String): String {
    val date = runCatching { LocalDate.parse(day) }.getOrNull() ?: return day
    val weekday = listOf("월", "화", "수", "목", "금", "토", "일")[date.dayOfWeek.value - 1]
    return "${date.monthValue}월 ${date.dayOfMonth}일 ${weekday}요일"
}

/** 오늘 기록에 넣는 종류(탕약·혈당·인슐린·옥체는 그 단계에서). */
private val mainKinds = setOf(RecordKind.vomit, RecordKind.stool, RecordKind.urine, RecordKind.meal, RecordKind.snack, RecordKind.med)

/** 오늘 기록: 그 아이의 그날 지우지 않은 기록, 이른 것 위. */
fun mainTodayRecords(records: List<Record>, petId: String, day: String) =
    records.filter { it.petId == petId && it.day == day && it.deletedAt == null && it.kind in mainKinds }.sortedBy { it.occurredAt }

/** 줄의 쉬운 말(보드 Main — 구토는 없음). */
fun plainWord(kind: RecordKind) = when (kind) {
    RecordKind.stool -> "변"
    RecordKind.urine -> "소변"
    RecordKind.meal -> "식사"
    RecordKind.snack -> "간식"
    RecordKind.med -> "약"
    else -> null
}
