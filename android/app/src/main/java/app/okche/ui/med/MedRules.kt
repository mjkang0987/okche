package app.okche.ui.med

import app.okche.store.DoseUnit
import app.okche.store.MedCategory
import app.okche.store.MedForm
import app.okche.store.MedSchedule
import app.okche.store.Medication
import app.okche.store.OkcheDao
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.ui.pet.toDecimal
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

// 약방 규칙(plan.md 8-1, 데이터 4장). iOS `MedRules.swift`와 같은 규칙.

val medCategories = listOf(MedCategory.medicine to "약", MedCategory.supplement to "보조제")
val medForms = listOf(MedForm.pill to "알약", MedForm.powder to "가루", MedForm.liquid to "물약", MedForm.topical to "연고·안약", MedForm.injection to "주사")

fun medFormTitle(form: MedForm) = medForms.first { it.first == form }.second

/** 단위는 모양을 따른다(데이터 4장). */
fun unitOf(form: MedForm) = when (form) {
    MedForm.pill -> DoseUnit.pill
    MedForm.powder -> DoseUnit.pack
    MedForm.liquid, MedForm.injection -> DoseUnit.ml
    MedForm.topical -> DoseUnit.drop
}

fun unitTitle(unit: DoseUnit) = when (unit) {
    DoseUnit.pill -> "알"
    DoseUnit.pack -> "포"
    DoseUnit.ml -> "ml"
    DoseUnit.drop -> "방울"
}

/** 숫자 글자: 소수 둘째 자리까지, 끝 0은 뺌('1'·'0.5'·'2.25'). */
fun medNumber(value: Double): String = BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

/** 지금 남은 양 = 적은 양 − 적은 뒤 그 약의 `given`·`vomited` 기록 양(같은 단위만, 지운 기록 빼고). 안 적었으면 null. */
fun medRemaining(med: Medication, records: List<Record>): Double? {
    val stock = med.stockAmount ?: return null
    val used = records.filter { r ->
        if (r.kind != RecordKind.med || r.deletedAt != null || (med.stockCountedAt != null && r.occurredAt <= med.stockCountedAt)) return@filter false
        val json = runCatching { JSONObject(r.data) }.getOrNull() ?: return@filter false
        json.optString("medicationId") == med.id && json.optString("unit") == med.unit.name && json.optString("status") in setOf("given", "vomited")
    }.sumOf { runCatching { JSONObject(it.data).optDouble("amount", 0.0) }.getOrDefault(0.0) }
    return stock - used
}

/** 약 줄 아래 글자 '알약 · 1알씩 · 남은 24알' / '… · 남은 양 안 적음'('○일분'은 8-2). */
fun medDetail(med: Medication, records: List<Record>): String {
    val unit = unitTitle(med.unit)
    val left = medRemaining(med, records)?.let { "남은 ${medNumber(maxOf(it, 0.0))}$unit" } ?: "남은 양 안 적음"
    return "${medFormTitle(med.form)} · ${medNumber(med.doseAmount)}${unit}씩 · $left"
}

/** 약방 목록: 묶음(약·보조제)마다 들인 순, 뺀 약 빼고. 빈 묶음은 뺀다. */
fun groupedMeds(meds: List<Medication>): List<Pair<String, List<Medication>>> {
    val listed = meds.filter { it.deletedAt == null }.sortedBy { it.createdAt }
    return medCategories.mapNotNull { (category, title) -> listed.filter { it.category == category }.takeIf { it.isNotEmpty() }?.let { title to it } }
}

/** 들이기·고치기 칸. */
data class MedFields(
    val name: String = "",
    val shortName: String = "",
    val category: MedCategory = MedCategory.medicine,
    val form: MedForm = MedForm.pill,
    val dose: String = "1",
    val stock: String = "",
    val memo: String = "",
)

/** 고치기: 지금 값(남은 양 칸은 계산한 지금 남은 양). */
fun medFormOf(med: Medication, records: List<Record>) = MedFields(
    med.name, med.shortName.orEmpty(), med.category, med.form, medNumber(med.doseAmount),
    medRemaining(med, records)?.let { medNumber(maxOf(it, 0.0)) }.orEmpty(), med.memo.orEmpty(),
)

private fun String.trimmedOrNull() = trim().takeIf { it.isNotEmpty() }

/** 칸 검사: 이름 필수, 짧은 이름 6자까지, 한 번에 0보다 큰 수, 남은 양 0 이상(선택). 칸 이름 → 문구. */
fun medErrors(f: MedFields): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    if (f.name.trimmedOrNull() == null) errors["name"] = "약 이름을 넣어 주세요"
    if ((f.shortName.trimmedOrNull()?.length ?: 0) > 6) errors["shortName"] = "6자까지 넣어 주세요"
    val dose = f.dose.trimmedOrNull()
    if (dose?.toDecimal()?.takeIf { it > 0 } == null) errors["dose"] = if (dose == null) "한 번에 줄 양을 넣어 주세요" else "0보다 큰 숫자로 넣어 주세요"
    if (f.stock.trimmedOrNull()?.let { it.toDecimal() == null } == true) errors["stock"] = "0 이상 숫자로 넣어 주세요"
    return errors
}

/** 약방에 들이기. 남은 양을 적었으면 지금 센 것으로. */
suspend fun addMed(dao: OkcheDao, f: MedFields, now: Long = System.currentTimeMillis()): Medication {
    val stock = f.stock.trimmedOrNull()?.toDecimal()
    return Medication(
        createdAt = now, name = f.name.trim(), shortName = f.shortName.trimmedOrNull(), category = f.category, form = f.form,
        doseAmount = f.dose.toDecimal() ?: 1.0, unit = unitOf(f.form), stockAmount = stock, stockCountedAt = stock?.let { now },
        memo = f.memo.trimmedOrNull(),
    ).also { dao.insert(it) }
}

/** 고치기: 바꾼 게 없으면 저장하지 않음(거짓). 남은 양 칸을 바꿨을 때만 다시 셈(비우면 안 적음). */
suspend fun updateMed(dao: OkcheDao, med: Medication, original: MedFields, f: MedFields, now: Long = System.currentTimeMillis()): Boolean {
    if (f == original) return false
    val stockChanged = f.stock != original.stock
    val stock = if (stockChanged) f.stock.trimmedOrNull()?.toDecimal() else med.stockAmount
    dao.update(
        med.copy(
            name = f.name.trimmedOrNull() ?: med.name, shortName = f.shortName.trimmedOrNull(), category = f.category, form = f.form,
            unit = unitOf(f.form), doseAmount = f.dose.toDecimal() ?: med.doseAmount, memo = f.memo.trimmedOrNull(),
            stockAmount = stock, stockCountedAt = if (stockChanged) stock?.let { now } else med.stockCountedAt, updatedAt = now,
        ),
    )
    return true
}

private fun particle(name: String, withFinal: String, without: String, unknown: String): String {
    val last = name.lastOrNull() ?: return name
    if (last !in '가'..'힣') return name + unknown
    return name + if ((last - '가') % 28 == 0) without else withFinal
}

/** 이름 + 이/가. */
fun withSubjectParticle(name: String) = particle(name, "이", "가", "이(가)")

/** 이름 + 은/는. */
fun withTopicParticle(name: String) = particle(name, "은", "는", "은(는)")

/** 이 약을 먹고 있는 아이 이름(열린 약 주기 — `activeUntil` 없음, 지우지 않음). 비면 뺄 수 있음. */
fun medTakers(med: Medication, schedules: List<MedSchedule>, pets: List<Pet>): List<String> {
    val ids = schedules.filter { it.medicationId == med.id && it.deletedAt == null && it.activeUntil == null }.map { it.petId }.toSet()
    return pets.filter { it.id in ids && it.deletedAt == null }.sortedBy { it.createdAt }.map { it.name }
}

/** '위장약 시럽은 보리가 먹고 있는 약이에요. …'(여럿이면 '콩이·보리가'). */
fun medBlockedMessage(med: Medication, takers: List<String>) =
    "${withTopicParticle(med.name)} ${withSubjectParticle(takers.joinToString("·"))} 먹고 있는 약이에요. 먼저 탕약에서 이 약을 '그만 먹이기'로 바꿔 주세요."

/** 약방에서 빼기: `deletedAt`만(지난 기록의 약 이름은 남음 — 1-4). */
suspend fun removeMed(dao: OkcheDao, med: Medication, now: Long = System.currentTimeMillis()) = dao.update(med.copy(deletedAt = now, updatedAt = now))
