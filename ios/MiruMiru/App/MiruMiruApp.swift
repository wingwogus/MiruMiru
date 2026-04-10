import SwiftUI

@main
struct MiruMiruApp: App {
    @StateObject private var session: AppSession
    private let requestCacheStore: RequestCacheStore
    private let homeClient: HomeClientProtocol
    private let timetableClient: TimetableClientProtocol
    private let boardsClient: BoardsClientProtocol
    private let courseReviewsClient: CourseReviewsClientProtocol
    private let messagesClient: MessagesClientProtocol
    private let messagesRealtimeClient: MessagesRealtimeClientProtocol

    init() {
        let environment = AppEnvironment.live()
        let tokenStore = KeychainTokenStore()
        let apiClient = APIClient(environment: environment)
        requestCacheStore = RequestCacheStore()
        let authorizedExecutor = AuthorizedRequestExecutor(
            apiClient: apiClient,
            tokenStore: tokenStore,
            cacheStore: requestCacheStore
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
        messagesClient = MessagesAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore,
            authorizedExecutor: authorizedExecutor
        )
        messagesRealtimeClient = MessagesRealtimeClient(
            environment: environment,
            tokenStore: tokenStore
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
                requestCacheStore: requestCacheStore,
                homeClient: homeClient,
                timetableClient: timetableClient,
                boardsClient: boardsClient,
                courseReviewsClient: courseReviewsClient,
                messagesClient: messagesClient,
                messagesRealtimeClient: messagesRealtimeClient
            )
                .task {
                    await session.bootstrap()
                }
        }
    }
}
