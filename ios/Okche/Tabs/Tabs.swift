import SwiftData
import SwiftUI

// 탭 첫 화면들. 내용은 6단계부터 채운다(문안은 `Main/MainView.swift`). 일기·문안·실록·어진첩 머리 오른쪽에 낙관형 표시(6-3).

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
