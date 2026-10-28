package app.okche.store

import java.util.UUID

/**
 * 하나만 있어야 하는 줄의 고정 ID (docs/data-model.md 1-5).
 * 이름의 UTF-8 바이트를 MD5 한 UUID v3, 네임스페이스 없음 — iOS `UUID(fixedName:)`와 같은 값.
 */
fun fixedId(name: String): String = UUID.nameUUIDFromBytes(name.toByteArray(Charsets.UTF_8)).toString()

object FixedIds {
    /** 함께 쓰는 설정 줄. */
    val SETTING = fixedId("okche:setting")

    /**
     * 급식기 자동 기록. 같은 차례면 두 기기가 따로 만들어도 같은 ID.
     * 같은 ID 줄이 없을 때만(지운 줄 포함) 만들고, `updatedAt`은 끼니 예정 시각(1-5) — 보호자가 고친 기록이 병합에서 이기게.
     */
    fun feederRecord(slotKey: String) = fixedId("okche:feeder:$slotKey")
}
