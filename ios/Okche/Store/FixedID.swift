import CryptoKit
import Foundation

/// 하나만 있어야 하는 줄의 고정 ID (docs/data-model.md 1-5).
/// 이름의 UTF-8 바이트를 MD5 한 UUID v3, 네임스페이스 없음 — Kotlin `UUID.nameUUIDFromBytes`와 같은 값.
extension UUID {
    init(fixedName name: String) {
        var b = Array(Insecure.MD5.hash(data: Data(name.utf8)))
        b[6] = b[6] & 0x0f | 0x30  // 버전 3
        b[8] = b[8] & 0x3f | 0x80  // RFC 4122 variant
        self.init(uuid: (b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7], b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]))
    }

    /// 함께 쓰는 설정 줄.
    static let setting = UUID(fixedName: "okche:setting")

    /// 급식기 자동 기록. 같은 차례면 두 기기가 따로 만들어도 같은 ID.
    /// 같은 ID 줄이 없을 때만(지운 줄 포함) 만든다 — 다시 넣으면 지운 기록·고친 기록을 덮어쓴다.
    /// `updatedAt`은 만든 때가 아니라 끼니 예정 시각(1-5) — 보호자가 고친 기록이 병합에서 이기게.
    static func feederRecord(slotKey: String) -> UUID {
        UUID(fixedName: "okche:feeder:\(slotKey)")
    }
}
