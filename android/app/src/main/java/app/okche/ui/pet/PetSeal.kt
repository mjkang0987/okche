package app.okche.ui.pet

import app.okche.store.Pet
import java.time.LocalDate
import java.time.Period

// 호칭·나이 표기·지금 보는 아이(plan.md 6-3). iOS `PetTitle`과 같은 규칙.

/** 생일부터 오늘까지 꽉 찬 해. 2/29생은 다음 해 2/28에 1살(기기 달력). */
private fun fullYears(birth: LocalDate, today: LocalDate): Int {
    var years = Period.between(birth, today).years
    if (!birth.plusYears(years + 1L).isAfter(today)) years++
    return years
}

/** 생일(어림 포함)부터 1살 생일 전날까지 '아기씨', 1살 생일부터 '마마'. 생일이 없으면 '아기씨'. */
fun honorific(birthDate: String?, today: LocalDate = LocalDate.now()): String {
    val birth = birthDate?.let(LocalDate::parse) ?: return "아기씨"
    return if (fullYears(birth, today) >= 1) "마마" else "아기씨"
}

/** '콩이 마마' */
fun petTitle(pet: Pet, today: LocalDate = LocalDate.now()) = "${pet.name} ${honorific(pet.birthDate, today)}"

/** 만 나이 '7살', 1살 미만은 만 개월 '3개월'(1개월 미만 '0개월'). 생일이 없으면 null. */
fun petAge(birthDate: String?, today: LocalDate = LocalDate.now()): String? {
    val birth = birthDate?.let(LocalDate::parse) ?: return null
    val years = fullYears(birth, today)
    if (years >= 1) return "${years}살"
    var months = Period.between(birth, today).toTotalMonths()
    if (!birth.plusMonths(months + 1).isAfter(today)) months++
    return "${maxOf(months, 0)}개월"
}

/** 지금 보는 아이: 이 기기만의 설정 `lastPetId`, 없거나 지운 아이면 가장 먼저 모신 아이. `pets`는 지운 줄 뺀 `createdAt` 순. */
fun currentPet(pets: List<Pet>, lastPetId: String?): Pet? = pets.firstOrNull { it.id == lastPetId } ?: pets.firstOrNull()
