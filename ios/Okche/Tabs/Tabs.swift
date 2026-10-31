import SwiftData
import SwiftUI

// 탭 첫 화면들. 내용은 6단계부터 채운다.

struct RecordTab: View {
    // 위 20은 디자인 일기 화면 값(토큰에 20이 없음).
    var body: some View { TabScreen(title: "승정원일기", subtitle: "매일 기록", topPadding: 20) }
}

struct MainTab: View {
    // shortcut: 켜 둔 채 날이 바뀌어도 그대로고 하루 기준(새벽 6시)도 아직 안 따름, 6단계 문안 내용에서 고친다.
    var body: some View {
        TabScreen(title: "문안", subtitle: Date.now.formatted(.dateTime.month().day().weekday(.wide)))
    }
}

struct SillokTab: View {
    // 디자인은 '콩이실록'처럼 아이 이름이 붙는다. 아이가 생기는 6단계에서 바꾼다.
    var body: some View { TabScreen(title: "실록") }
}

struct AlbumTab: View {
    var body: some View { TabScreen(title: "어진첩", subtitle: "앨범") }
}

struct MoreTab: View {
    @Query(filter: #Predicate<Pet> { $0.deletedAt == nil }, sort: \Pet.createdAt) private var pets: [Pet]

    var body: some View {
        NavigationStack {
            TabScreen(title: "더보기") {
                // shortcut: 아이 정보로 가는 임시 줄(아이마다 한 줄), 6-3 아이 바꾸기·더보기 화면을 만들 때 디자인 `More`대로 바꾼다.
                if !pets.isEmpty {
                    VStack(spacing: 0) {
                        ForEach(Array(pets.enumerated()), id: \.element.id) { index, pet in
                            NavigationLink { PetEditScreen(pet: pet) } label: { row(pet) }
                                .overlay(alignment: .top) { if index > 0 { Rectangle().fill(OkcheColor.line).frame(height: 1) } }
                        }
                    }
                    .padding(.horizontal, 14)
                    .background(OkcheColor.paper, in: RoundedRectangle(cornerRadius: OkcheRadius.radiusLg))
                }
            }
            .toolbar(.hidden, for: .navigationBar)
        }
    }

    private func row(_ pet: Pet) -> some View {
        HStack(spacing: 12) {
            Text("\(pet.name) · 아이 정보").okcheText(OkcheType.body).foregroundStyle(OkcheColor.ink)
            Spacer(minLength: 0)
            Image(systemName: "chevron.right").font(.body.weight(.medium)).foregroundStyle(OkcheColor.lineStrong)
                .accessibilityHidden(true)
        }
        .frame(minHeight: 52)
        .contentShape(Rectangle())
    }
}
