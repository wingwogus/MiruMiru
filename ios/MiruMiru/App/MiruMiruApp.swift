import SwiftUI

@main
struct MiruMiruApp: App {
    @StateObject private var session: AppSession
    private let homeClient: HomeClientProtocol
    private let timetableClient: TimetableClientProtocol
    private let boardsClient: BoardsClientProtocol
    private let courseReviewsClient: CourseReviewsClientProtocol

    init() {
        let environment = AppEnvironment.live()
        let tokenStore = KeychainTokenStore()
        let apiClient = APIClient(environment: environment)
        let authorizedExecutor = AuthorizedRequestExecutor(
            apiClient: apiClient,
            tokenStore: tokenStore
        )
        let authClient = AuthAPIClient(apiClient: apiClient)
        homeClient = HomeAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore,
            authorizedExecutor: authorizedExecutor
        )
        timetableClient = TimetableAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore,
            authorizedExecutor: authorizedExecutor
        )
        boardsClient = BoardsAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore,
            authorizedExecutor: authorizedExecutor
        )
        courseReviewsClient = CourseReviewsAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore,
            authorizedExecutor: authorizedExecutor
        )
        _session = StateObject(
            wrappedValue: AppSession(
                authClient: authClient,
                tokenStore: tokenStore
            )
        )
    }

    var body: some Scene {
        WindowGroup {
            AppRoot(
                session: session,
                homeClient: homeClient,
                timetableClient: timetableClient,
                boardsClient: boardsClient,
                courseReviewsClient: courseReviewsClient
            )
                .task {
                    await session.bootstrap()
                }
        }
    }
}
