import SwiftData
import SwiftUI

// 탭 첫 화면들. 내용은 6단계부터 채운다. 일기·문안·실록·어진첩 머리 오른쪽에 낙관형 표시(6-3).

struct RecordTab: View {
    // 위 20은 디자인 일기 화면 값(토큰에 20이 없음).
    var body: some View { TabScreen(title: "승정원일기", subtitle: "매일 기록", topPadding: 20, trailing: { PetSeal() }) }
}

struct MainTab: View {
    // shortcut: 켜 둔 채 날이 바뀌어도 그대로고 하루 기준(새벽 6시)도 아직 안 따름, 6단계 문안 내용에서 고친다.
    var body: some View {
        TabScreen(title: "문안", subtitle: Date.now.formatted(.dateTime.month().day().weekday(.wide)), trailing: { PetSeal() })
    }
}

struct SillokTab: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""

    // '콩이실록'(이름 + 실록, 호칭 없음 — 보드 `Sillok`). 아이가 없으면 '실록'.
    var body: some View {
        TabScreen(title: PetTitle.current(pets, lastPetId: lastPetId).map { "\($0.name)실록" } ?? "실록", trailing: { PetSeal() })
    }
}

struct AlbumTab: View {
    var body: some View { TabScreen(title: "어진첩", subtitle: "앨범", trailing: { PetSeal() }) }
}

struct MoreTab: View {
    var body: some View {
        NavigationStack {
            // 더보기의 다른 줄(사료·다과·탕약·약방·등사·백업·의서·기별·결제)은 각 단계에서.
            TabScreen(title: "더보기", content: { PetManageSection() })
                .toolbar(.hidden, for: .navigationBar)
        }
    }
}
