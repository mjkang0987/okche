package app.okche.ui.pet

import android.util.Log
import app.okche.store.InsulinPlan
import app.okche.store.OkcheDao
import app.okche.store.RecordKind
import app.okche.store.RestPeriod
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.Serializable
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.Period
import java.time.temporal.ChronoUnit

// 아이 정보 고치기·인슐린 설정 입력과 저장 규칙(plan.md 6-2, iOS PetEditing·InsulinSetup과 같음).

/** 아이 정보 고치기 입력. Serializable이라 rememberSaveable로 화면을 다시 만들어도 남는다. */
data class PetEditForm(
    val petId: String,
    val name: String,
    val species: Species,
    val breed: String,
    val furColor: String,
    val sex: Sex?,
    val neutered: Boolean?,
    val birthDate: LocalDate?,
    val birthUnknown: Boolean,
    val age: String,
    val familyDate: LocalDate?,
    val diagnosis: String,
    val hasDiabetes: Boolean,
    /** 휴양 중 · 관리 쉬기 */
    val resting: Boolean,
    /** 열 때 넣어 둔 나이. 이걸 안 고치면 어림잡은 생일을 그대로 둔다(저장할 때마다 생일이 밀리지 않게). */
    val loadedAge: String,
) : Serializable {
    /** 종을 바꾸면 품종·털색을 그 종의 첫째로 되돌린다(아이 등록과 같음). */
    fun withSpecies(species: Species) = if (species == this.species) this else
        copy(species = species, breed = breeds.getValue(species).first(), furColor = furColors.getValue(species).first().name)

    companion object {
        /** 저장된 아이에서 입력을 만든다. 어림잡은 생일이면 오늘 기준 만 나이를 넣어 둔다. 아이가 없으면(지움) null. */
        suspend fun load(dao: OkcheDao, petId: String, today: LocalDate = LocalDate.now()): PetEditForm? {
            val pet = dao.pet(petId) ?: return null
            val birth = pet.birthDate?.let(LocalDate::parse)
            val age = if (pet.birthEstimated && birth != null) Period.between(birth, today).years.toString() else ""
            return PetEditForm(
                petId = pet.id, name = pet.name, species = pet.species, breed = pet.breed, furColor = pet.furColor, sex = pet.sex,
                neutered = pet.neutered, birthDate = birth, birthUnknown = pet.birthEstimated, age = age,
                familyDate = pet.familyDate?.let(LocalDate::parse), diagnosis = pet.diagnosis.orEmpty(), hasDiabetes = pet.hasDiabetes,
                resting = dao.openRestPeriods(pet.id).isNotEmpty(), loadedAge = age,
            )
        }
    }
}

/**
 * 아이 정보 저장. 오류가 있으면 아무것도 바꾸지 않고 오류를 돌려준다(칸 열쇠 → 문구, 화면 위에서부터 순서).
 * 칸 열쇠: name · birthDate · age · familyDate · insulinPlan, 저장소 오류는 save. 저장되면 빈 맵.
 * 당뇨를 끄면 열린 인슐린 계획을 끝내고, 휴양은 켤 때 열린 기간이 없을 때만 새로·끌 때 열린 기간 모두 닫는다(문서 2-1).
 */
suspend fun savePetEdit(
    dao: OkcheDao,
    form: PetEditForm,
    now: Long = System.currentTimeMillis(),
    today: LocalDate = LocalDate.now(),
): Map<String, String> {
    try {
        val pet = dao.pet(form.petId) ?: return mapOf("save" to SAVE_ERROR)
        val errors = linkedMapOf<String, String>()
        val name = form.name.trim()
        if (name.isEmpty()) errors["name"] = "이름을 넣어 주세요"

        var birth: String? = null
        if (form.birthUnknown) {
            if (pet.birthEstimated && form.age.trim() == form.loadedAge.trim()) {
                birth = pet.birthDate
            } else if (form.age.isNotBlank()) {
                val age = form.age.toWholeNumber()
                if (age == null || age !in 0..30) errors["age"] = AGE_ERROR else birth = today.minusYears(age.toLong()).toString()
            }
        } else {
            if (form.birthDate != null && form.birthDate > today) errors["birthDate"] = FUTURE_DAY_ERROR
            birth = form.birthDate?.toString()
        }
        if (form.familyDate != null && form.familyDate > today) errors["familyDate"] = FUTURE_DAY_ERROR

        val plans = dao.openInsulinPlans(pet.id)
        if (form.hasDiabetes && plans.isEmpty()) errors["insulinPlan"] = "인슐린 시간과 양을 먼저 설정해 주세요"
        if (errors.isNotEmpty()) return errors

        val edited = pet.copy(
            name = name, species = form.species, breed = form.breed, furColor = form.furColor, sex = form.sex, neutered = form.neutered,
            // 나이를 비워 날짜가 없으면 어림잡음 표시도 하지 않는다(아이 등록과 같음).
            birthDate = birth, birthEstimated = form.birthUnknown && birth != null,
            familyDate = form.familyDate?.toString(), diagnosis = form.diagnosis.trim().ifEmpty { null }, hasDiabetes = form.hasDiabetes,
        )
        val rests = dao.openRestPeriods(pet.id)
        dao.savePetEdit(
            pet = edited.takeIf { it != pet }?.copy(updatedAt = now),
            closePlans = if (form.hasDiabetes) emptyList() else plans.map { it.copy(activeUntil = now, updatedAt = now) },
            newRest = if (form.resting && rests.isEmpty()) RestPeriod(createdAt = now, petId = pet.id, startedAt = now) else null,
            closeRests = if (form.resting) emptyList() else rests.map { it.copy(endedAt = now, updatedAt = now) },
        )
        return emptyMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 저장소 오류는 칸 오류가 아니라 '저장하기' 위에 보인다. 트랜잭션이라 아무것도 남지 않는다.
        Log.e("Okche", "아이 정보 저장 실패", e)
        return mapOf("save" to SAVE_ERROR)
    }
}

/**
 * 동행 N일: 가족 된 날을 1일로 센다(기기 달력, 2026-10-31 사용자 확정 — 데이터 2장).
 */
fun companionDays(familyDate: String?, today: LocalDate = LocalDate.now()): Long? {
    val start = familyDate?.let(LocalDate::parse) ?: return null
    return ChronoUnit.DAYS.between(start, today).takeIf { it >= 0 }?.plus(1)
}

/** 아이 정보의 체중 줄: '4.62kg · 10월 25일'(기록 날짜), 없으면 null. */
suspend fun latestWeightText(dao: OkcheDao, petId: String): String? {
    val record = dao.latestRecord(petId, RecordKind.weight) ?: return null
    val grams = JSONObject(record.data).optLong("grams", -1).takeIf { it >= 0 } ?: return null
    val kg = BigDecimal.valueOf(grams, 3).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    // 기록 날짜는 6시 기준 day가 아니라 잰 시각의 달력 날짜(iOS와 같음).
    val day = Instant.ofEpochMilli(record.occurredAt).atZone(ZoneId.systemDefault()).toLocalDate()
    return "${kg}kg · ${day.monthValue}월 ${day.dayOfMonth}일"
}

/** 알림 보낼 때 고르기(문서 7장 leadMinutes). */
val insulinLeadChoices = listOf(0 to "맞는 시간에", 5 to "5분 전", 10 to "10분 전", 15 to "15분 전", 30 to "30분 전", 60 to "1시간 전")

/** 인슐린 설정 입력. */
data class InsulinSetupForm(
    val petId: String,
    val name: String = "",
    val syringe: Syringe = Syringe.unknown,
    val rows: List<InsulinRow> = listOf(InsulinRow("08:00"), InsulinRow("20:00")),
    /** 인슐린 시간 알림 — 설정 notify.insulinTime(모든 아이 공통) */
    val notify: Boolean = true,
    /** 밤에도 울리기 — 디자인 처음 값 켜짐 */
    val ringDuringQuiet: Boolean = true,
    val leadMinutes: Int = 10,
) : Serializable {
    companion object {
        /** 열린 계획이 있으면 그 값, 없으면 아이 등록과 같은 처음 값. */
        suspend fun load(dao: OkcheDao, petId: String): InsulinSetupForm {
            val notify = dao.loadOrCreateSetting().notify.insulinTime
            val plan = dao.openInsulinPlans(petId).firstOrNull() ?: return InsulinSetupForm(petId, notify = notify)
            return InsulinSetupForm(
                petId = petId, name = plan.name.orEmpty(), syringe = plan.syringe,
                rows = plan.slots.map { InsulinRow(it.time, BigDecimal.valueOf(it.units).stripTrailingZeros().toPlainString()) },
                notify = notify, ringDuringQuiet = plan.ringDuringQuiet, leadMinutes = plan.leadMinutes,
            )
        }
    }
}

/**
 * 인슐린 설정 저장. 시간·양이 바뀌면 열린 계획을 모두 닫고 새 계획, 이름·주사기·밤에도 울리기·몇 분 전만 바뀌면 그 계획을 고친다(문서 7장·5장).
 * 인슐린을 설정했으면 당뇨 아이로 둔다. 오류 열쇠는 time{i}·units{i}·insulinRows·save, 저장되면 빈 맵.
 */
suspend fun saveInsulinSetup(dao: OkcheDao, form: InsulinSetupForm, now: Long = System.currentTimeMillis()): Map<String, String> {
    val (errors, slots) = checkInsulinRows(form.rows)
    if (errors.isNotEmpty()) return errors
    try {
        val pet = dao.pet(form.petId) ?: return mapOf("save" to SAVE_ERROR)
        val plans = dao.openInsulinPlans(pet.id)
        val name = form.name.trim().ifEmpty { null }
        val current = plans.firstOrNull()
        var updates = emptyList<InsulinPlan>()
        var newPlan: InsulinPlan? = null
        if (current != null && current.slots.sortedBy { it.time } == slots) {
            val edited = current.copy(name = name, syringe = form.syringe, ringDuringQuiet = form.ringDuringQuiet, leadMinutes = form.leadMinutes)
            if (edited != current) updates = listOf(edited.copy(updatedAt = now))
        } else {
            updates = plans.map { it.copy(activeUntil = now, updatedAt = now) }
            newPlan = InsulinPlan(
                createdAt = now, petId = pet.id, name = name, syringe = form.syringe, slots = slots,
                ringDuringQuiet = form.ringDuringQuiet, leadMinutes = form.leadMinutes, activeFrom = now,
            )
        }
        dao.saveInsulinSetup(updates, newPlan, pet.takeIf { !it.hasDiabetes }?.copy(hasDiabetes = true, updatedAt = now), form.notify, now)
        return emptyMap()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.e("Okche", "인슐린 설정 저장 실패", e)
        return mapOf("save" to SAVE_ERROR)
    }
}
