package app.okche.ui.food

import app.okche.store.Food
import app.okche.store.FoodChange
import app.okche.store.FoodChangeMethod
import app.okche.store.OkcheDao
import java.time.LocalDate
import java.time.temporal.ChronoUnit

// 사료 바꾸기 규칙(plan.md 7-4a, 데이터 8장). iOS `FoodRules.swift`와 같은 규칙.

/** '사료 다 바꿨나요?'를 묻는 날(먹기 시작한 날 = 1일째). */
val foodSteps = listOf(7, 10, 12, 14)

private val latestFirst = compareBy<FoodChange>({ it.startedOn }, { it.createdAt })

/** 그날(`YYYY-MM-DD`) 먹던 사료 바꾸기: 그날까지 시작한 것 중 가장 늦은 `startedOn`(같으면 늦게 만든 것). */
fun foodChangeAt(day: String, petId: String, changes: List<FoodChange>): FoodChange? =
    changes.filter { it.petId == petId && it.deletedAt == null && it.startedOn <= day }.maxWithOrNull(latestFirst)

/** 그날 먹던 사료 ID — 수라 기록 `foodId`. */
suspend fun foodIdAt(dao: OkcheDao, petId: String?, day: String): String? =
    petId?.let { foodChangeAt(day, it, dao.foodChanges())?.foodId }

/** 전에 먹던 사료: 이 아이가 먹었던 사료 이름, 최근 바꾼 순, 지금 먹는 사료는 뺌. */
fun previousFoodNames(petId: String, today: String, changes: List<FoodChange>, foods: List<Food>): List<String> {
    val current = foodChangeAt(today, petId, changes)
    return changes.filter { it.petId == petId && it.deletedAt == null && it.foodId != current?.foodId }
        .sortedWith(latestFirst.reversed())
        .map { it.foodId }.distinct()
        .mapNotNull { id -> foods.firstOrNull { it.id == id }?.name }
}

/** 사료 바꾸기 저장: 앞뒤 빈칸 뺀 이름이 같은 사료가 있으면 그 사료. */
suspend fun saveFoodChange(dao: OkcheDao, petId: String, name: String, startedOn: String, method: FoodChangeMethod, now: Long = System.currentTimeMillis()) =
    dao.saveFoodChange(petId, name.trim(), startedOn, method, now)

/** 아이 정보 줄 '가수분해 처방식 · 10/14부터'. */
fun foodLine(change: FoodChange, food: Food?): String {
    val date = runCatching { LocalDate.parse(change.startedOn) }.getOrNull()
    return food?.name.orEmpty() + (date?.let { " · ${it.monthValue}/${it.dayOfMonth}부터" } ?: "")
}

/** 먹기 시작한 날을 1일째로 센 오늘. */
fun foodDayNumber(startedOn: String, today: String): Int? = runCatching {
    ChronoUnit.DAYS.between(LocalDate.parse(startedOn), LocalDate.parse(today)).toInt() + 1
}.getOrNull()

/** '사료 다 바꿨나요?' 카드: 단계 날부터 다음 단계 전날까지(14일째는 그날만), 누른 단계·다 바꿨으면 안 띄움. (단계, N일째) */
fun foodCheck(change: FoodChange?, today: String): Pair<Int, Int>? {
    if (change == null || change.finishedOn != null) return null
    val day = foodDayNumber(change.startedOn, today)?.takeIf { it <= 14 } ?: return null
    val step = foodSteps.lastOrNull { it <= day }?.takeIf { it > change.checkedStep } ?: return null
    return step to day
}

/** 카드 안내 '아직이면 10일·12일·14일째에 한 번 더 물어볼게요.' — 14일째는 없음. */
fun foodLaterNote(step: Int): String? = foodSteps.filter { it > step }.takeIf { it.isNotEmpty() }
    ?.let { later -> "아직이면 ${later.joinToString("·") { "${it}일" }}째에 한 번 더 물어볼게요." }

suspend fun foodNotYet(dao: OkcheDao, change: FoodChange, step: Int, now: Long = System.currentTimeMillis()) =
    dao.update(change.copy(checkedStep = step, updatedAt = now))

suspend fun foodFinished(dao: OkcheDao, change: FoodChange, today: String, now: Long = System.currentTimeMillis()) =
    dao.update(change.copy(finishedOn = today, updatedAt = now))
