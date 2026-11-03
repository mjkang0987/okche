import SwiftUI

/// 탭 첫 화면 틀: 제목 28 + 쉬운 말 14(디자인 '화면 짜임새' 규칙), 한지 바탕, 좌우 16. 머리 오른쪽 자리(`trailing`)에 낙관형 표시(6-3).
struct TabScreen<Trailing: View, Content: View>: View {
    let title: String
    var subtitle: String? = nil
    /// 화면 위 여백. 디자인은 탭 첫 화면 24, 일기만 20.
    var topPadding: CGFloat = OkcheSpacing.space6
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
                ViewThatFits(in: .horizontal) {
                    HStack(spacing: OkcheSpacing.space3) {
                        titleBlock(HStack(alignment: .firstTextBaseline, spacing: OkcheSpacing.space2) { header })
                        Spacer(minLength: 0)
                        trailing
                    }
                    HStack(spacing: OkcheSpacing.space3) {
                        titleBlock(VStack(alignment: .leading, spacing: OkcheSpacing.space1) { header })
                        Spacer(minLength: 0)
                        trailing
                    }
                    VStack(alignment: .leading, spacing: OkcheSpacing.space2) {
                        titleBlock(VStack(alignment: .leading, spacing: OkcheSpacing.space1) { header })
                        trailing
                    }
                }
                content
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, OkcheSpacing.space4)
            .padding(.top, topPadding)
            .padding(.bottom, OkcheSpacing.space4)
        }
        .background(OkcheColor.hanji)
    }
}

extension TabScreen where Trailing == EmptyView {
    init(title: String, subtitle: String? = nil, topPadding: CGFloat = OkcheSpacing.space6, @ViewBuilder content: () -> Content) {
        self.init(title: title, subtitle: subtitle, topPadding: topPadding, trailing: { EmptyView() }, content: content)
    }
}

extension TabScreen where Content == EmptyView {
    init(title: String, subtitle: String? = nil, topPadding: CGFloat = OkcheSpacing.space6, @ViewBuilder trailing: () -> Trailing) {
        self.init(title: title, subtitle: subtitle, topPadding: topPadding, trailing: trailing, content: { EmptyView() })
    }
}

extension TabScreen where Trailing == EmptyView, Content == EmptyView {
    init(title: String, subtitle: String? = nil, topPadding: CGFloat = OkcheSpacing.space6) {
        self.init(title: title, subtitle: subtitle, topPadding: topPadding, trailing: { EmptyView() }, content: { EmptyView() })
    }
}
