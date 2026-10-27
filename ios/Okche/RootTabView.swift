import SwiftUI

/// 하단 탭 5개. 순서는 디자인 기준(일기 → 문안 → 실록 → 어진첩 → 더보기), 첫 화면은 일기.
struct RootTabView: View {
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
    }
}
