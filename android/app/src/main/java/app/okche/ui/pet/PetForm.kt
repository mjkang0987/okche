package app.okche.ui.pet

import androidx.compose.ui.graphics.Color
import app.okche.store.InsulinPlan
import app.okche.store.InsulinSlot
import app.okche.store.OkcheDao
import app.okche.store.Pet
import app.okche.store.Record
import app.okche.store.RecordKind
import app.okche.store.Sex
import app.okche.store.Species
import app.okche.store.Syringe
import android.util.Log
import kotlinx.coroutines.CancellationException
import org.json.JSONObject
import java.io.Serializable
import java.time.LocalDate

// 아이 등록 입력과 저장 규칙(iOS와 같음). 화면과 떼어 두어 테스트에서 바로 부른다.

/** 품종 — 화면 글자 그대로 저장(문서 2장). */
val breeds = mapOf(
    Species.cat to listOf(
        "코리안숏헤어", "러시안블루", "페르시안", "엑조틱", "브리티시숏헤어", "스코티시폴드", "먼치킨", "랙돌", "메인쿤",
        "아메리칸숏헤어", "샴", "노르웨이숲", "벵갈", "아비시니안", "스핑크스", "터키시앙고라", "믹스 · 잘 모르겠어요",
    ),
    Species.dog to listOf(
        "말티즈", "토이푸들", "포메라니안", "비숑", "시츄", "치와와", "요크셔테리어", "슈나우저", "웰시코기", "닥스훈트",
        "진돗개", "시바", "비글", "보더콜리", "사모예드", "골든리트리버", "래브라도", "프렌치불독", "퍼그", "믹스 · 잘 모르겠어요",
    ),
)

/** 털색 — 이름을 저장하고 동그라미 색은 화면에만 쓴다. 밝은 색은 바탕과 구분되게 테두리를 두른다. */
data class FurColor(val name: String, val color: Color, val outlined: Boolean = false)

val furColors = mapOf(
    Species.cat to listOf(
        FurColor("치즈", Color(0xFFF4A95E)), FurColor("고등어", Color(0xFFAAA398), outlined = true),
        FurColor("턱시도", Color(0xFF3E3739)), FurColor("회색", Color(0xFFA9B2BC), outlined = true),
    ),
    Species.dog to listOf(
        FurColor("흰색", Color(0xFFF4F1EA), outlined = true), FurColor("누런색", Color(0xFFD9A75F)),
        FurColor("갈색", Color(0xFF8A5A3B)), FurColor("검은색", Color(0xFF2E2A2B)),
    ),
)

/** 인슐린 시간(`HH:mm`)·양(입력 글자) 한 줄. */
data class InsulinRow(val time: String, val units: String = "") : Serializable

/** 등록 화면 입력 그대로. Serializable이라 rememberSaveable로 화면을 다시 만들어도 남는다. */
data class PetForm(
    val name: String = "",
    val species: Species = Species.cat,
    val breed: String = breeds.getValue(Species.cat).first(),
    val furColor: String = furColors.getValue(Species.cat).first().name,
    val sex: Sex? = null,
    val neutered: Boolean? = null,
    val birthDate: LocalDate? = null,
    /** '생일을 몰라요' — 켜면 생일 대신 대략 나이를 받는다. */
    val birthUnknown: Boolean = false,
    val age: String = "",
    val weight: String = "",
    val diagnosis: String = "",
    val hasDiabetes: Boolean = false,
    val insulinName: String = "",
    val insulinRows: List<InsulinRow> = listOf(InsulinRow("08:00"), InsulinRow("20:00")),
    val syringe: Syringe = Syringe.unknown,
) : Serializable {
    /** 종을 바꾸면 품종·털색을 그 종의 첫째로 되돌린다. */
    fun withSpecies(species: Species) =
        copy(species = species, breed = breeds.getValue(species).first(), furColor = furColors.getValue(species).first().name)
}

// iOS와 같은 숫자 규칙: ASCII 숫자만(전각·아랍 숫자 거절), 소수점은 점이나 쉼표(일부 키보드) 하나.
// Kotlin toDouble이 받는 '1.5f'·'1e3'·'+3'·'NaN' 같은 글은 숫자가 아니다.
private val decimalPattern = Regex("[0-9]+([.,][0-9]*)?|[.,][0-9]+")
private val integerPattern = Regex("[0-9]+")

/** 소수 입력. 유한한 값만(아주 긴 숫자가 무한대가 되면 숫자 아님). */
private fun String.toDecimal() = trim().takeIf { decimalPattern.matches(it) }?.replace(',', '.')?.toDouble()?.takeIf { it.isFinite() }

/** 정수 입력(나이). 너무 길어 Int에 안 들어가면 숫자 아님. */
private fun String.toWholeNumber() = trim().takeIf { integerPattern.matches(it) }?.toIntOrNull()

/**
 * 검사해서 오류가 있으면 아무것도 저장하지 않고 오류를 돌려준다(칸 열쇠 → 문구, 화면 위에서부터 순서).
 * 칸 열쇠: name · birthDate · age · weight · insulinRows · time{i} · units{i}, 저장소 오류는 save. 오류가 없으면 아이 + 체중 기록 + 인슐린 계획을 한 트랜잭션으로 저장하고 빈 맵.
 */
suspend fun registerPet(
    dao: OkcheDao,
    form: PetForm,
    now: Long = System.currentTimeMillis(),
    today: LocalDate = LocalDate.now(),
): Map<String, String> {
    val errors = linkedMapOf<String, String>()
    val name = form.name.trim()
    if (name.isEmpty()) errors["name"] = "이름을 넣어 주세요"

    var birth: LocalDate? = null
    if (form.birthUnknown) {
        if (form.age.isNotBlank()) {
            val age = form.age.toWholeNumber()
            // 2/29에서 해를 빼면 java.time 규칙대로 2/28.
            if (age == null || age !in 0..30) errors["age"] = "0에서 30 사이로 넣어 주세요" else birth = today.minusYears(age.toLong())
        }
    } else {
        birth = form.birthDate
        if (birth != null && birth > today) errors["birthDate"] = "오늘보다 뒤 날짜는 넣을 수 없어요"
    }

    var kg: Double? = null
    if (form.weight.isNotBlank()) {
        // 그램으로 반올림해 0이 되는 값(0.0004 등)도 체중이 아니다.
        kg = form.weight.toDecimal()?.takeIf { it > 0 && it <= 100 && Math.round(it * 1000) >= 1 }
        if (kg == null) errors["weight"] = "0보다 크고 100 이하인 숫자로 넣어 주세요"
    }

    val slots = mutableListOf<InsulinSlot>()
    if (form.hasDiabetes) {
        // 화면은 마지막 줄을 못 빼게 하지만 저장 함수에서도 막는다.
        if (form.insulinRows.isEmpty()) errors["insulinRows"] = "시간을 하나 이상 넣어 주세요"
        form.insulinRows.forEachIndexed { i, row ->
            if (form.insulinRows.take(i).any { it.time == row.time }) errors["time$i"] = "같은 시간이 두 번 있어요"
            val units = row.units.toDecimal()?.takeIf { it > 0 && it <= 100 }
            when {
                row.units.isBlank() -> errors["units$i"] = "양을 넣어 주세요"
                units == null -> errors["units$i"] = "0보다 크고 100 이하인 숫자로 넣어 주세요"
                else -> slots += InsulinSlot(row.time, units)
            }
        }
    }
    if (errors.isNotEmpty()) return errors

    val pet = Pet(
        createdAt = now, name = name, species = form.species, breed = form.breed, furColor = form.furColor,
        sex = form.sex, neutered = form.neutered, birthDate = birth?.toString(), birthEstimated = form.birthUnknown && birth != null,
        diagnosis = form.diagnosis.trim().ifEmpty { null }, hasDiabetes = form.hasDiabetes,
    )
    val weight = kg?.let {
        Record(createdAt = now, petId = pet.id, kind = RecordKind.weight, occurredAt = now,
            data = JSONObject().put("grams", Math.round(it * 1000)).toString())
    }
    val plan = if (form.hasDiabetes) {
        InsulinPlan(createdAt = now, petId = pet.id, name = form.insulinName.trim().ifEmpty { null }, syringe = form.syringe,
            slots = slots.sortedBy { it.time }, activeFrom = now)
    } else {
        null
    }
    try {
        dao.insertPetRegistration(pet, weight, plan)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // 저장소 오류는 칸 오류가 아니라 '모시기' 위에 보인다. 트랜잭션이라 아무것도 남지 않는다.
        Log.e("Okche", "아이 등록 저장 실패", e)
        return mapOf("save" to "저장하지 못했어요. 다시 해 주세요.")
    }
    return errors
}
