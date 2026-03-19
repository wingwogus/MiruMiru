import SwiftUI

enum AuthenticatedTab: Hashable {
    case home
    case timetable
}

struct AuthenticatedAppShell: View {
    @ObservedObject private var session: AppSession
    private let homeClient: HomeClientProtocol
    private let timetableClient: TimetableClientProtocol
    @State private var selectedTab: AuthenticatedTab = .home

    init(
        session: AppSession,
        homeClient: HomeClientProtocol,
        timetableClient: TimetableClientProtocol
    ) {
        self.session = session
        self.homeClient = homeClient
        self.timetableClient = timetableClient
    }

    var body: some View {
        ZStack {
            HomeView(
                session: session,
                client: homeClient,
                isActive: selectedTab == .home
            )
            .opacity(selectedTab == .home ? 1 : 0)
            .allowsHitTesting(selectedTab == .home)
            .accessibilityHidden(selectedTab != .home)

            TimetableView(
                session: session,
                client: timetableClient,
                isActive: selectedTab == .timetable
            )
            .opacity(selectedTab == .timetable ? 1 : 0)
            .allowsHitTesting(selectedTab == .timetable)
            .accessibilityHidden(selectedTab != .timetable)
        }
        .safeAreaInset(edge: .bottom) {
            AuthenticatedTabBar(selectedTab: $selectedTab)
        }
    }
}

private struct AuthenticatedTabBar: View {
    @Binding var selectedTab: AuthenticatedTab

    private let items: [AuthenticatedTabBarItem] = [
        .init(systemImage: "house.fill", title: "Home", tab: .home),
        .init(systemImage: "calendar", title: "Timetable", tab: .timetable),
        .init(systemImage: "list.bullet.rectangle.portrait", title: "Boards", tab: nil),
        .init(systemImage: "star.bubble", title: "Reviews", tab: nil),
        .init(systemImage: "briefcase", title: "Career", tab: nil)
    ]

    var body: some View {
        HStack {
            ForEach(items) { item in
                Spacer()

                Button {
                    guard let tab = item.tab else { return }
                    selectedTab = tab
                } label: {
                    VStack(spacing: 6) {
                        Image(systemName: item.systemImage)
                            .font(.system(size: 20, weight: .semibold))
                            .foregroundStyle(foregroundColor(for: item))

                        Text(item.title)
                            .font(AppFont.medium(12, relativeTo: .caption))
                            .foregroundStyle(foregroundColor(for: item))
                    }
                    .frame(maxWidth: .infinity)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .disabled(item.tab == nil)

                Spacer()
            }
        }
        .padding(.top, 14)
        .padding(.bottom, 10)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(Color(red: 0.90, green: 0.92, blue: 0.96))
                .frame(height: 1)
        }
    }

    private func foregroundColor(for item: AuthenticatedTabBarItem) -> Color {
        if item.tab == selectedTab {
            return AuthPalette.primaryStart
        }
        return Color(red: 0.48, green: 0.56, blue: 0.67)
    }
}

private struct AuthenticatedTabBarItem: Identifiable {
    let id: String
    let systemImage: String
    let title: String
    let tab: AuthenticatedTab?

    init(systemImage: String, title: String, tab: AuthenticatedTab?) {
        self.id = title
        self.systemImage = systemImage
        self.title = title
        self.tab = tab
    }
}

struct AuthenticatedAppShell_Previews: PreviewProvider {
    static var previews: some View {
        AuthenticatedAppShell(
            session: PreviewFactory.makeSession(state: .authenticated),
            homeClient: PreviewHomeClient.loaded(),
            timetableClient: PreviewTimetableClient.loaded()
        )
    }
}
