import SwiftUI

struct AppRoot: View {
    @ObservedObject var session: AppSession
    @StateObject private var boardsSyncStore = BoardsSyncStore()
    private let homeClient: HomeClientProtocol
    private let timetableClient: TimetableClientProtocol
    private let boardsClient: BoardsClientProtocol
    private let courseReviewsClient: CourseReviewsClientProtocol
    private let messagesClient: MessagesClientProtocol
    private let messagesRealtimeClient: MessagesRealtimeClientProtocol
    @State private var authRoute: AuthRoute = .login
    @State private var loginPrefillEmail = ""

    init(
        session: AppSession,
        homeClient: HomeClientProtocol,
        timetableClient: TimetableClientProtocol,
        boardsClient: BoardsClientProtocol,
        courseReviewsClient: CourseReviewsClientProtocol,
        messagesClient: MessagesClientProtocol,
        messagesRealtimeClient: MessagesRealtimeClientProtocol
    ) {
        self.session = session
        self.homeClient = homeClient
        self.timetableClient = timetableClient
        self.boardsClient = boardsClient
        self.courseReviewsClient = courseReviewsClient
        self.messagesClient = messagesClient
        self.messagesRealtimeClient = messagesRealtimeClient
    }

    var body: some View {
        Group {
            switch session.state {
            case .launch, .restoring:
                loadingView
            case .unauthenticated, .submitting, .invalidSession:
                authShell
            case .authenticated:
                AuthenticatedAppShell(
                    session: session,
                    homeClient: homeClient,
                    timetableClient: timetableClient,
                    boardsClient: boardsClient,
                    courseReviewsClient: courseReviewsClient,
                    messagesClient: messagesClient,
                    messagesRealtimeClient: messagesRealtimeClient,
                    boardsSyncStore: boardsSyncStore
                )
            }
        }
        .animation(.easeInOut(duration: 0.2), value: session.state)
        .animation(.easeInOut(duration: 0.2), value: authRoute)
        .onChange(of: session.state) { _, newState in
            if newState == .unauthenticated || newState == .invalidSession {
                authRoute = .login
            }
            if newState != .authenticated {
                boardsSyncStore.reset()
            }
        }
    }

    private var loadingView: some View {
        ZStack {
            LinearGradient(
                colors: [
                    Color(.systemBackground),
                    Color.blue.opacity(0.03)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()

            VStack(spacing: 18) {
                ProgressView()
                    .controlSize(.large)

                Text(session.state == .restoring ? "Restoring session..." : "Preparing MiruMiru...")
                    .font(AppFont.semibold(17, relativeTo: .headline))
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var authShell: some View {
        Group {
            switch authRoute {
            case .login:
                LoginView(
                    session: session,
                    initialEmail: loginPrefillEmail,
                    onSignupTap: {
                        authRoute = .signup
                    }
                )
            case .signup:
                SignupPlaceholderView(
                    client: AuthSignupClientAdapter(
                        client: AuthAPIClient(
                            apiClient: APIClient(environment: .live())
                        )
                    ),
                    onClose: {
                        authRoute = .login
                    },
                    onBackToLogin: { completion in
                        loginPrefillEmail = completion.email
                        session.logoutToLogin(message: completion.bannerMessage)
                        authRoute = .login
                    }
                )
            }
        }
    }
}

struct AppRoot_Previews: PreviewProvider {
    static var previews: some View {
        Group {
            AppRoot(
                session: PreviewFactory.makeSession(state: .unauthenticated),
                homeClient: PreviewHomeClient.loaded(),
                timetableClient: PreviewTimetableClient.loaded(),
                boardsClient: PreviewBoardsClient(scenario: .loaded),
                courseReviewsClient: PreviewCourseReviewsClient.loaded(),
                messagesClient: PreviewMessagesClient.loaded(),
                messagesRealtimeClient: PreviewMessagesRealtimeClient()
            )
            .previewDisplayName("AppRoot Login")

            AppRoot(
                session: PreviewFactory.makeSession(state: .authenticated),
                homeClient: PreviewHomeClient.loaded(),
                timetableClient: PreviewTimetableClient.loaded(),
                boardsClient: PreviewBoardsClient(scenario: .loaded),
                courseReviewsClient: PreviewCourseReviewsClient.loaded(),
                messagesClient: PreviewMessagesClient.loaded(),
                messagesRealtimeClient: PreviewMessagesRealtimeClient()
            )
            .previewDisplayName("AppRoot Home")
        }
    }
}
