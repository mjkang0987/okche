import SwiftData
import SwiftUI

// 탭 첫 화면들. 내용은 6단계부터 채운다(문안은 `Main/MainView.swift`). 일기·문안·실록·어진첩 머리 오른쪽에 낙관형 표시(6-3).

struct SillokTab: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""

    // 아이가 있으면 실록(10-1, 머리 '콩이실록'은 남색 금테 카드 — 보드 `Sillok`), 없으면 '실록' 빈 탭 머리.
    var body: some View {
        if let pet = PetTitle.current(pets, lastPetId: lastPetId) {
            SillokView(pet: pet)
        } else {
            TabScreen(title: "실록", trailing: { PetSeal() })
        }
    }
}

struct AlbumTab: View {
    var body: some View { TabScreen(title: "어진첩", subtitle: "앨범", trailing: { PetSeal() }) }
}

struct MoreTab: View {
    /// 더보기 안 화면은 경로로 쌓는다 — 탕약 안에서 `NavigationLink { MedAddView() }`로 약 추가를 열면 두 화면이 끝없이 다시 그려져 멈춰서(2026-11-04 확인).
    @State private var path: [MoreRoute] = []
    @State private var router = NotifyRouter.shared

    var body: some View {
        NavigationStack(path: $path) {
            // 더보기의 다른 줄(등사·백업·의서·기별·결제)은 각 단계에서.
            TabScreen(title: "더보기", content: { PetManageSection(); MoreMenu() })
                .toolbar(.hidden, for: .navigationBar)
                .navigationDestination(for: MoreRoute.self) { route in
                    switch route {
                    case .snacks: SnackListView()
                    case .tangyak: TangyakView()
                    case .medAdd: MedAddView()
                    case .medEdit(let id): MedAddView(scheduleId: id)
                    case .yakbang: YakbangView()
                    case .notify: NotifySettingsView()
                    }
                }
        }
        // 남은 약 알림을 누르면 약방으로(그 약 고치기는 약방이 연다, 8-4c).
        .onChange(of: router.openMedId, initial: true) { if router.openMedId != nil, path.last != .yakbang { path = [.yakbang] } }
    }
}

/// 더보기 안 화면.
enum MoreRoute: Hashable { case snacks, tangyak, medAdd, medEdit(UUID), yakbang, notify }

/// 더보기 메뉴(보드 `More`): 흰 칸 안 52 줄, 이름 + 쉬운 말 + ›. 사료 · 처방식은 지금 보는 아이의 사료 바꾸기 시트.
private struct MoreMenu: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]
    @AppStorage("lastPetId") private var lastPetId = ""
    @State private var foodPet: Pet?
    @Query(filter: #Predicate<FoodChange> { $0.deletedAt == nil }) private var changes: [FoodChange]
    @Query(filter: #Predicate<Food> { $0.deletedAt == nil }) private var foods: [Food]

    var body: some View {
        VStack(spacing: 0) {
            // 아이가 없거나 지금 보는 아이가 추억이면 사료 줄 없음(바꿀 아이가 없음). 다과는 모든 아이 공통이라 늘 있음.
            if let pet = PetTitle.current(pets, lastPetId: lastPetId), pet.memorializedAt == nil {
                // 아래 줄에 지금 먹는 사료 — 바꾸면 바로 바뀌어 저장한 게 보이게(2026-11-04 사용자).
                let food = FoodRules.change(at: OkcheDay.of(.now), petId: pet.id, in: changes)
                    .map { change in FoodRules.line(change, food: foods.first { $0.id == change.foodId }) }
                MoreMenuRow(title: "사료 · 처방식", detail: food) { foodPet = pet }
                Rectangle().fill(OkcheColor.line).frame(height: 1)
            }
            NavigationLink(value: MoreRoute.snacks) { MoreMenuLabel(title: "다과", note: "간식 목록") }
                .buttonStyle(.plain)
            Rectangle().fill(OkcheColor.line).frame(height: 1)
            NavigationLink(value: MoreRoute.tangyak) { MoreMenuLabel(title: "탕약", note: "약 알림") }
                .buttonStyle(.plain)
            Rectangle().fill(OkcheColor.line).frame(height: 1)
            NavigationLink(value: MoreRoute.yakbang) { MoreMenuLabel(title: "약방", note: "약 목록") }
                .buttonStyle(.plain)
            Rectangle().fill(OkcheColor.line).frame(height: 1)
            NavigationLink(value: MoreRoute.notify) { MoreMenuLabel(title: "기별", note: "알림 설정") }
                .buttonStyle(.plain)
        }
        .padding(.horizontal, OkcheSpacing.space4)
        .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
        .accessibilityElement(children: .contain)
        .accessibilityLabel("설정 메뉴")
        .sheet(item: $foodPet) { FoodChangeSheet(pet: $0) }
    }
}

/// 더보기 메뉴 줄: 이름(+ 쉬운 말) + ›, 52 높이.
struct MoreMenuRow: View {
    let title: String
    var note: String?
    /// 아래 줄(흐린 14) — 사료 줄의 지금 먹는 사료.
    var detail: String?
    let action: () -> Void
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 52

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    (Text(title) + Text(note.map { " · \($0)" } ?? "").font(.subheadline).foregroundColor(OkcheColor.inkMuted))
                        .okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
                    if let detail {
                        Text(detail).okcheText(OkcheType.label, weight: .regular).foregroundStyle(OkcheColor.inkMuted)
                    }
                }
                .padding(.vertical, detail == nil ? 0 : 10)
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

/// 다른 화면으로 넘어가는 메뉴 줄 글자(`NavigationLink`의 label — `MoreMenuRow`와 같은 모양).
struct MoreMenuLabel: View {
    let title: String
    var note: String?
    @ScaledMetric(relativeTo: .body) private var height: CGFloat = 52

    var body: some View {
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
}
