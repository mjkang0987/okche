package app.okche.ui.record

import app.okche.store.OkcheDao
import app.okche.store.Record
import app.okche.store.RecordKind
import org.json.JSONArray
import org.json.JSONObject
import java.time.ZoneId

// 구토·매화·소피 자세히 시트·의서 레이어·기록 줄 값 글자 규칙(plan.md 7-2, 데이터 3장). iOS `RecordDetail`과 같은 규칙.

/** 고를 것 하나: 값, 시트 글자, 기록 줄 글자(null이면 줄에서 뺌). */
data class DetailOption(val value: String, val title: String, val line: String?)

/** 시트 칸 하나: `data` 칸 이름, 칸 이름(legend)·덧말, 여럿 고르기인지, 숫자로 저장하는지, 고를 것. */
data class DetailField(
    val key: String,
    val title: String,
    val options: List<DetailOption>,
    val hint: String? = null,
    val multi: Boolean = false,
    val numeric: Boolean = false,
)

/** 칸이 고른 값들(칸 이름 → 값들). 하나 고르기 칸도 목록 하나로 쥔다. */
typealias DetailValues = Map<String, List<String>>

private fun o(value: String, title: String, line: String? = title) = DetailOption(value, title, line)
private const val MANY = "(여러 개 고를 수 있어요)"

fun detailFields(kind: RecordKind): List<DetailField> = when (kind) {
    RecordKind.vomit -> listOf(
        DetailField("contents", "토한 모양새는 어떠한가요?", listOf(
            o("kibble", "사료 알갱이", "사료"), o("digested", "죽처럼 불은 사료", "불은 사료"), o("liquidFoam", "액체·거품", "거품"),
            o("hairball", "헤어볼"), o("plant", "풀·식물"), o("foreign", "이물질 (끈·비닐 등)", "이물질"),
        ), hint = MANY, multi = true),
        DetailField("color", "빛깔", listOf(
            o("food", "사료 색", null), o("yellow", "노란색"), o("clear", "투명·흰색"), o("green", "초록색"),
            o("red", "붉은색 (피)", "붉은색"), o("darkRed", "검붉은·커피색"),
        )),
        DetailField("count", "연달아 몇 번?", listOf(o("1", "1번", null), o("2", "2번"), o("3", "3번 이상")), numeric = true),
    )
    RecordKind.stool -> listOf(
        DetailField("shape", "어떤 모양이었나요?", listOf(
            o("hard", "단단해요", "단단"), o("normal", "정상"), o("soft", "물러요", "무름"), o("loose", "묽어요", "묽음"), o("watery", "물 같아요", "물 같음"),
        ), hint = "(단단 → 물)"),
        DetailField("color", "빛깔", listOf(
            o("brown", "갈색 (보통)", null), o("yellowOrange", "노랑·주황"), o("green", "초록"), o("black", "검은색 (짜장처럼)", "검은색"), o("pale", "회백색"),
        )),
        DetailField("signs", "함께 보였나요?", listOf(
            o("blood", "피가 비쳐요", "피"), o("mucus", "점액(끈적한 막)이 있어요", "점액"), o("foreign", "털·이물질이 섞였어요", "털·이물질"),
        ), hint = MANY, multi = true),
    )
    RecordKind.urine -> listOf(
        DetailField("amount", "양은 어땠나요?", listOf(o("little", "찔끔"), o("normal", "보통"), o("much", "많음")), hint = "(모래 덩어리 크기로)"),
        DetailField("color", "빛깔", listOf(
            o("paleYellow", "연한 노랑 (보통)", null), o("clear", "거의 투명"), o("darkYellow", "진한 노랑"), o("pinkRed", "분홍·붉은색"),
        )),
        DetailField("signs", "함께 보였나요?", listOf(
            o("straining", "힘만 주고 거의 안 나와요", "힘만 줌"), o("frequent", "화장실을 자주 들락거려요", "자주 들락거림"),
            o("outside", "화장실 밖에 봤어요", "화장실 밖"), o("pain", "볼 때 울거나 아파해요", "아파함"),
        ), hint = MANY, multi = true),
    )
    else -> emptyList()
}

/** 의서 레이어를 띄우는 값(2026-11-01 사용자): 구토 피·검붉은·이물질·3번 이상, 매화 검은색, 소피 힘만 줌. */
private val alertValues = mapOf(
    RecordKind.vomit to mapOf("color" to setOf("red", "darkRed"), "contents" to setOf("foreign"), "count" to setOf("3")),
    RecordKind.stool to mapOf("color" to setOf("black")),
    RecordKind.urine to mapOf("signs" to setOf("straining")),
)

/** 고르기 전 [before]에 없던 레이어 값을 [after]에서 새로 골랐는지(풀 때는 안 뜸). */
fun picksAlert(kind: RecordKind, before: DetailValues, after: DetailValues): Boolean =
    alertValues[kind].orEmpty().any { (key, values) -> (after[key].orEmpty() - before[key].orEmpty().toSet()).any { it in values } }

/** 칩 하나를 눌렀을 때: 하나 고르기는 바꾸거나 다시 누르면 풂, 여럿 고르기는 넣고 뺌. */
fun toggleDetail(values: DetailValues, field: DetailField, value: String): DetailValues {
    val current = values[field.key].orEmpty()
    val next = when {
        value in current -> current - value
        field.multi -> current + value
        else -> listOf(value)
    }
    return if (next.isEmpty()) values - field.key else values + (field.key to next)
}

/** 기록의 `data` → 시트 값(모르는 칸·값은 버림). */
fun detailValues(record: Record): DetailValues {
    val json = runCatching { JSONObject(record.data) }.getOrNull() ?: return emptyMap()
    return detailFields(record.kind).mapNotNull { field ->
        val raw = when (val v = json.opt(field.key)) {
            is JSONArray -> List(v.length()) { v.opt(it).toString() }
            null, JSONObject.NULL -> emptyList()
            else -> listOf(v.toString())
        }
        val known = raw.filter { value -> field.options.any { it.value == value } }
        if (known.isEmpty()) null else field.key to if (field.multi) known else known.take(1)
    }.toMap()
}

/** 시트 값 → `data` JSON(여럿 고르기는 배열, 횟수는 숫자, 안 고른 칸은 뺌). 칸 이름 순(iOS sortedKeys와 같은 글자). */
fun detailData(kind: RecordKind, values: DetailValues): String {
    val json = JSONObject()
    for (field in detailFields(kind).sortedBy { it.key }) {
        val picked = values[field.key].orEmpty()
        val ordered = field.options.map { it.value }.filter { it in picked }
        if (ordered.isEmpty()) continue
        json.put(field.key, when {
            field.multi -> JSONArray(ordered)
            field.numeric -> ordered[0].toInt()
            else -> ordered[0]
        })
    }
    return json.toString()
}

/** 기록 줄 값 글자: 칸 순서대로 줄 글자를 ' · '로(보통 값은 뺌). 고른 게 없으면 '자세한 내용 없음'. 시트가 없는 종류는 null. */
fun detailLine(record: Record): String? {
    val fields = detailFields(record.kind).ifEmpty { return null }
    val values = detailValues(record)
    val parts = fields.flatMap { field -> field.options.filter { it.value in values[field.key].orEmpty() }.mapNotNull { it.line } }
    return if (parts.isEmpty()) "자세한 내용 없음" else parts.joinToString(" · ")
}

/** 자세히 시트 '기록하기': 새 기록이면 넣고, 고치기면 그 기록의 `data`·`occurredAt`·`day`·`updatedAt`만 바꾼다. */
suspend fun saveDetail(
    dao: OkcheDao,
    kind: RecordKind,
    values: DetailValues,
    occurredAt: Long,
    editing: Record?,
    petId: String?,
    now: Long = System.currentTimeMillis(),
    zone: ZoneId = ZoneId.systemDefault(),
): Record {
    val at = minOf(occurredAt, now) // 지금보다 뒤는 안 됨
    val data = detailData(kind, values)
    return if (editing != null) {
        // 시간을 바꿨을 때만 `day`를 다시 정한다(다른 시간대에서 남긴 기록이 칸만 고쳐 다른 날로 옮겨지지 않게).
        val changed = if (at != editing.occurredAt) editing.withOccurredAt(at, zone, now) else editing.copy(updatedAt = now)
        changed.copy(data = data).also { dao.update(it) }
    } else {
        Record(createdAt = now, petId = petId, kind = kind, occurredAt = at, day = app.okche.store.OkcheDay.of(at, zone), data = data).also { dao.insert(it) }
    }
}
