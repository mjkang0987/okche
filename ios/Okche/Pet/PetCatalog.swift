import SwiftUI

/// 아이 등록에서 고르는 목록(기획 문서 '품종 목록', 2026-10-28). 화면 글자 그대로 저장한다.
/// shortcut: 앱 안에 박아 둔 목록, 품종 참조 DB(검색·덩치·품종 ID)를 만들 때 바꾼다.
enum PetCatalog {
    static func breeds(_ species: Species) -> [String] {
        switch species {
        case .cat: ["코리안숏헤어", "러시안블루", "페르시안", "엑조틱", "브리티시숏헤어", "스코티시폴드", "먼치킨", "랙돌", "메인쿤",
                    "아메리칸숏헤어", "샴", "노르웨이숲", "벵갈", "아비시니안", "스핑크스", "터키시앙고라", mixed]
        case .dog: ["말티즈", "토이푸들", "포메라니안", "비숑", "시츄", "치와와", "요크셔테리어", "슈나우저", "웰시코기", "닥스훈트",
                    "진돗개", "시바", "비글", "보더콜리", "사모예드", "골든리트리버", "래브라도", "프렌치불독", "퍼그", mixed]
        }
    }

    static let mixed = "믹스 · 잘 모르겠어요"

    struct FurColor: Hashable {
        let name: String
        /// 실제 털 색(디자인 가이드 예외).
        let color: Color
        /// 바탕과 비슷한 밝은 색은 테두리를 둔다.
        let outlined: Bool
    }

    static func furColors(_ species: Species) -> [FurColor] {
        switch species {
        case .cat: [FurColor(name: "치즈", color: rgb(0xF4A95E), outlined: false),
                    FurColor(name: "고등어", color: rgb(0xAAA398), outlined: true),
                    FurColor(name: "턱시도", color: rgb(0x3E3739), outlined: false),
                    FurColor(name: "회색", color: rgb(0xA9B2BC), outlined: true)]
        // shortcut: 강아지 털색 이름은 기획 문서에 없어 임시(디자인 보드와 같음), 캐릭터를 그릴 때 다시 정한다.
        case .dog: [FurColor(name: "흰색", color: rgb(0xF4F1EA), outlined: true),
                    FurColor(name: "누런색", color: rgb(0xD9A75F), outlined: false),
                    FurColor(name: "갈색", color: rgb(0x8A5A3B), outlined: false),
                    FurColor(name: "검은색", color: rgb(0x2E2A2B), outlined: false)]
        }
    }

    private static func rgb(_ hex: Int) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 0xFF) / 255, green: Double(hex >> 8 & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}
