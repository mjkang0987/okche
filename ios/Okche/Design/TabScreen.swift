import SwiftUI

/// 탭 첫 화면 틀: 제목 28 + 쉬운 말 14(디자인 '화면 짜임새' 규칙), 한지 바탕, 좌우 16. 머리 오른쪽 자리(`trailing`)에 낙관형 표시(6-3).
struct TabScreen<Trailing: View, Content: View>: View {
    let title: String
    var subtitle: String? = nil
    @ViewBuilder var trailing: Trailing
    @ViewBuilder var content: Content

    @ViewBuilder private var header: some View {
        Text(title)
            .okcheText(OkcheType.display)
            .foregroundStyle(OkcheColor.ink)
            .lineLimit(1)
            .minimumScaleFactor(0.5)
        if let subtitle {
            Text(subtitle)
                .okcheText(OkcheType.label, weight: .regular)
                .foregroundStyle(OkcheColor.inkMuted)
        }
    }

    /// VoiceOver가 제목과 쉬운 말을 한 머리말로 읽게.
    private func titleBlock(_ layout: some View) -> some View {
        layout
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: OkcheSpacing.space4) {
                // 한 줄에 안 들어가면 쉬운 말을 먼저 제목 아래로, 그래도 안 되면 오른쪽 자리를 제목 아래로 내린다.
                // 제목 첫 줄은 언제나 위에서 4(낙관 44 가운데) — 낙관이 없거나 쉬운 말이 아래로 내려가도 탭을 오갈 때 제목이 튀지 않게(2026-11-04 사용자).
                ViewThatFits(in: .horizontal) {
                    HStack(alignment: .top, spacing: OkcheSpacing.space3) {
                        titleBlock(HStack(alignment: .firstTextBaseline, spacing: OkcheSpacing.space2) { header }).padding(.top, 4)
                        Spacer(minLength: 0)
                        trailing
                    }
                    HStack(alignment: .top, spacing: OkcheSpacing.space3) {
                        titleBlock(VStack(alignment: .leading, spacing: OkcheSpacing.space1) { header }).padding(.top, 4)
                        Spacer(minLength: 0)
                        trailing
                    }
                    VStack(alignment: .leading, spacing: OkcheSpacing.space2) {
                        titleBlock(VStack(alignment: .leading, spacing: OkcheSpacing.space1) { header }).padding(.top, 4)
                        trailing
                    }
                }
                .frame(minHeight: 44, alignment: .top)
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, OkcheSpacing.space6) // 다섯 탭 모두 24(일기 보드 20 대신 — 2026-11-04 사용자)
            .padding(.bottom, OkcheSpacing.space4)
        }
        .background(OkcheColor.hanji)
    }
}

extension TabScreen where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, @ViewBuilder content: () -> Content) {
        self.init(title: title, subtitle: subtitle, trailing: { EmptyView() }, content: content)
    }
}

extension TabScreen where Content == EmptyView {
    init(title: String, subtitle: String? = nil, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, subtitle: subtitle, trailing: trailing, content: { EmptyView() })
    }
}

extension TabScreen where Trailing == EmptyView, Content == EmptyView {
    init(title: String, subtitle: String? = nil) {
        self.init(title: title, subtitle: subtitle, trailing: { EmptyView() }, content: { EmptyView() })
    }
}
