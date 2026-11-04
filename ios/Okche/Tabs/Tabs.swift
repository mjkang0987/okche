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
            // 더보기의 다른 줄(다과·탕약·약방·등사·백업·의서·기별·결제)은 각 단계에서.
            TabScreen(title: "더보기", content: { PetManageSection(); MoreMenu() })
                .toolbar(.hidden, for: .navigationBar)
        }
    }
}

/// 더보기 메뉴(보드 `More`): 흰 칸 안 52 줄, 이름 + 쉬운 말 + ›. 사료 · 처방식은 지금 보는 아이의 사료 바꾸기 시트.
private struct MoreMenu: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @State private var foodPet: Pet?

    var body: some View {
        // 아이가 없거나 지금 보는 아이가 추억이면 사료 줄 없음(바꿀 아이가 없음).
        if let pet = PetTitle.current(pets, lastPetId: lastPetId), pet.memorializedAt == nil {
            VStack(spacing: 0) {
                MoreMenuRow(title: "사료 · 처방식") { foodPet = pet }
            }
            .padding(.horizontal, OkcheSpacing.space4)
            .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
            .accessibilityElement(children: .contain)
            .accessibilityLabel("설정 메뉴")
            .sheet(item: $foodPet) { FoodChangeSheet(pet: $0) }
        }
    }
}

/// 더보기 메뉴 줄: 이름(+ 쉬운 말) + ›, 52 높이.
struct MoreMenuRow: View {
    let title: String
    var note: String?
    let action: () -> Void
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 52

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                (Text(title) + Text(note.map { " · \($0)" } ?? "").font(.subheadline).foregroundColor(OkcheColor.inkMuted))
                    .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Text("›").okcheText(OkcheType.body).foregroundStyle(OkcheColor.lineStrong).accessibilityHidden(true)
            }
            .multilineTextAlignment(.leading)
            .frame(minHeight: height)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
    }
}
