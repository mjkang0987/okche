import SwiftUI

/// 하단 탭 5개. 순서는 디자인 기준(일기 → 문안 → 실록 → 어진첩 → 더보기), 첫 화면은 일기.
struct RootTabView: View {
    init() {
        // 디자인의 탭 막대: 흰 바탕, 위 가는 선(line), 안 고른 탭은 inkMuted.
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(OkcheColor.paper)
        appearance.shadowColor = UIColor(OkcheColor.line)
        let item = appearance.stackedLayoutAppearance
        item.normal.iconColor = UIColor(OkcheColor.inkMuted)
        // 탭 글자 12(caption), 고른 탭만 굵게 — 디자인과 같게.
        item.normal.titleTextAttributes = [
            .foregroundColor: UIColor(OkcheColor.inkMuted),
            .font: UIFont.systemFont(ofSize: OkcheType.caption.size),
        ]
        item.selected.iconColor = UIColor(OkcheColor.namsaek)
        item.selected.titleTextAttributes = [
            .foregroundColor: UIColor(OkcheColor.namsaek),
            .font: UIFont.systemFont(ofSize: OkcheType.caption.size, weight: .bold),
        ]
        UITabBar.appearance().standardAppearance = appearance
        UITabBar.appearance().scrollEdgeAppearance = appearance
    }

    var body: some View {
        TabView {
            // 디자인 아이콘은 선 모양이라, 시스템이 탭 아이콘을 채운 모양으로 바꾸지 않게 탭마다 symbolVariants를 끈다.
            RecordTab()
                .tabItem { Label("일기", systemImage: "doc.text").environment(\.symbolVariants, .none) }
            MainTab()
                .tabItem { Label("문안", systemImage: "heart").environment(\.symbolVariants, .none) }
            SillokTab()
                .tabItem { Label("실록", systemImage: "chart.bar").environment(\.symbolVariants, .none) }
            AlbumTab()
                .tabItem { Label("어진첩", systemImage: "photo").environment(\.symbolVariants, .none) }
            MoreTab()
                .tabItem { Label("더보기", systemImage: "ellipsis").environment(\.symbolVariants, .none) }
        }
        .tint(OkcheColor.namsaek)
    }
}
