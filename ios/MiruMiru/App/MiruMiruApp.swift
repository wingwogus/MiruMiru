import SwiftUI

@main
struct MiruMiruApp: App {
    @StateObject private var session: AppSession
    private let homeClient: HomeClientProtocol
    private let timetableClient: TimetableClientProtocol
    private let boardsClient: BoardsClientProtocol

    init() {
        let environment = AppEnvironment.live()
        let tokenStore = KeychainTokenStore()
        let apiClient = APIClient(environment: environment)
        let authClient = AuthAPIClient(apiClient: apiClient)
        homeClient = HomeAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore
        )
        timetableClient = TimetableAPIClient(
            apiClient: apiClient,
            tokenStore: tokenStore
        )
        boardsClient = BoardsAPIClient(
            apiClient: apiClient,
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
                homeClient: homeClient,
                timetableClient: timetableClient,
                boardsClient: boardsClient
            )
                .task {
                    await session.bootstrap()
                }
        }
    }
}
