import SwiftUI

enum AuthenticatedTab: Hashable {
    case home
    case timetable
    case boards
    case reviews
    case messages
}

struct AuthenticatedAppShell: View {
    @ObservedObject private var session: AppSession
    @ObservedObject private var boardsSyncStore: BoardsSyncStore
    private let homeClient: HomeClientProtocol
    private let timetableClient: TimetableClientProtocol
    private let boardsClient: BoardsClientProtocol
    private let courseReviewsClient: CourseReviewsClientProtocol
    private let messagesClient: MessagesClientProtocol
    private let messagesRealtimeClient: MessagesRealtimeClientProtocol
    @State private var selectedTab: AuthenticatedTab = .home
    @State private var isTabBarHidden = false
    @State private var pendingBoardsPostId: Int64?
    @State private var pendingMessageStartRequest: MessageStartRequest?

    init(
        session: AppSession,
        homeClient: HomeClientProtocol,
        timetableClient: TimetableClientProtocol,
        boardsClient: BoardsClientProtocol,
        courseReviewsClient: CourseReviewsClientProtocol,
        messagesClient: MessagesClientProtocol,
        messagesRealtimeClient: MessagesRealtimeClientProtocol,
        boardsSyncStore: BoardsSyncStore
    ) {
        self.session = session
        self.boardsSyncStore = boardsSyncStore
        self.homeClient = homeClient
        self.timetableClient = timetableClient
        self.boardsClient = boardsClient
        self.courseReviewsClient = courseReviewsClient
        self.messagesClient = messagesClient
        self.messagesRealtimeClient = messagesRealtimeClient
    }

    var body: some View {
        ZStack {
            HomeView(
                session: session,
                client: homeClient,
                boardsSyncStore: boardsSyncStore,
                isActive: selectedTab == .home,
                onSemesterTap: {
                    selectedTab = .timetable
                },
                onBoardsTap: {
                    selectedTab = .boards
                },
                onTrendingPostTap: { postId in
                    pendingBoardsPostId = postId
                    selectedTab = .boards
                }
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

            BoardsRootView(
                session: session,
                client: boardsClient,
                syncStore: boardsSyncStore,
                isTabBarHidden: $isTabBarHidden,
                pendingPostId: $pendingBoardsPostId,
                onStartChat: { request in
                    pendingMessageStartRequest = request
                    selectedTab = .messages
                },
                isActive: selectedTab == .boards
            )
            .opacity(selectedTab == .boards ? 1 : 0)
            .allowsHitTesting(selectedTab == .boards)
            .accessibilityHidden(selectedTab != .boards)

            CourseReviewsRootView(
                session: session,
                client: courseReviewsClient,
                isTabBarHidden: $isTabBarHidden,
                isActive: selectedTab == .reviews
            )
            .opacity(selectedTab == .reviews ? 1 : 0)
            .allowsHitTesting(selectedTab == .reviews)
            .accessibilityHidden(selectedTab != .reviews)

            MessagesRootView(
                session: session,
                client: messagesClient,
                realtimeClient: messagesRealtimeClient,
                isTabBarHidden: $isTabBarHidden,
                pendingStartRequest: $pendingMessageStartRequest,
                isActive: selectedTab == .messages
            )
            .opacity(selectedTab == .messages ? 1 : 0)
            .allowsHitTesting(selectedTab == .messages)
            .accessibilityHidden(selectedTab != .messages)
        }
        .safeAreaInset(edge: .bottom, spacing: 0) {
            if isTabBarHidden == false {
                AuthenticatedTabBar(selectedTab: $selectedTab)
            }
        }
        .animation(.easeInOut(duration: 0.18), value: isTabBarHidden)
        .onChange(of: selectedTab) { _, newValue in
            if newValue != .boards, newValue != .reviews, newValue != .messages {
                isTabBarHidden = false
            }
        }
        .onChange(of: session.state) { _, newState in
            if newState != .authenticated {
                boardsSyncStore.reset()
            }
        }
    }
}

private struct AuthenticatedTabBar: View {
    @Binding var selectedTab: AuthenticatedTab

    private let items: [AuthenticatedTabBarItem] = [
        .init(systemImage: "house.fill", title: "Home", tab: .home),
        .init(systemImage: "calendar", title: "Timetable", tab: .timetable),
        .init(systemImage: "list.bullet.rectangle.portrait", title: "Boards", tab: .boards),
        .init(systemImage: "star.fill", title: "Reviews", tab: .reviews),
        .init(systemImage: "message", title: "Messages", tab: .messages)
    ]

    var body: some View {
        HStack {
            ForEach(items) { item in
                Spacer()

                Button {
                    selectedTab = item.tab
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

                Spacer()
            }
        }
        .padding(.top, 14)
        .padding(.bottom, 10)
        .background(.ultraThinMaterial)
        .overlay(alignment: .top) {
            Rectangle()
                .fill(AppTheme.divider)
                .frame(height: 1)
        }
    }

    private func foregroundColor(for item: AuthenticatedTabBarItem) -> Color {
        if item.tab == selectedTab {
            return AuthPalette.primaryStart
        }
        return AppTheme.textSecondary
    }
}

private struct AuthenticatedTabBarItem: Identifiable {
    let id: String
    let systemImage: String
    let title: String
    let tab: AuthenticatedTab

    init(systemImage: String, title: String, tab: AuthenticatedTab) {
        self.id = title
        self.systemImage = systemImage
        self.title = title
        self.tab = tab
    }
}

struct AuthenticatedAppShell_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            previewShell
            .previewDisplayName("Authenticated Shell - Light")

            previewShell
            .preferredColorScheme(.dark)
            .previewDisplayName("Authenticated Shell - Dark")
        }
    }

    private static var previewShell: some View {
        AuthenticatedAppShell(
            session: PreviewFactory.makeSession(state: .authenticated),
                homeClient: PreviewHomeClient.loaded(),
                timetableClient: PreviewTimetableClient.loaded(),
                boardsClient: PreviewBoardsClient(scenario: .loaded),
                courseReviewsClient: PreviewCourseReviewsClient.loaded(),
                messagesClient: PreviewMessagesClient.loaded(),
                messagesRealtimeClient: PreviewMessagesRealtimeClient(),
                boardsSyncStore: BoardsSyncStore()
        )
    }
}
