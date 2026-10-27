import SwiftUI

// 탭 첫 화면들. 내용은 6단계부터 채운다.

struct RecordTab: View {
    var body: some View { TabScreen(title: "승정원일기", subtitle: "매일 기록", topPadding: 20) }
}

struct MainTab: View {
    // shortcut: 켜 둔 채 날이 바뀌어도 그대로고 하루 기준(새벽 6시)도 아직 안 따름, 6단계 문안 내용에서 고친다.
    var body: some View {
        TabScreen(title: "문안", subtitle: Date.now.formatted(.dateTime.month().day().weekday(.wide).locale(Locale(identifier: "ko_KR"))))
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
    var body: some View { TabScreen(title: "더보기") }
}
